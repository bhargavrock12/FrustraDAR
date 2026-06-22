from pydantic import BaseModel
from typing import Optional
from datetime import datetime
from uuid import UUID


class AlertResponse(BaseModel):
    id:              UUID
    alert_type:      str
    severity:        str
    message:         Optional[str]
    triggered_score: Optional[float]
    sent_at:         datetime
    acknowledged:    bool

    class Config:
        from_attributes = True


class AlertAcknowledge(BaseModel):
    acknowledged: bool = True


class AlertSettings(BaseModel):
    """Parent configures when to get alerts"""
    high_frustration_threshold:     float = 70.0
    critical_frustration_threshold: float = 85.0
    daily_hours_threshold:          float = 3.0
    night_gaming_alert:             bool  = True
    rapid_reopen_alert:             bool  = True