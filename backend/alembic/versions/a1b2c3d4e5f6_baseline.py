"""baseline — existing schema created by create_all

Revision ID: a1b2c3d4e5f6
Revises: None
Create Date: 2025-01-01 00:00:00.000000

Purpose:
    Establishes Alembic history baseline for the existing database
    that was created using SQLAlchemy Base.metadata.create_all().

    This migration does NOT create or modify any tables.
    All tables already exist physically in PostgreSQL.

    After stamping this baseline, subsequent migrations
    apply only the incremental schema changes.
"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa

revision: str = "a1b2c3d4e5f6"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """
    No-op. Tables already exist from create_all().
    This migration only establishes the Alembic version baseline.
    """
    pass


def downgrade() -> None:
    """
    No-op. Downgrading baseline would mean dropping all tables.
    That must be done manually if ever required.
    """
    pass