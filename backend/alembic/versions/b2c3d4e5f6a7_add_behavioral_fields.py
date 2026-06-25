"""add behavioral analytics fields to daily_summaries

Revision ID: b2c3d4e5f6a7
Revises: a1b2c3d4e5f6
Create Date: 2025-01-01 00:01:00.000000

Changes:
    daily_summaries table:
        ADD avg_session_duration_min  INTEGER  DEFAULT 0  NOT NULL
        ADD night_session_count       INTEGER  DEFAULT 0  NOT NULL
        ADD weekday_play_time_min     INTEGER  DEFAULT 0  NOT NULL
        ADD weekend_play_time_min     INTEGER  DEFAULT 0  NOT NULL
        ADD behavioral_indicators     TEXT[]   DEFAULT '{}'

    addiction_risk_score:
        NOT dropped — left as legacy unused column.
        Application no longer reads, writes, or exposes it.

Verification:
    Migration chain: None → a1b2c3d4e5f6 → b2c3d4e5f6a7
    Only adds new columns to existing table.
    No data is dropped or modified.
    Safe to apply against live database.
"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision: str = "b2c3d4e5f6a7"
down_revision: Union[str, None] = "a1b2c3d4e5f6"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "daily_summaries",
        sa.Column(
            "avg_session_duration_min",
            sa.Integer(),
            nullable=False,
            server_default="0"
        )
    )
    op.add_column(
        "daily_summaries",
        sa.Column(
            "night_session_count",
            sa.Integer(),
            nullable=False,
            server_default="0"
        )
    )
    op.add_column(
        "daily_summaries",
        sa.Column(
            "weekday_play_time_min",
            sa.Integer(),
            nullable=False,
            server_default="0"
        )
    )
    op.add_column(
        "daily_summaries",
        sa.Column(
            "weekend_play_time_min",
            sa.Integer(),
            nullable=False,
            server_default="0"
        )
    )
    op.add_column(
        "daily_summaries",
        sa.Column(
            "behavioral_indicators",
            postgresql.ARRAY(sa.Text()),
            nullable=True,
            server_default="{}"
        )
    )


def downgrade() -> None:
    op.drop_column("daily_summaries", "behavioral_indicators")
    op.drop_column("daily_summaries", "weekend_play_time_min")
    op.drop_column("daily_summaries", "weekday_play_time_min")
    op.drop_column("daily_summaries", "night_session_count")
    op.drop_column("daily_summaries", "avg_session_duration_min")