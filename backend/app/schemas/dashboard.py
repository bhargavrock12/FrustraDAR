from pydantic import BaseModel
from typing import Optional, List
from datetime import datetime, date
from uuid import UUID


# ─────────────────────────────────────────────────────────────────────────
# SHARED BEHAVIORAL SCHEMAS
# ─────────────────────────────────────────────────────────────────────────

class BehavioralSummary(BaseModel):
    """
    Behavioral analytics fields present in daily and period summaries.
    These are descriptive — not a score and not a diagnosis.
    """
    total_play_time_min:      int
    total_sessions:           int
    avg_session_duration_min: int
    night_session_count:      int
    night_play_time_min:      int
    weekday_play_time_min:    int
    weekend_play_time_min:    int
    reopen_events:            int
    most_played_game:         Optional[str]
    avg_frustration_score:    Optional[float]
    max_frustration_score:    Optional[float]
    behavioral_indicators:    List[str]


class TrendChanges(BaseModel):
    """
    Period-over-period percentage changes.
    None means no previous data to compare against.
    """
    playtime_change_pct:               Optional[float]
    session_frequency_change_pct:      Optional[float]
    avg_session_duration_change_pct:   Optional[float]
    night_activity_change_pct:         Optional[float]


# ─────────────────────────────────────────────────────────────────────────
# DAILY SUMMARY RESPONSE
# ─────────────────────────────────────────────────────────────────────────

class DailySummaryResponse(BaseModel):
    date:                     date
    total_play_time_min:      int
    total_sessions:           int
    avg_session_duration_min: int
    night_session_count:      int
    night_play_time_min:      int
    weekday_play_time_min:    int
    weekend_play_time_min:    int
    reopen_events:            int
    consecutive_days:         int
    most_played_game:         Optional[str]
    avg_frustration_score:    Optional[float]
    max_frustration_score:    Optional[float]
    behavioral_indicators:    List[str]

    class Config:
        from_attributes = True


# ─────────────────────────────────────────────────────────────────────────
# DASHBOARD STATS
# ─────────────────────────────────────────────────────────────────────────

class CurrentSessionInfo(BaseModel):
    active:     bool
    game_name:  Optional[str]
    start_time: Optional[str]


class RecentScoreEntry(BaseModel):
    timestamp:    str
    fusion_score: float
    level:        str


class DashboardStats(BaseModel):
    """Top-level stats shown on the home dashboard."""
    today_play_time_min:      int
    today_sessions:           int
    avg_session_duration_min: int
    avg_frustration_today:    Optional[float]
    max_frustration_today:    Optional[float]
    night_session_count:      int
    consecutive_days_played:  int
    behavioral_indicators:    List[str]


# ─────────────────────────────────────────────────────────────────────────
# STUDENT DASHBOARD RESPONSE
# ─────────────────────────────────────────────────────────────────────────

class StudentDashboardResponse(BaseModel):
    user: dict
    today_stats:     DashboardStats
    current_session: CurrentSessionInfo
    recent_scores:   List[RecentScoreEntry]
    unread_alerts:   int


# ─────────────────────────────────────────────────────────────────────────
# PARENT DASHBOARD RESPONSE
# ─────────────────────────────────────────────────────────────────────────

class ChildSummary(BaseModel):
    id:                       str
    username:                 str
    is_gaming_now:            bool
    current_game:             Optional[str]
    avg_frustration:          Optional[float]
    max_frustration:          Optional[float]
    play_time_min:            int
    night_session_count:      int
    behavioral_indicators:    List[str]
    frustration_level:        str


class ParentDashboardResponse(BaseModel):
    parent:          dict
    children:        List[ChildSummary]
    total_children:  int
    unread_alerts:   int


# ─────────────────────────────────────────────────────────────────────────
# CHILD DETAIL RESPONSE (parent view)
# ─────────────────────────────────────────────────────────────────────────

class ChildDetailResponse(BaseModel):
    child:           dict
    trend_data:      List[dict]
    recent_sessions: List[dict]
    recent_alerts:   List[dict]
    today_summary:   Optional[DailySummaryResponse]


# ─────────────────────────────────────────────────────────────────────────
# REPORT RESPONSE SCHEMAS
# ─────────────────────────────────────────────────────────────────────────

class WeeklyReportResponse(BaseModel):
    period:           str
    week_start:       str
    week_end:         str
    current:          BehavioralSummary
    previous:         BehavioralSummary
    changes:          TrendChanges
    trend_indicators: List[str]
    daily_breakdown:  List[dict]


class MonthlyReportResponse(BaseModel):
    period:           str
    month_start:      str
    month_end:        str
    current:          BehavioralSummary
    previous:         BehavioralSummary
    changes:          TrendChanges
    trend_indicators: List[str]
    weekly_breakdown: List[dict]