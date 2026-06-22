import logging
from datetime import datetime, timedelta, date
from typing import List, Optional
from uuid import UUID

from sqlalchemy.orm import Session
from sqlalchemy import func

from app.models.score import FrustrationScore
from app.models.session import GameSession
from app.models.daily_summary import DailySummary
from app.core.config import settings

logger = logging.getLogger(__name__)

# ─────────────────────────────────────────────────────────────────────────
# BEHAVIORAL INDICATOR CONSTANTS
# ─────────────────────────────────────────────────────────────────────────
INDICATOR_LONG_SESSION        = "LONG_SESSION"
INDICATOR_HIGH_DAILY_PLAYTIME = "HIGH_DAILY_PLAYTIME"
INDICATOR_FREQUENT_SESSIONS   = "FREQUENT_SESSIONS"
INDICATOR_LATE_NIGHT_GAMING   = "LATE_NIGHT_GAMING"
INDICATOR_RAPID_REOPEN        = "RAPID_REOPEN"

# ─────────────────────────────────────────────────────────────────────────
# TREND INDICATOR CONSTANTS
# ─────────────────────────────────────────────────────────────────────────
TREND_INCREASING_PLAYTIME   = "INCREASING_PLAYTIME"
TREND_INCREASING_FREQUENCY  = "INCREASING_FREQUENCY"
TREND_INCREASING_SESSION_LEN = "INCREASING_SESSION_DURATION"
TREND_INCREASING_NIGHT      = "INCREASING_NIGHT_ACTIVITY"


class TrendService:
    def __init__(self, db: Session):
        self.db = db

    # ─────────────────────────────────────────────────────────────────────
    # DAILY SUMMARY COMPUTATION
    # ─────────────────────────────────────────────────────────────────────

    def compute_daily_summary(
        self,
        user_id:     UUID,
        target_date: Optional[date] = None
    ) -> DailySummary:
        """
        Compute and persist the daily behavioral analytics summary
        for a user on the given date.

        Called at end of day or triggered manually.
        Uses session and score data already stored in the database.
        """
        if target_date is None:
            target_date = datetime.utcnow().date()

        day_start = datetime.combine(target_date, datetime.min.time())
        day_end   = datetime.combine(target_date, datetime.max.time())

        sessions = self._get_sessions(user_id, day_start, day_end)
        scores   = self._get_scores(user_id, day_start, day_end)

        # ── Playtime ──────────────────────────────────────────────────────
        total_play_time_min = sum(
            (s.duration_sec or 0) for s in sessions
        ) // 60

        total_sessions = len(sessions)

        avg_session_duration_min = (
            total_play_time_min // total_sessions
            if total_sessions > 0
            else 0
        )

        # ── Night Activity ─────────────────────────────────────────────────
        night_sessions = [s for s in sessions if s.is_night]
        night_session_count = len(night_sessions)
        night_play_time_min = sum(
            (s.duration_sec or 0) for s in night_sessions
        ) // 60

        # ── Weekday / Weekend Split ────────────────────────────────────────
        # target_date.weekday(): Mon=0 … Fri=4, Sat=5, Sun=6
        is_weekend = target_date.weekday() >= 5
        weekday_play_time_min = (
            0 if is_weekend else total_play_time_min
        )
        weekend_play_time_min = (
            total_play_time_min if is_weekend else 0
        )

        # ── Frustration Stats ──────────────────────────────────────────────
        fusion_scores = [s.fusion_score for s in scores]
        avg_frustration = (
            round(sum(fusion_scores) / len(fusion_scores), 2)
            if fusion_scores else None
        )
        max_frustration = max(fusion_scores) if fusion_scores else None
        peak_score_obj  = (
            max(scores, key=lambda s: s.fusion_score)
            if scores else None
        )

        # ── Most Played Game ───────────────────────────────────────────────
        game_times: dict = {}
        for s in sessions:
            if s.game_name:
                game_times[s.game_name] = (
                    game_times.get(s.game_name, 0) + (s.duration_sec or 0)
                )
        most_played = (
            max(game_times, key=lambda g: game_times[g])
            if game_times else None
        )

        # ── Reopen Events ─────────────────────────────────────────────────
        total_reopens = sum(s.reopen_count for s in sessions)

        # ── Consecutive Days Streak ───────────────────────────────────────
        streak = self._compute_streak(user_id, target_date)

        # ── Behavioral Indicators ─────────────────────────────────────────
        indicators = self._generate_indicators(
            total_play_time_min=total_play_time_min,
            avg_session_duration_min=avg_session_duration_min,
            total_sessions=total_sessions,
            night_session_count=night_session_count,
            total_reopens=total_reopens
        )

        # ── Upsert Daily Summary ──────────────────────────────────────────
        summary = (
            self.db.query(DailySummary)
            .filter(
                DailySummary.user_id == user_id,
                DailySummary.date    == target_date
            )
            .first()
        )
        if summary is None:
            summary = DailySummary(user_id=user_id, date=target_date)
            self.db.add(summary)

        summary.total_play_time_min      = total_play_time_min
        summary.total_sessions           = total_sessions
        summary.avg_session_duration_min = avg_session_duration_min
        summary.night_play_time_min      = night_play_time_min
        summary.night_session_count      = night_session_count
        summary.weekday_play_time_min    = weekday_play_time_min
        summary.weekend_play_time_min    = weekend_play_time_min
        summary.avg_frustration_score    = avg_frustration
        summary.max_frustration_score    = max_frustration
        summary.peak_frustration_time    = (
            peak_score_obj.timestamp if peak_score_obj else None
        )
        summary.most_played_game         = most_played
        summary.reopen_events            = total_reopens
        summary.consecutive_days         = streak
        summary.behavioral_indicators    = indicators

        self.db.commit()
        self.db.refresh(summary)

        logger.info(
            "Daily summary computed: user=%s date=%s indicators=%s",
            user_id, target_date, indicators
        )
        return summary

    # ─────────────────────────────────────────────────────────────────────
    # WEEKLY REPORT
    # ─────────────────────────────────────────────────────────────────────

    def get_weekly_report(
        self,
        user_id:   UUID,
        weeks_ago: int = 0
    ) -> dict:
        """
        Weekly analytics report.
        Includes current-week stats and comparison against previous week.
        weeks_ago=0 → current week, weeks_ago=1 → last week, etc.
        """
        current_start, current_end = self._week_bounds(weeks_ago)
        prev_start,    prev_end    = self._week_bounds(weeks_ago + 1)

        current_summaries = self._get_summaries(
            user_id, current_start, current_end
        )
        prev_summaries = self._get_summaries(
            user_id, prev_start, prev_end
        )

        current_agg = self._aggregate_summaries(current_summaries)
        prev_agg    = self._aggregate_summaries(prev_summaries)

        changes       = self._compute_changes(current_agg, prev_agg)
        trend_indicators = self._generate_trend_indicators(changes)

        return {
            "period":           "weekly",
            "week_start":       current_start.isoformat(),
            "week_end":         current_end.isoformat(),
            "current":          current_agg,
            "previous":         prev_agg,
            "changes":          changes,
            "trend_indicators": trend_indicators,
            "daily_breakdown":  [
                self._summary_to_dict(s) for s in current_summaries
            ],
        }

    # ─────────────────────────────────────────────────────────────────────
    # MONTHLY REPORT
    # ─────────────────────────────────────────────────────────────────────

    def get_monthly_report(
        self,
        user_id:    UUID,
        months_ago: int = 0
    ) -> dict:
        """
        Monthly analytics report.
        Includes current-month stats and comparison against previous month.
        months_ago=0 → current month, months_ago=1 → last month, etc.
        """
        current_start, current_end = self._month_bounds(months_ago)
        prev_start,    prev_end    = self._month_bounds(months_ago + 1)

        current_summaries = self._get_summaries(
            user_id, current_start, current_end
        )
        prev_summaries = self._get_summaries(
            user_id, prev_start, prev_end
        )

        current_agg = self._aggregate_summaries(current_summaries)
        prev_agg    = self._aggregate_summaries(prev_summaries)

        changes          = self._compute_changes(current_agg, prev_agg)
        trend_indicators = self._generate_trend_indicators(changes)

        return {
            "period":           "monthly",
            "month_start":      current_start.isoformat(),
            "month_end":        current_end.isoformat(),
            "current":          current_agg,
            "previous":         prev_agg,
            "changes":          changes,
            "trend_indicators": trend_indicators,
            "weekly_breakdown": self._build_weekly_breakdown(
                current_summaries, current_start, current_end
            ),
        }

    # ─────────────────────────────────────────────────────────────────────
    # BEHAVIORAL INDICATOR GENERATION
    # ─────────────────────────────────────────────────────────────────────

    def _generate_indicators(
        self,
        total_play_time_min:   int,
        avg_session_duration_min: int,
        total_sessions:        int,
        night_session_count:   int,
        total_reopens:         int
    ) -> List[str]:
        """
        Generate behavioral indicator strings from daily usage data.
        These are descriptive analytics — not a score or diagnosis.
        All thresholds come from config.
        """
        indicators: List[str] = []

        threshold_min = int(
            settings.DAILY_PLAYTIME_THRESHOLD_HOURS * 60
        )

        if total_play_time_min >= threshold_min:
            indicators.append(INDICATOR_HIGH_DAILY_PLAYTIME)

        if avg_session_duration_min >= settings.LONG_SESSION_THRESHOLD_MIN:
            indicators.append(INDICATOR_LONG_SESSION)

        if total_sessions >= settings.FREQUENT_SESSIONS_THRESHOLD:
            indicators.append(INDICATOR_FREQUENT_SESSIONS)

        if night_session_count > 0:
            indicators.append(INDICATOR_LATE_NIGHT_GAMING)

        if total_reopens >= settings.RAPID_REOPEN_COUNT:
            indicators.append(INDICATOR_RAPID_REOPEN)

        return indicators

    # ─────────────────────────────────────────────────────────────────────
    # AGGREGATION HELPERS
    # ─────────────────────────────────────────────────────────────────────

    def _aggregate_summaries(
        self,
        summaries: List[DailySummary]
    ) -> dict:
        """
        Aggregate a list of DailySummary records into a period summary.
        """
        if not summaries:
            return {
                "total_play_time_min":      0,
                "total_sessions":           0,
                "avg_session_duration_min": 0,
                "night_session_count":      0,
                "night_play_time_min":      0,
                "weekday_play_time_min":    0,
                "weekend_play_time_min":    0,
                "reopen_events":            0,
                "avg_frustration_score":    None,
                "max_frustration_score":    None,
                "most_played_game":         None,
                "behavioral_indicators":    [],
            }

        total_play      = sum(s.total_play_time_min for s in summaries)
        total_sessions  = sum(s.total_sessions for s in summaries)
        night_sessions  = sum(s.night_session_count for s in summaries)
        night_play      = sum(s.night_play_time_min for s in summaries)
        weekday_play    = sum(s.weekday_play_time_min for s in summaries)
        weekend_play    = sum(s.weekend_play_time_min for s in summaries)
        reopen_events   = sum(s.reopen_events for s in summaries)

        avg_session_dur = (
            total_play // total_sessions if total_sessions > 0 else 0
        )

        frust_scores = [
            s.avg_frustration_score
            for s in summaries
            if s.avg_frustration_score is not None
        ]
        avg_frustration = (
            round(sum(frust_scores) / len(frust_scores), 2)
            if frust_scores else None
        )

        max_scores = [
            s.max_frustration_score
            for s in summaries
            if s.max_frustration_score is not None
        ]
        max_frustration = max(max_scores) if max_scores else None

        # Most played game across the period
        game_totals: dict = {}
        for s in summaries:
            if s.most_played_game:
                game_totals[s.most_played_game] = (
                    game_totals.get(s.most_played_game, 0)
                    + s.total_play_time_min
                )
        most_played = (
            max(game_totals, key=lambda g: game_totals[g])
            if game_totals else None
        )

        # Collect all unique indicators across the period
        all_indicators: List[str] = []
        for s in summaries:
            for ind in (s.behavioral_indicators or []):
                if ind not in all_indicators:
                    all_indicators.append(ind)

        return {
            "total_play_time_min":      total_play,
            "total_sessions":           total_sessions,
            "avg_session_duration_min": avg_session_dur,
            "night_session_count":      night_sessions,
            "night_play_time_min":      night_play,
            "weekday_play_time_min":    weekday_play,
            "weekend_play_time_min":    weekend_play,
            "reopen_events":            reopen_events,
            "avg_frustration_score":    avg_frustration,
            "max_frustration_score":    max_frustration,
            "most_played_game":         most_played,
            "behavioral_indicators":    all_indicators,
        }

    def _compute_changes(
        self,
        current: dict,
        previous: dict
    ) -> dict:
        """
        Calculate percentage changes between two period aggregates.
        Returns None for a metric when previous value is zero
        (no meaningful percentage).
        """
        def pct_change(curr: float, prev: float) -> Optional[float]:
            if prev == 0:
                return None
            return round(((curr - prev) / prev) * 100, 1)

        return {
            "playtime_change_pct": pct_change(
                current["total_play_time_min"],
                previous["total_play_time_min"]
            ),
            "session_frequency_change_pct": pct_change(
                current["total_sessions"],
                previous["total_sessions"]
            ),
            "avg_session_duration_change_pct": pct_change(
                current["avg_session_duration_min"],
                previous["avg_session_duration_min"]
            ),
            "night_activity_change_pct": pct_change(
                current["night_session_count"],
                previous["night_session_count"]
            ),
        }

    def _generate_trend_indicators(self, changes: dict) -> List[str]:
        """
        Generate trend indicator strings from computed percentage changes.
        Positive change above a minimal threshold triggers an indicator.
        """
        indicators: List[str] = []
        threshold = 10.0  # % change considered meaningful

        playtime_chg = changes.get("playtime_change_pct")
        if playtime_chg is not None and playtime_chg > threshold:
            indicators.append(TREND_INCREASING_PLAYTIME)

        freq_chg = changes.get("session_frequency_change_pct")
        if freq_chg is not None and freq_chg > threshold:
            indicators.append(TREND_INCREASING_FREQUENCY)

        dur_chg = changes.get("avg_session_duration_change_pct")
        if dur_chg is not None and dur_chg > threshold:
            indicators.append(TREND_INCREASING_SESSION_LEN)

        night_chg = changes.get("night_activity_change_pct")
        if night_chg is not None and night_chg > threshold:
            indicators.append(TREND_INCREASING_NIGHT)

        return indicators

    # ─────────────────────────────────────────────────────────────────────
    # DATE BOUND HELPERS
    # ─────────────────────────────────────────────────────────────────────

    def _week_bounds(self, weeks_ago: int) -> tuple[date, date]:
        today      = datetime.utcnow().date()
        week_end   = today - timedelta(
            days=today.weekday() + 7 * weeks_ago
        )
        week_start = week_end - timedelta(days=6)
        return week_start, week_end

    def _month_bounds(self, months_ago: int) -> tuple[date, date]:
        today = datetime.utcnow().date()
        # First day of target month
        month = today.month - months_ago
        year  = today.year
        while month <= 0:
            month += 12
            year  -= 1
        month_start = date(year, month, 1)
        # Last day of target month
        if month == 12:
            month_end = date(year + 1, 1, 1) - timedelta(days=1)
        else:
            month_end = date(year, month + 1, 1) - timedelta(days=1)
        return month_start, month_end

    def _build_weekly_breakdown(
        self,
        summaries:     List[DailySummary],
        period_start:  date,
        period_end:    date
    ) -> List[dict]:
        """
        Group daily summaries into weekly buckets for monthly report.
        """
        weeks: List[dict] = []
        cursor = period_start
        while cursor <= period_end:
            week_end   = min(cursor + timedelta(days=6), period_end)
            week_sums  = [
                s for s in summaries
                if cursor <= s.date <= week_end
            ]
            weeks.append({
                "week_start": cursor.isoformat(),
                "week_end":   week_end.isoformat(),
                **self._aggregate_summaries(week_sums)
            })
            cursor = week_end + timedelta(days=1)
        return weeks

    # ─────────────────────────────────────────────────────────────────────
    # DB QUERY HELPERS
    # ─────────────────────────────────────────────────────────────────────

    def _get_sessions(
        self,
        user_id:   UUID,
        day_start: datetime,
        day_end:   datetime
    ) -> List[GameSession]:
        return (
            self.db.query(GameSession)
            .filter(
                GameSession.user_id    == user_id,
                GameSession.start_time >= day_start,
                GameSession.start_time <= day_end
            )
            .all()
        )

    def _get_scores(
        self,
        user_id:   UUID,
        day_start: datetime,
        day_end:   datetime
    ) -> List[FrustrationScore]:
        return (
            self.db.query(FrustrationScore)
            .filter(
                FrustrationScore.user_id   == user_id,
                FrustrationScore.timestamp >= day_start,
                FrustrationScore.timestamp <= day_end
            )
            .all()
        )

    def _get_summaries(
        self,
        user_id:    UUID,
        start_date: date,
        end_date:   date
    ) -> List[DailySummary]:
        return (
            self.db.query(DailySummary)
            .filter(
                DailySummary.user_id == user_id,
                DailySummary.date    >= start_date,
                DailySummary.date    <= end_date
            )
            .order_by(DailySummary.date)
            .all()
        )

    def _compute_streak(
        self,
        user_id:      UUID,
        current_date: date
    ) -> int:
        """Count consecutive days with at least one gaming session."""
        streak     = 0
        check_date = current_date

        while True:
            has_session = (
                self.db.query(GameSession)
                .filter(
                    GameSession.user_id == user_id,
                    func.date(GameSession.start_time) == check_date
                )
                .first()
            )
            if has_session:
                streak    += 1
                check_date = check_date - timedelta(days=1)
            else:
                break

            if streak > 365:  # safety cap
                break

        return streak

    @staticmethod
    def _summary_to_dict(s: DailySummary) -> dict:
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