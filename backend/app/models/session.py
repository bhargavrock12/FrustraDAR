import uuid                    # Generate unique UUIDs for each game session
from datetime import datetime  # Store creation timestamps

from sqlalchemy import (
    Column, String, DateTime,
    Integer, Boolean, ForeignKey
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import relationship

from app.db.database import Base


# ------------------------------------------------------------------
# GAME SESSION MODEL
# ------------------------------------------------------------------
# Represents ONE gaming session.
#
# Example:
# User opens Clash Royale at 8 PM and closes it at 9 PM.
# This creates ONE GameSession record.
#
# If the same user plays tomorrow,
# another GameSession record is created.
#
class GameSession(Base):

    # PostgreSQL table name
    __tablename__ = "sessions"

    # ------------------------------------------------------------------
    # PRIMARY KEY
    # ------------------------------------------------------------------
    # Unique identifier for every gaming session.
    id = Column(
        UUID(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4
    )

    # ------------------------------------------------------------------
    # USER RELATION
    # ------------------------------------------------------------------
    # Foreign Key linking this session to a user.
    #
    # One User
    #      │
    #      ├── Session 1
    #      ├── Session 2
    #      └── Session 3
    #
    user_id = Column(
        UUID(as_uuid=True),
        ForeignKey("users.id"),
        nullable=False,
        index=True      # Faster lookup of all sessions belonging to a user
    )

    # ------------------------------------------------------------------
    # GAME INFORMATION
    # ------------------------------------------------------------------

    # Android package name
    # Example:
    # com.supercell.clashofclans
    game_package = Column(
        String(200),
        nullable=True
    )

    # Human-readable game name
    # Example:
    # Clash of Clans
    game_name = Column(
        String(100),
        nullable=True
    )

    # ------------------------------------------------------------------
    # SESSION TIMING
    # ------------------------------------------------------------------

    # When the game started
    start_time = Column(
        DateTime,
        nullable=False
    )

    # When the game ended
    end_time = Column(
        DateTime,
        nullable=True
    )

    # Total play duration in seconds.
    # Stored directly for faster analytics
    # instead of recalculating every time.
    duration_sec = Column(
        Integer,
        nullable=True
    )

    # ------------------------------------------------------------------
    # BEHAVIOUR FLAGS
    # ------------------------------------------------------------------

    # True if user played between
    # approximately 11 PM and 5 AM.
    # Used for addiction analysis.
    is_night = Column(
        Boolean,
        default=False
    )

    # True while the session is still running.
    # Becomes False once the game ends.
    is_active = Column(
        Boolean,
        default=True
    )

    # Number of times the user reopened
    # the game during this session/day.
    # Used as a behavioural signal.
    reopen_count = Column(
        Integer,
        default=0
    )

    # Timestamp when this database record was created
    created_at = Column(
        DateTime,
        default=datetime.utcnow
    )

    # ------------------------------------------------------------------
    # RELATIONSHIPS
    # ------------------------------------------------------------------

    # Every GameSession belongs to ONE User.
    user = relationship(
        "User",
        back_populates="sessions"
    )

    # One GameSession can have MANY
    # frustration score records collected over time.
    scores = relationship(
        "FrustrationScore",
        back_populates="session"
    )

    # Used only while debugging/logging
    def __repr__(self):
        return f"<Session {self.game_name} user={self.user_id}>"