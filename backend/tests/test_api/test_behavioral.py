"""Behavioral analytics: daily-summary computation persists behavioral_indicators, and the legacy
addiction_risk_score column is never read/written/exposed (FD-18).

These tests assert the *existing* computation's shape and the FD-18 guarantee; they do not re-derive
behavioral thresholds (that logic is owned by the frozen service).
"""

import pytest

from tests.conftest import API

pytestmark = pytest.mark.api

START = "2026-08-24T14:00:00"
END = "2026-08-24T16:00:00"  # 2h session (long-play territory)


def _completed_session(client, headers):
    started = client.post(
        f"{API}/sessions/start",
        json={"game_name": "Example", "start_time": START},
        headers=headers,
    ).json()
    client.put(
        f"{API}/sessions/{started['id']}/end",
        json={"end_time": END, "reopen_count": 0},
        headers=headers,
    )


def test_daily_summary_persists_behavioral_indicators(client, student, db_session):
    from app.models.daily_summary import DailySummary

    _completed_session(client, student["headers"])
    resp = client.post(f"{API}/dashboard/compute-daily-summary", headers=student["headers"])
    assert resp.status_code == 200

    summary = (
        db_session.query(DailySummary)
        .filter(DailySummary.user_id == student["user"]["id"])
        .first()
    )
    assert summary is not None
    # behavioral_indicators is an ARRAY(Text) column defaulting to [] (added by b2c3d4e5f6a7).
    assert isinstance(summary.behavioral_indicators, list)


def test_addiction_field_never_written_by_computation(client, student, db_session):
    from app.models.daily_summary import DailySummary

    _completed_session(client, student["headers"])
    client.post(f"{API}/dashboard/compute-daily-summary", headers=student["headers"])

    summary = (
        db_session.query(DailySummary)
        .filter(DailySummary.user_id == student["user"]["id"])
        .first()
    )
    # The dormant legacy column stays untouched by the current computation (never populated).
    assert summary.addiction_risk_score is None
