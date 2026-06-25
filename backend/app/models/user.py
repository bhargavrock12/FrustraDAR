import uuid                    # Generate unique UUIDs for primary keys
import enum                    # Create Enum values (Student, Parent)
from datetime import datetime  # Store creation/update timestamps

from sqlalchemy import (
    Column, String, DateTime,
    Boolean, ForeignKey, Enum as SAEnum
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import relationship

from app.db.database import Base


# ------------------------------------------------------------------
# USER ROLES
# ------------------------------------------------------------------
# Enum restricts the role to only these values.
# Database will only allow "student" or "parent".
class UserRole(str, enum.Enum):
    STUDENT = "student"
    PARENT = "parent"


# ------------------------------------------------------------------
# USER MODEL
# ------------------------------------------------------------------
# Inherits from Base, so SQLAlchemy treats this class
# as a PostgreSQL table.
class User(Base):

    # PostgreSQL table name
    __tablename__ = "users"

    # ------------------------------------------------------------------
    # PRIMARY KEY
    # ------------------------------------------------------------------
    # UUID is used instead of integer IDs because:
    # - Globally unique
    # - Harder to guess
    # - Better for APIs
    id = Column(
        UUID(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
        index=True
    )

    # ------------------------------------------------------------------
    # BASIC USER INFORMATION
    # ------------------------------------------------------------------

    # Login email
    # unique=True     -> no duplicate emails
    # nullable=False  -> email is mandatory
    # index=True      -> faster email lookup during login
    email = Column(
        String(100),
        unique=True,
        nullable=False,
        index=True
    )

    # Display username
    username = Column(
        String(50),
        nullable=False
    )

    # Store HASHED password (never plain password)
    hashed_password = Column(
        String(255),
        nullable=False
    )

    # ------------------------------------------------------------------
    # USER ROLE
    # ------------------------------------------------------------------
    # Can only be:
    # - student
    # - parent
    role = Column(
        SAEnum(UserRole),
        nullable=False
    )

    # ------------------------------------------------------------------
    # PARENT-STUDENT RELATIONSHIP
    # ------------------------------------------------------------------
    # Self-referencing Foreign Key.
    #
    # Parent is also stored inside the same "users" table.
    #
    # Example:
    #
    # Parent
    # id = abc123
    #
    # Student
    # parent_id = abc123
    #
    # If the user itself is a parent,
    # parent_id remains NULL.
    parent_id = Column(
        UUID(as_uuid=True),
        ForeignKey("users.id"),
        nullable=True
    )

    # Parent email used for alerts.
    # Filled only by student accounts.
    parent_email = Column(
        String(100),
        nullable=True
    )

    # Firebase Cloud Messaging token.
    # Used later to send push notifications.
    fcm_token = Column(
        String(500),
        nullable=True
    )

    # ------------------------------------------------------------------
    # STATUS
    # ------------------------------------------------------------------

    # Soft delete / account active flag
    is_active = Column(
        Boolean,
        default=True
    )

    # Automatically stores registration time
    created_at = Column(
        DateTime,
        default=datetime.utcnow
    )

    # Automatically updates whenever this row changes
    updated_at = Column(
        DateTime,
        default=datetime.utcnow,
        onupdate=datetime.utcnow
    )

    # ------------------------------------------------------------------
    # RELATIONSHIPS
    # ------------------------------------------------------------------
    # These DO NOT create database columns.
    #
    # They tell SQLAlchemy how different models
    # are connected with User.
    #
    # User
    #   ├── Game Sessions
    #   ├── Frustration Scores
    #   ├── Alerts
    #   └── Daily Summaries
    #
    sessions = relationship(
        "GameSession",
        back_populates="user"
    )

    scores = relationship(
        "FrustrationScore",
        back_populates="user"
    )

    alerts = relationship(
        "Alert",
        back_populates="user"
    )

    daily_summaries = relationship(
        "DailySummary",
        back_populates="user"
    )

    # ------------------------------------------------------------------
    # SELF REFERENCING RELATIONSHIP
    # ------------------------------------------------------------------
    # One Parent User
    #        │
    #        ├── Child User
    #        ├── Child User
    #        └── Child User
    #
    # This is called a self-referencing relationship because
    # User points to another User.
    children = relationship(
        "User",
        foreign_keys=[parent_id],
        back_populates="parent"
    )

    parent = relationship(
        "User",
        foreign_keys=[parent_id],
        remote_side=[id],
        back_populates="children"
    )

    # Used only for debugging/logging
    def __repr__(self):
        return f"<User {self.email} ({self.role})>"