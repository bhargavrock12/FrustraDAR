from pydantic import BaseModel
from typing import Optional
from datetime import datetime
from uuid import UUID


class SessionStart(BaseModel):
    """Called when user opens a game"""
    game_package: Optional[str] = None
    game_name:    Optional[str] = None
    start_time:   datetime


class SessionEnd(BaseModel):
    """Called when user closes a game"""
    end_time:     datetime
    reopen_count: Optional[int] = 0


class SessionResponse(BaseModel):
    id:           UUID
    game_package: Optional[str]
    game_name:    Optional[str]
    start_time:   datetime
    end_time:     Optional[datetime]
    duration_sec: Optional[int]
    is_night:     bool
    reopen_count: int
    is_active:    bool

    class Config:
        from_attributes = True