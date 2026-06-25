import asyncio
import json
import logging
from typing import Dict, List, Optional, Set
from fastapi import WebSocket

logger = logging.getLogger(__name__)


class ConnectionManager:
    """
    Manages active WebSocket connections in memory.
    Supports MULTIPLE connections per user (phone + tablet + web).
    NOT persisted to database — transient runtime state only.

    Structure:
        _connections: { user_id_str -> List[WebSocket] }
        _user_roles:  { user_id_str -> role_str }
        _parent_map:  { student_id_str -> parent_id_str }
    """

    def __init__(self):
        # Multiple connections per user supported
        self._connections: Dict[str, List[WebSocket]] = {}
        self._user_roles:  Dict[str, str]             = {}
        self._parent_map:  Dict[str, str]             = {}

    # ============================================
    # CONNECT
    # ============================================
    async def connect(
        self,
        user_id:   str,
        role:      str,
        websocket: WebSocket,
        parent_id: Optional[str] = None
    ):
        """
        Register a new WebSocket connection for a user.
        Multiple connections per user are supported.
        """
        await websocket.accept()

        if user_id not in self._connections:
            self._connections[user_id] = []

        self._connections[user_id].append(websocket)
        self._user_roles[user_id] = role

        if role == "student" and parent_id:
            self._parent_map[user_id] = parent_id

        count = len(self._connections[user_id])
        logger.info(
            f"WS connected: user={user_id} role={role} "
            f"total_connections={count}"
        )

    # ============================================
    # DISCONNECT
    # ============================================
    def disconnect(self, user_id: str, websocket: WebSocket):
        """
        Remove a specific WebSocket connection for a user.
        Other connections for the same user remain active.
        """
        if user_id not in self._connections:
            return

        try:
            self._connections[user_id].remove(websocket)
        except ValueError:
            pass  # already removed

        # Clean up if no connections remain
        if not self._connections[user_id]:
            del self._connections[user_id]
            self._user_roles.pop(user_id, None)
            self._parent_map.pop(user_id, None)
            logger.info(f"WS fully disconnected: user={user_id}")
        else:
            remaining = len(self._connections[user_id])
            logger.info(
                f"WS connection removed: user={user_id} "
                f"remaining={remaining}"
            )

    # ============================================
    # SEND TO USER
    # Sends to ALL active connections for a user
    # ============================================
    async def send_to_user(self, user_id: str, event: dict) -> bool:
        """
        Send event to ALL active connections of a user.
        Removes stale connections on send failure.
        Returns True if sent to at least one connection.
        """
        connections = self._connections.get(user_id)
        if not connections:
            return False

        payload      = json.dumps(event)
        sent_count   = 0
        stale        = []

        for ws in list(connections):
            try:
                await ws.send_text(payload)
                sent_count += 1
            except Exception as e:
                logger.warning(
                    f"WS send failed for user={user_id}: {e} "
                    f"— marking connection as stale"
                )
                stale.append(ws)

        # Clean up stale connections
        for ws in stale:
            self.disconnect(user_id, ws)

        return sent_count > 0

    async def send_to_parent_of(
        self,
        student_id: str,
        event:      dict,
        parent_id:  Optional[str] = None
    ) -> bool:
        """
        Send event to all connections of the parent of a student.
        parent_id can be passed directly (preferred — from DB lookup).
        Falls back to _parent_map.
        """
        target = parent_id or self._parent_map.get(student_id)
        if not target:
            return False
        return await self.send_to_user(target, event)

    async def send_to_student(self, student_id: str, event: dict) -> bool:
        return await self.send_to_user(student_id, event)

    # ============================================
    # HEARTBEAT — per connection, not per user
    # ============================================
    async def send_ping(
        self,
        user_id:   str,
        websocket: WebSocket
    ) -> bool:
        """
        Send ping to a SPECIFIC connection.
        Called per-connection in the heartbeat loop.
        Returns False if connection is dead.
        """
        try:
            await websocket.send_text(json.dumps({"type": "ping"}))
            return True
        except Exception:
            self.disconnect(user_id, websocket)
            return False

    # ============================================
    # STATUS
    # ============================================
    def is_connected(self, user_id: str) -> bool:
        return bool(self._connections.get(user_id))

    def connection_count(self, user_id: str) -> int:
        return len(self._connections.get(user_id, []))

    def total_connected_users(self) -> int:
        return len(self._connections)

    def total_connections(self) -> int:
        return sum(len(v) for v in self._connections.values())


# Singleton — shared across the entire app
manager = ConnectionManager()