from datetime import datetime
from typing import Any, Optional, List


class EventType:
    FRUSTRATION_SCORE_UPDATED = "frustration_score_updated"
    FRUSTRATION_ALERT         = "frustration_alert"
    SESSION_STARTED           = "session_started"
    SESSION_ENDED             = "session_ended"
    GAMING_STATUS_UPDATED     = "gaming_status_updated"


def build_event(event_type: str, data: dict) -> dict:
    return {
        "type":      event_type,
        "timestamp": datetime.utcnow().isoformat(),
        "data":      data
    }


def evt_frustration_score_updated(
    user_id:        str,
    session_id:     Optional[str],
    fusion_score:   float,
    level:          str,
    facial_score:   Optional[float] = None,
    audio_score:    Optional[float] = None,
    motion_score:   Optional[float] = None,
    behavior_score: Optional[float] = None,
    signals_used:   Optional[List[str]] = None
) -> dict:
    return build_event(EventType.FRUSTRATION_SCORE_UPDATED, {
        "user_id":        user_id,
        "session_id":     session_id,
        "fusion_score":   fusion_score,
        "level":          level,
        "facial_score":   facial_score,
        "audio_score":    audio_score,
        "motion_score":   motion_score,
        "behavior_score": behavior_score,
        "signals_used":   signals_used or [],
    })


def evt_frustration_alert(
    alert_id:        str,
    alert_type:      str,
    severity:        str,
    message:         str,
    triggered_score: Optional[float],
    user_id:         str,
    username:        str
) -> dict:
    return build_event(EventType.FRUSTRATION_ALERT, {
        "alert_id":        alert_id,
        "alert_type":      alert_type,
        "severity":        severity,
        "message":         message,
        "triggered_score": triggered_score,
        "user_id":         user_id,
        "username":        username,
    })


def evt_session_started(
    session_id:   str,
    user_id:      str,
    username:     str,
    game_name:    Optional[str],
    game_package: Optional[str],
    start_time:   str,
    is_night:     bool
) -> dict:
    return build_event(EventType.SESSION_STARTED, {
        "session_id":   session_id,
        "user_id":      user_id,
        "username":     username,
        "game_name":    game_name,
        "game_package": game_package,
        "start_time":   start_time,
        "is_night":     is_night,
    })


def evt_session_ended(
    session_id:   str,
    user_id:      str,
    username:     str,
    game_name:    Optional[str],
    duration_sec: Optional[int],
    end_time:     str
) -> dict:
    return build_event(EventType.SESSION_ENDED, {
        "session_id":   session_id,
        "user_id":      user_id,
        "username":     username,
        "game_name":    game_name,
        "duration_sec": duration_sec,
        "end_time":     end_time,
    })


def evt_gaming_status_updated(
    user_id:       str,
    username:      str,
    is_gaming:     bool,
    game_name:     Optional[str],
    current_score: Optional[float]
) -> dict:
    return build_event(EventType.GAMING_STATUS_UPDATED, {
        "user_id":       user_id,
        "username":      username,
        "is_gaming":     is_gaming,
        "game_name":     game_name,
        "current_score": current_score,
    })