import asyncio
import json
import logging
from typing import Optional

from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Query
from sqlalchemy.orm import Session

from app.db.database import SessionLocal
from app.core.security import decode_access_token
from app.models.user import User
from app.websocket.connection_manager import manager
from app.core.config import settings

logger = logging.getLogger(__name__)
router = APIRouter()


def _get_user_from_token(token: str, db: Session) -> Optional[User]:
    """Validate JWT token and return active User from DB."""
    payload = decode_access_token(token)
    if not payload:
        return None

    user_id = payload.get("sub")
    if not user_id:
        return None

    return db.query(User).filter(
        User.id == user_id,
        User.is_active == True
    ).first()


@router.websocket("/ws")
async def websocket_endpoint(
    websocket: WebSocket,
    token: str = Query(...)
):
    """
    Single WebSocket endpoint — all roles use this.
    Auth: JWT passed as query param → ?token=<jwt>
    Multi-connection: multiple devices per user supported.

    Connect: ws://host/ws?token=<jwt>
    """
    db = SessionLocal()
    user = None
    user_id = None

    try:
        # ---- AUTHENTICATE BEFORE ACCEPTING ----
        user = _get_user_from_token(token, db)

        if not user:
            # Reject — close before accept
            await websocket.close(code=4001, reason="Unauthorized")
            return

        user_id = str(user.id)
        role = user.role.value
        parent_id = str(user.parent_id) if user.parent_id else None

        # ---- REGISTER CONNECTION ----
        # manager.connect() calls websocket.accept() internally
        await manager.connect(
            user_id=user_id,
            role=role,
            websocket=websocket,
            parent_id=parent_id
        )

        # ---- SEND CONNECTED CONFIRMATION ----
        await websocket.send_text(json.dumps({
            "type": "connected",
            "data": {
                "user_id": user_id,
                "role": role,
                "message": "WebSocket connected successfully"
            }
        }))

        # ---- HEARTBEAT — per connection ----
        async def heartbeat_loop():
            """
            Sends ping to THIS specific connection every N seconds.
            Cancels cleanly when connection closes.
            """
            while True:
                await asyncio.sleep(settings.WS_HEARTBEAT_INTERVAL)
                alive = await manager.send_ping(user_id, websocket)
                if not alive:
                    logger.info(
                        f"Heartbeat failed — "
                        f"connection dead for user={user_id}"
                    )
                    break

        heartbeat_task = asyncio.create_task(heartbeat_loop())

        try:
            # ---- MESSAGE LOOP ----
            while True:
                raw = await websocket.receive_text()

                try:
                    msg = json.loads(raw)
                    msg_type = msg.get("type")

                    if msg_type == "pong":
                        # Heartbeat acknowledged — nothing to do
                        pass

                    elif msg_type == "ping":
                        # Client-initiated ping — respond with pong
                        await websocket.send_text(
                            json.dumps({"type": "pong"})
                        )

                    else:
                        logger.debug(
                            f"Unhandled WS message type={msg_type} "
                            f"from user={user_id}"
                        )

                except json.JSONDecodeError:
                    logger.warning(
                        f"Invalid JSON from user={user_id}: {raw[:100]}"
                    )

        except WebSocketDisconnect:
            logger.info(f"WS client disconnected: user={user_id}")

        finally:
            heartbeat_task.cancel()
            try:
                await heartbeat_task
            except asyncio.CancelledError:
                pass

    except Exception as e:
        logger.error(
            f"WS error for user={user_id or 'unknown'}: {e}",
            exc_info=True
        )

    finally:
        # Remove THIS specific connection (not all user connections)
        if user_id:
            manager.disconnect(user_id, websocket)
        db.close()