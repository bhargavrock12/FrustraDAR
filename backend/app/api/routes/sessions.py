import logging
from fastapi import APIRouter, Depends, status, HTTPException
from sqlalchemy.orm import Session
from typing import List
from uuid import UUID
from app.db.database import get_db
from app.schemas.session import SessionStart, SessionEnd, SessionResponse
from app.services.session_service import SessionService
from app.services.event_pipeline import EventPipeline
from app.core.dependencies import get_current_student
from app.models.user import User

logger = logging.getLogger(__name__)
router = APIRouter()


@router.post(
    "/start",
    response_model=SessionResponse,
    status_code=status.HTTP_201_CREATED
)
async def start_session(
    data:         SessionStart,
    current_user: User    = Depends(get_current_student),
    db:           Session = Depends(get_db)
):
    """
    Called when student opens a game.
    Session persisted then pipeline fires WS + night alert if needed.
    """
    session = SessionService(db).start_session(current_user, data)

    pipeline = EventPipeline(db)
    await pipeline.on_session_started(current_user, session)

    return session


@router.put("/{session_id}/end", response_model=SessionResponse)
async def end_session(
    session_id:   UUID,
    data:         SessionEnd,
    current_user: User    = Depends(get_current_student),
    db:           Session = Depends(get_db)
):
    """
    Called when student closes a game.
    Session updated then pipeline fires WS events.
    """
    session = SessionService(db).end_session(session_id, current_user, data)

    pipeline = EventPipeline(db)
    await pipeline.on_session_ended(current_user, session)

    return session


@router.get("/active", response_model=SessionResponse)
def get_active_session(
    current_user: User    = Depends(get_current_student),
    db:           Session = Depends(get_db)
):
    session = SessionService(db).get_active_session(current_user.id)
    if not session:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No active session"
        )
    return session


@router.get("/history", response_model=List[SessionResponse])
def get_session_history(
    limit:        int     = 20,
    skip:         int     = 0,
    current_user: User    = Depends(get_current_student),
    db:           Session = Depends(get_db)
):
    return SessionService(db).get_session_history(
        current_user.id, limit, skip
    )