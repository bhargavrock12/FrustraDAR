from sqlalchemy.orm import Session
from fastapi import HTTPException, status
from datetime import datetime
from uuid import UUID
from app.models.session import GameSession
from app.models.user import User
from app.schemas.session import SessionStart, SessionEnd
from app.core.config import settings


class SessionService:
    def __init__(self, db: Session):
        self.db = db

    # ============================================
    # START SESSION
    # Night check uses config values (not hardcoded)
    # Pipeline triggered by route after this returns
    # ============================================
    def start_session(
        self,
        user: User,
        data: SessionStart
    ) -> GameSession:
        # Close any lingering active sessions
        self._close_active_sessions(user.id)

        # Night check from config
        hour     = data.start_time.hour
        is_night = (
            hour >= settings.NIGHT_START_HOUR or
            hour <  settings.NIGHT_END_HOUR
        )

        session = GameSession(
            user_id=user.id,
            game_package=data.game_package,
            game_name=data.game_name,
            start_time=data.start_time,
            is_night=is_night,
            is_active=True
        )

        self.db.add(session)
        self.db.commit()
        self.db.refresh(session)

        return session

    # ============================================
    # END SESSION
    # ============================================
    def end_session(
        self,
        session_id: UUID,
        user:       User,
        data:       SessionEnd
    ) -> GameSession:
        session = self._get_session(session_id, user.id)

        session.end_time     = data.end_time
        session.is_active    = False
        session.reopen_count = data.reopen_count or 0

        if session.start_time:
            delta = data.end_time - session.start_time
            session.duration_sec = int(delta.total_seconds())

        self.db.commit()
        self.db.refresh(session)

        return session

    # ============================================
    # QUERIES
    # ============================================
    def get_active_session(self, user_id: UUID) -> GameSession:
        return self.db.query(GameSession).filter(
            GameSession.user_id  == user_id,
            GameSession.is_active == True
        ).first()

    def get_session_history(
        self,
        user_id: UUID,
        limit:   int = 20,
        skip:    int = 0
    ) -> list:
        return self.db.query(GameSession).filter(
            GameSession.user_id == user_id
        ).order_by(
            GameSession.start_time.desc()
        ).offset(skip).limit(limit).all()

    # ============================================
    # PRIVATE
    # ============================================
    def _get_session(
        self,
        session_id: UUID,
        user_id:    UUID
    ) -> GameSession:
        session = self.db.query(GameSession).filter(
            GameSession.id      == session_id,
            GameSession.user_id == user_id
        ).first()

        if not session:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Session not found"
            )
        return session

    def _close_active_sessions(self, user_id: UUID):
        active = self.db.query(GameSession).filter(
            GameSession.user_id   == user_id,
            GameSession.is_active == True
        ).all()

        for s in active:
            s.is_active = False
            s.end_time  = datetime.utcnow()

        if active:
            self.db.commit()