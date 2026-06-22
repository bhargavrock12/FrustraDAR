import logging
from datetime import datetime
from typing import Optional
from uuid import UUID

from sqlalchemy.orm import Session

from app.models.user import User
from app.models.session import GameSession
from app.models.score import FrustrationScore
from app.models.daily_summary import DailySummary
from app.models.alert import Alert
from app.services.score_service import ScoreService
from app.services.session_service import SessionService
from app.services.alert_service import AlertService
from app.utils.helpers import score_to_level

logger = logging.getLogger(__name__)


class DashboardService:
    def __init__(self, db: Session):
        self.db          = db
        self.score_svc   = ScoreService(db)
        self.session_svc = SessionService(db)
        self.alert_svc   = AlertService(db)

    # ─────────────────────────────────────────────────────────────────────
    # STUDENT DASHBOARD
    # ─────────────────────────────────────────────────────────────────────

    def get_student_dashboard(self, user: User) -> dict:
        """All data needed for the student home screen."""
        today          = datetime.utcnow().date()
        today_stats    = self.score_svc.get_today_stats(user.id)
        active_session = self.session_svc.get_active_session(user.id)
        recent_scores  = self.score_svc.get_latest_scores(user.id, limit=10)
        unread_alerts  = self.alert_svc.get_unread_count(user.id)
        daily_summary  = self._get_daily_summary(user.id, today)

        return {
            "user": {
                "id":       str(user.id),
                "username": user.username,
                "email":    user.email,
                "role":     user.role.value,
            },
            "today_stats": {
                "today_play_time_min":      (
                    daily_summary.total_play_time_min
                    if daily_summary else 0
                ),
                "today_sessions": (
                    daily_summary.total_sessions
                    if daily_summary else 0
                ),
                "avg_session_duration_min": (
                    daily_summary.avg_session_duration_min
                    if daily_summary else 0
                ),
                "avg_frustration_today":  today_stats.get("avg_score"),
                "max_frustration_today":  today_stats.get("max_score"),
                "night_session_count": (
                    daily_summary.night_session_count
                    if daily_summary else 0
                ),
                "consecutive_days_played": (
                    daily_summary.consecutive_days
                    if daily_summary else 0
                ),
                "behavioral_indicators": (
                    daily_summary.behavioral_indicators or []
                    if daily_summary else []
                ),
            },
            "current_session": {
                "active":     active_session is not None,
                "game_name":  active_session.game_name if active_session else None,
                "start_time": (
                    active_session.start_time.isoformat()
                    if active_session else None
                ),
            },
            "recent_scores": [
                {
                    "timestamp":    s.timestamp.isoformat(),
                    "fusion_score": s.fusion_score,
                    "level":        score_to_level(s.fusion_score),
                }
                for s in recent_scores
            ],
            "unread_alerts": unread_alerts,
        }

    # ─────────────────────────────────────────────────────────────────────
    # PARENT DASHBOARD
    # ─────────────────────────────────────────────────────────────────────

    def get_parent_dashboard(self, parent: User) -> dict:
        """All data needed for the parent home screen."""
        children = (
            self.db.query(User)
            .filter(User.parent_id == parent.id)
            .all()
        )

        today = datetime.utcnow().date()
        children_data = []

        for child in children:
            today_stats    = self.score_svc.get_today_stats(child.id)
            active_session = self.session_svc.get_active_session(child.id)
            daily_summary  = self._get_daily_summary(child.id, today)

            children_data.append({
                "id":               str(child.id),
                "username":         child.username,
                "is_gaming_now":    active_session is not None,
                "current_game":     (
                    active_session.game_name if active_session else None
                ),
                "avg_frustration":  today_stats.get("avg_score"),
                "max_frustration":  today_stats.get("max_score"),
                "play_time_min": (
                    daily_summary.total_play_time_min
                    if daily_summary else 0
                ),
                "night_session_count": (
                    daily_summary.night_session_count
                    if daily_summary else 0
                ),
                "behavioral_indicators": (
                    daily_summary.behavioral_indicators or []
                    if daily_summary else []
                ),
                "frustration_level": score_to_level(
                    today_stats.get("max_score") or 0.0
                ),
            })

        unread = (
            self.db.query(Alert)
            .join(User, Alert.user_id == User.id)
            .filter(
                User.parent_id     == parent.id,
                Alert.acknowledged == False  # noqa: E712
            )
            .count()
        )

        return {
            "parent": {
                "id":       str(parent.id),
                "username": parent.username,
            },
            "children":       children_data,
            "total_children": len(children),
            "unread_alerts":  unread,
        }

    # ─────────────────────────────────────────────────────────────────────
    # CHILD DETAIL  (parent view)
    # ─────────────────────────────────────────────────────────────────────

    def get_child_detail(
        self,
        parent:   User,
        child_id: UUID
    ) -> dict:
        """Detailed view of a single child for the parent."""
        from fastapi import HTTPException, status as http_status

        child = (
            self.db.query(User)
            .filter(
                User.id        == child_id,
                User.parent_id == parent.id
            )
            .first()
        )
        if not child:
            raise HTTPException(
                status_code=http_status.HTTP_404_NOT_FOUND,
                detail="Child not found"
            )

        today         = datetime.utcnow().date()
        trend_data    = self.score_svc.get_trend_data(child.id, days=7)
        sessions      = self.session_svc.get_session_history(child.id, limit=10)
        alerts        = self.alert_svc.get_user_alerts(child.id, limit=20)
        today_summary = self._get_daily_summary(child.id, today)

        return {
            "child": {
                "id":       str(child.id),
                "username": child.username,
                "email":    child.email,
            },
            "trend_data": trend_data,
            "recent_sessions": [
                {
                    "id":           str(s.id),
                    "game_name":    s.game_name,
                    "start_time":   s.start_time.isoformat(),
                    "end_time":     (
                        s.end_time.isoformat() if s.end_time else None
                    ),
                    "duration_min": (s.duration_sec or 0) // 60,
                    "is_night":     s.is_night,
                    "reopen_count": s.reopen_count,
                }
                for s in sessions
            ],
            "recent_alerts": [
                {
                    "id":           str(a.id),
                    "alert_type":   a.alert_type,
                    "severity":     a.severity,
                    "message":      a.message,
                    "sent_at":      a.sent_at.isoformat(),
                    "acknowledged": a.acknowledged,
                }
                for a in alerts
            ],
            "today_summary": (
                self._daily_summary_to_dict(today_summary)
                if today_summary else None
            ),
        }

    # ─────────────────────────────────────────────────────────────────────
    # PRIVATE HELPERS
    # ─────────────────────────────────────────────────────────────────────

    def _get_daily_summary(
        self,
        user_id:     UUID,
        target_date: object
    ) -> Optional[DailySummary]:
        return (
            self.db.query(DailySummary)
            .filter(
                DailySummary.user_id == user_id,
                DailySummary.date    == target_date
            )
            .first()
        )

    @staticmethod
    def _daily_summary_to_dict(s: DailySummary) -> dict:
        return {
            "date":                     s.date.isoformat(),
            "total_play_time_min":      s.total_play_time_min,
            "total_sessions":           s.total_sessions,
            "avg_session_duration_min": s.avg_session_duration_min,
            "night_session_count":      s.night_session_count,
            "night_play_time_min":      s.night_play_time_min,
            "weekday_play_time_min":    s.weekday_play_time_min,
            "weekend_play_time_min":    s.weekend_play_time_min,
            "reopen_events":            s.reopen_events,
            "consecutive_days":         s.consecutive_days,
            "most_played_game":         s.most_played_game,
            "avg_frustration_score":    s.avg_frustration_score,
            "max_frustration_score":    s.max_frustration_score,
            "behavioral_indicators":    s.behavioral_indicators or [],
        }