
from pydantic import BaseModel

from typing import Optional, List

from datetime import datetime

from uuid import UUID



class ScoreCreate(BaseModel):

    timestamp: datetime

    facial_score: Optional[float] = None

    audio_score: Optional[float] = None

    motion_score: Optional[float] = None

    behavior_score: Optional[float] = None

    fusion_score: float

    signals_used: List[str] = []

    window_duration_sec: int = 90



class ScoreBatchCreate(BaseModel):

    session_id: UUID

    scores: List[ScoreCreate]



class ScoreResponse(BaseModel):

    id: UUID

    timestamp: datetime

    facial_score: Optional[float]

    audio_score: Optional[float]

    motion_score: Optional[float]

    behavior_score: Optional[float]

    fusion_score: float

    signals_used: List[str]

    

    class Config:

        from_attributes = True

