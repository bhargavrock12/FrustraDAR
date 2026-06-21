import uuid
from datetime import datetime
from sqlalchemy import (
    Column, Float, DateTime,
    Integer, ForeignKey, ARRAY, String
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import relationship
from app.db.database import Base


class FrustrationScore(Base):
    __tablename__ = "frustration_scores"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    # Who
    user_id    = Column(UUID(as_uuid=True), ForeignKey("users.id"),    nullable=False, index=True)
    session_id = Column(UUID(as_uuid=True), ForeignKey("sessions.id"), nullable=True,  index=True)

    # When
    timestamp  = Column(DateTime, nullable=False, index=True)

    # Individual signal scores (None if signal not available)
    facial_score   = Column(Float, nullable=True)   # 0-100
    audio_score    = Column(Float, nullable=True)   # 0-100
    motion_score   = Column(Float, nullable=True)   # 0-100
    behavior_score = Column(Float, nullable=True)   # 0-100

    # Final fused score
    fusion_score = Column(Float, nullable=False)    # 0-100

    # Which signals were actually used (camera might be off etc.)
    signals_used = Column(ARRAY(String), default=[])

    # Window duration in seconds (how much data this score covers)
    window_duration_sec = Column(Integer, default=90)

    created_at = Column(DateTime, default=datetime.utcnow)

    # Relationships
    user    = relationship("User",        back_populates="scores")
    session = relationship("GameSession", back_populates="scores")

    def __repr__(self):
        return f"<Score fusion={self.fusion_score} user={self.user_id}>"