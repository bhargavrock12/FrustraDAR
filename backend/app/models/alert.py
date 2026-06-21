import uuid
from datetime import datetime
from sqlalchemy import (
    Column, String, Float,
    DateTime, Boolean, Text, ForeignKey
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import relationship
from app.db.database import Base


class Alert(Base):
    __tablename__ = "alerts"

    # ── Identity ──────────────────────────────────────────────────────────
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(
        UUID(as_uuid=True),
        ForeignKey("users.id"),
        nullable=False,
        index=True
    )

    # ── Alert Classification ──────────────────────────────────────────────
    # Valid alert_type values:
    #   high_frustration      — fusion score >= FRUSTRATION_HIGH_THRESHOLD
    #   critical_frustration  — fusion score >= FRUSTRATION_CRITICAL_THRESHOLD
    #   night_session         — gaming started after NIGHT_START_HOUR
    #   rapid_reopen          — rapid reopen detected by Android, stored here
    alert_type = Column(String(50),  nullable=False)

    # Valid severity values: low | medium | high | critical
    severity = Column(String(20), nullable=False)

    # ── Content ───────────────────────────────────────────────────────────
    message         = Column(Text,        nullable=True)
    triggered_score = Column(Float,       nullable=True)
    sent_to         = Column(String(100), nullable=True)

    # ── Status ───────────────────────────────────────────────────────────
    sent_at      = Column(DateTime, default=datetime.utcnow, nullable=False)
    acknowledged = Column(Boolean,  default=False,           nullable=False)

    # ── Relationships ─────────────────────────────────────────────────────
    user = relationship("User", back_populates="alerts")

    def __repr__(self) -> str:
        return (
            f"<Alert type={self.alert_type} "
            f"severity={self.severity} "
            f"user={self.user_id}>"
        )