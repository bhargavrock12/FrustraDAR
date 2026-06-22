import logging
from typing import Optional
from sqlalchemy.orm import Session
from app.models.user import User
from app.models.session import GameSession
from app.models.score import FrustrationScore
from app.models.alert import Alert
from app.services.alert_service import AlertService
from app.services.notification_service import NotificationService
from app.websocket.connection_manager import manager
from app.websocket import events as evt
from app.utils.helpers import score_to_level   # ← uses config thresholds

logger = logging.getLogger(__name__)

notification_svc = NotificationService()


class EventPipeline:
    """
    Orchestrator only.
    Delegates to alert_service, notification_service, websocket manager.
    Contains NO business logic itself.
    """

    def __init__(self, db: Session):
        self.db        = db
        self.alert_svc = AlertService(db)

    # ============================================
    # SCORE PIPELINE
    # ============================================
    async def on_scores_uploaded(
        self,
        user:         User,
        session:      GameSession,
        max_score:    float,
        latest_score: FrustrationScore
    ):
        user_id   = str(user.id)
        session_id = str(session.id)
        parent_id = str(user.parent_id) if user.parent_id else None
        level     = score_to_level(latest_score.fusion_score)  # ← config-driven

        # ---- 1. WebSocket → student: score update ----
        score_event = evt.evt_frustration_score_updated(
            user_id=user_id,
            session_id=session_id,
            fusion_score=latest_score.fusion_score,
            level=level,
            facial_score=latest_score.facial_score,
            audio_score=latest_score.audio_score,
            motion_score=latest_score.motion_score,
            behavior_score=latest_score.behavior_score,
            signals_used=latest_score.signals_used or []
        )
        await manager.send_to_user(user_id, score_event)

        # ---- 2. WebSocket → parent: gaming status ----
        if parent_id:
            status_event = evt.evt_gaming_status_updated(
                user_id=user_id,
                username=user.username,
                is_gaming=True,
                game_name=session.game_name,
                current_score=latest_score.fusion_score
            )
            await manager.send_to_parent_of(
                user_id, status_event, parent_id=parent_id
            )

        # ---- 3. Alert pipeline (cooldown inside alert_service) ----
        alert = self.alert_svc.check_and_alert(
            user=user,
            fusion_score=max_score,
            game_name=session.game_name
        )

        if alert:
            await self._on_alert_created(alert, user, parent_id)

    # ============================================
    # SESSION PIPELINE
    # ============================================
    async def on_session_started(
        self,
        user:    User,
        session: GameSession
    ):
        user_id   = str(user.id)
        parent_id = str(user.parent_id) if user.parent_id else None

        event = evt.evt_session_started(
            session_id=str(session.id),
            user_id=user_id,
            username=user.username,
            game_name=session.game_name,
            game_package=session.game_package,
            start_time=session.start_time.isoformat(),
            is_night=session.is_night
        )

        await manager.send_to_user(user_id, event)

        if parent_id:
            await manager.send_to_parent_of(
                user_id, event, parent_id=parent_id
            )

        # Night alert + FCM (if night session and cooldown allows)
        if session.is_night:
            night_alert = self.alert_svc.create_night_alert(
                user, session.game_name
            )
            if night_alert and parent_id:
                parent = self._get_parent(user.parent_id)
                if parent and parent.fcm_token:
                    notification_svc.notify_night_gaming(
                        parent.fcm_token,
                        user.username,
                        session.game_name
                    )

    async def on_session_ended(
        self,
        user:    User,
        session: GameSession
    ):
        user_id   = str(user.id)
        parent_id = str(user.parent_id) if user.parent_id else None

        event = evt.evt_session_ended(
            session_id=str(session.id),
            user_id=user_id,
            username=user.username,
            game_name=session.game_name,
            duration_sec=session.duration_sec,
            end_time=session.end_time.isoformat() if session.end_time else ""
        )

        await manager.send_to_user(user_id, event)

        if parent_id:
            await manager.send_to_parent_of(
                user_id, event, parent_id=parent_id
            )

    # ============================================
    # ALERT → WebSocket + FCM
    # ============================================
    async def _on_alert_created(
        self,
        alert:     Alert,
        user:      User,
        parent_id: Optional[str]
    ):
        alert_event = evt.evt_frustration_alert(
            alert_id=str(alert.id),
            alert_type=alert.alert_type,
            severity=alert.severity,
            message=alert.message or "",
            triggered_score=alert.triggered_score,
            user_id=str(user.id),
            username=user.username
        )

        # WebSocket → parent (real-time if connected)
        if parent_id:
            await manager.send_to_parent_of(
                str(user.id), alert_event, parent_id=parent_id
            )

        # FCM → parent mobile (high/critical only)
        if alert.severity in ("high", "critical") and parent_id:
            parent = self._get_parent(user.parent_id)
            if parent and parent.fcm_token:
                notification_svc.notify_high_frustration(
                    parent_fcm_token=parent.fcm_token,
                    child_name=user.username,
                    score=alert.triggered_score or 0,
                    game_name=None
                )

    # ============================================
    # HELPER
    # ============================================
    def _get_parent(self, parent_id) -> Optional[User]:
        if not parent_id:
            return None
        return self.db.query(User).filter(User.id == parent_id).first()