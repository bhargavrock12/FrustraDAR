import uuid
from datetime import datetime
from sqlalchemy import (
    Column, Float, DateTime,
    Integer, String, Date, ForeignKey,
    UniqueConstraint, Text
)
from sqlalchemy.dialects.postgresql import UUID, ARRAY
from sqlalchemy.orm import relationship
from app.db.database import Base


class DailySummary(Base):
    __tablename__ = "daily_summaries"

    # ── Identity ──────────────────────────────────────────────────────────
    id      = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(
        UUID(as_uuid=True),
        ForeignKey("users.id"),
        nullable=False,
        index=True
    )
    date = Column(Date, nullable=False)

    # ── Playtime Stats ────────────────────────────────────────────────────
    total_play_time_min      = Column(Integer, nullable=False, default=0)
    total_sessions           = Column(Integer, nullable=False, default=0)
    avg_session_duration_min = Column(Integer, nullable=False, default=0)

    # ── Session Timing ────────────────────────────────────────────────────
    night_play_time_min  = Column(Integer, nullable=False, default=0)
    night_session_count  = Column(Integer, nullable=False, default=0)
    weekday_play_time_min = Column(Integer, nullable=False, default=0)
    weekend_play_time_min = Column(Integer, nullable=False, default=0)

    # ── Frustration Stats ─────────────────────────────────────────────────
    avg_frustration_score = Column(Float,    nullable=True)
    max_frustration_score = Column(Float,    nullable=True)
    peak_frustration_time = Column(DateTime, nullable=True)

    # ── Game Info ─────────────────────────────────────────────────────────
    most_played_game = Column(String(100), nullable=True)

    # ── Behavioral Signals ────────────────────────────────────────────────
    reopen_events = Column(Integer, nullable=False, default=0)
    consecutive_days = Column(Integer, nullable=False, default=0)

    # Indicator strings — e.g. LONG_SESSION, HIGH_DAILY_PLAYTIME
    # Analytics only — not a score or diagnosis
    behavioral_indicators = Column(
        ARRAY(Text()),
        nullable=True,
        default=list
    )

    # ── Legacy Column ─────────────────────────────────────────────────────
    # addiction_risk_score: left in DB for compatibility
    # Application no longer reads, writes, or exposes this column
    addiction_risk_score = Column(Float, nullable=True)

    # ── Timestamps ────────────────────────────────────────────────────────
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(
        DateTime,
        default=datetime.utcnow,
        onupdate=datetime.utcnow
    )

    # ── Constraints ───────────────────────────────────────────────────────
    __table_args__ = (
        UniqueConstraint("user_id", "date", name="uq_user_date"),
    )

    # ── Relationships ─────────────────────────────────────────────────────
    user = relationship("User", back_populates="daily_summaries")

    def __repr__(self) -> str:
        return f"<DailySummary user={self.user_id} date={self.date}>"