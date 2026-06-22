import logging
from datetime import datetime, timedelta
from typing import List, Optional
from uuid import UUID

from sqlalchemy.orm import Session
from sqlalchemy import desc
from fastapi import HTTPException, status

from app.models.alert import Alert
from app.models.user import User
from app.core.config import settings

logger = logging.getLogger(__name__)


class AlertService:
    def __init__(self, db: Session):
        self.db = db

    # ─────────────────────────────────────────────────────────────────────
    # FRUSTRATION ALERTS
    # ─────────────────────────────────────────────────────────────────────

    def check_and_alert(
        self,
        user:       User,
        fusion_score: float,
        game_name:  Optional[str] = None
    ) -> Optional[Alert]:
        """
        Evaluate fusion score against configured thresholds.
        Apply cooldown before creating a new alert.
        Returns created Alert or None if threshold not crossed
        or cooldown is still active.
        """
        if fusion_score < settings.FRUSTRATION_HIGH_THRESHOLD:
            return None

        if fusion_score >= settings.FRUSTRATION_CRITICAL_THRESHOLD:
            alert_type = "critical_frustration"
            severity   = "critical"
            message    = (
                f"⚠️ CRITICAL: {user.username} is extremely frustrated "
                f"(score: {fusion_score:.1f})"
                + (f" while playing {game_name}" if game_name else "")
            )
        else:
            alert_type = "high_frustration"
            severity   = "high"
            message    = (
                f"{user.username} is showing HIGH frustration "
                f"(score: {fusion_score:.1f})"
                + (f" while playing {game_name}" if game_name else "")
            )

        if self._is_on_cooldown(user.id, alert_type):
            logger.debug(
                "Alert suppressed — cooldown active: "
                "user=%s type=%s", user.id, alert_type
            )
            return None

        return self._create_alert(
            user_id=user.id,
            alert_type=alert_type,
            severity=severity,
            message=message,
            triggered_score=fusion_score,
            sent_to=user.parent_email
        )

    # ─────────────────────────────────────────────────────────────────────
    # NIGHT SESSION ALERT
    # ─────────────────────────────────────────────────────────────────────

    def create_night_alert(
        self,
        user:      User,
        game_name: Optional[str] = None
    ) -> Optional[Alert]:
        """
        Create alert when a gaming session starts during night hours.
        Cooldown applied — one alert per cooldown window.
        """
        if self._is_on_cooldown(user.id, "night_session"):
            return None

        message = (
            f"🌙 {user.username} started gaming late at night "
            f"({datetime.utcnow().strftime('%I:%M %p')})"
            + (f" — playing {game_name}" if game_name else "")
        )

        return self._create_alert(
            user_id=user.id,
            alert_type="night_session",
            severity="medium",
            message=message,
            sent_to=user.parent_email
        )

    # ─────────────────────────────────────────────────────────────────────
    # RAPID REOPEN ALERT
    # Detected by Android behavioral layer.
    # Backend consumes and stores the alert.
    # ─────────────────────────────────────────────────────────────────────

    def create_rapid_reopen_alert(
        self,
        user:         User,
        reopen_count: int,
        game_name:    Optional[str] = None
    ) -> Optional[Alert]:
        """
        Store a rapid-reopen alert reported by the Android client.
        Cooldown applied to avoid duplicate storage.
        """
        if self._is_on_cooldown(user.id, "rapid_reopen"):
            return None

        message = (
            f"🔄 {user.username} reopened "
            f"{game_name or 'a game'} {reopen_count} times rapidly."
        )

        return self._create_alert(
            user_id=user.id,
            alert_type="rapid_reopen",
            severity="medium",
            message=message,
            sent_to=user.parent_email
        )

    # ─────────────────────────────────────────────────────────────────────
    # RETRIEVAL
    # ─────────────────────────────────────────────────────────────────────

    def get_user_alerts(
        self,
        user_id: UUID,
        limit:   int = 50,
        skip:    int = 0
    ) -> List[Alert]:
        return (
            self.db.query(Alert)
            .filter(Alert.user_id == user_id)
            .order_by(desc(Alert.sent_at))
            .offset(skip)
            .limit(limit)
            .all()
        )

    def get_unread_count(self, user_id: UUID) -> int:
        return (
            self.db.query(Alert)
            .filter(
                Alert.user_id    == user_id,
                Alert.acknowledged == False   # noqa: E712
            )
            .count()
        )

    def acknowledge_alert(
        self,
        alert_id: UUID,
        user_id:  UUID
    ) -> Optional[Alert]:
        alert = (
            self.db.query(Alert)
            .filter(
                Alert.id      == alert_id,
                Alert.user_id == user_id
            )
            .first()
        )
        if alert:
            alert.acknowledged = True
            self.db.commit()
            self.db.refresh(alert)
        return alert

    # ─────────────────────────────────────────────────────────────────────
    # COOLDOWN — reuses existing Alert table (Option A, confirmed)
    # ─────────────────────────────────────────────────────────────────────

    def _is_on_cooldown(
        self,
        user_id:    UUID,
        alert_type: str
    ) -> bool:
        """
        Returns True if a recent alert of the same type exists
        within the configured cooldown window.
        No separate cooldown table — queries existing alerts.
        """
        cutoff = datetime.utcnow() - timedelta(
            minutes=settings.ALERT_COOLDOWN_MINUTES
        )
        recent = (
            self.db.query(Alert)
            .filter(
                Alert.user_id    == user_id,
                Alert.alert_type == alert_type,
                Alert.sent_at    >= cutoff
            )
            .first()
        )
        return recent is not None

    # ─────────────────────────────────────────────────────────────────────
    # INTERNAL
    # ─────────────────────────────────────────────────────────────────────

    def _create_alert(
        self,
        user_id:         UUID,
        alert_type:      str,
        severity:        str,
        message:         str,
        triggered_score: Optional[float] = None,
        sent_to:         Optional[str]   = None
    ) -> Alert:
        alert = Alert(
            user_id=user_id,
            alert_type=alert_type,
            severity=severity,
            message=message,
            triggered_score=triggered_score,
            sent_to=sent_to,
            sent_at=datetime.utcnow()
        )
        self.db.add(alert)
        self.db.commit()
        self.db.refresh(alert)
        logger.info(
            "Alert created: type=%s severity=%s user=%s score=%s",
            alert_type, severity, user_id, triggered_score
        )
        return alert