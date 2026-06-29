"""Alerts through the ingestion path: threshold creation, cooldown, retrieve, ack, unread-count.

Alerts are created by EventPipeline.on_scores_uploaded -> AlertService.check_and_alert when the
max fusion_score of a batch crosses the HIGH (70) / CRITICAL (85) thresholds. The 5-minute cooldown
(settings.ALERT_COOLDOWN_MINUTES) suppresses a second alert of the same type. FCM/email dispatch is
stubbed by conftest, but the Alert row is still persisted.
"""

from datetime import datetime, timedelta

import pytest

from tests.conftest import API

pytestmark = pytest.mark.api

START = "2026-08-24T14:00:00"
TS = "2026-08-24T14:00:30"


def _open_session(client, headers):
    resp = client.post(
        f"{API}/sessions/start",
        json={"game_name": "Example", "start_time": START},
        headers=headers,
    )
    return resp.json()["id"]


def _upload(client, headers, session_id, fusion):
    return client.post(
        f"{API}/scores/batch",
        json={
            "session_id": session_id,
            "scores": [{"timestamp": TS, "fusion_score": fusion, "signals_used": ["facial"]}],
        },
        headers=headers,
    )


def test_critical_score_creates_alert(client, student):
    session_id = _open_session(client, student["headers"])
    assert _upload(client, student["headers"], session_id, 90.0).status_code == 201

    alerts = client.get(f"{API}/alerts/", headers=student["headers"])
    assert alerts.status_code == 200
    body = alerts.json()
    assert len(body) == 1
    assert body[0]["severity"] == "critical"


def test_high_score_creates_high_alert(client, student):
    session_id = _open_session(client, student["headers"])
    _upload(client, student["headers"], session_id, 75.0)
    body = client.get(f"{API}/alerts/", headers=student["headers"]).json()
    assert len(body) == 1
    assert body[0]["severity"] == "high"


def test_below_threshold_creates_no_alert(client, student):
    session_id = _open_session(client, student["headers"])
    _upload(client, student["headers"], session_id, 50.0)
    assert client.get(f"{API}/alerts/", headers=student["headers"]).json() == []


def test_cooldown_suppresses_duplicate(client, student):
    session_id = _open_session(client, student["headers"])
    _upload(client, student["headers"], session_id, 90.0)
    # Second qualifying upload of the same type within the cooldown window -> no new alert.
    _upload(client, student["headers"], session_id, 92.0)
    assert len(client.get(f"{API}/alerts/", headers=student["headers"]).json()) == 1


def test_alert_recreated_after_cooldown_elapses(client, student, db_session):
    from app.models.alert import Alert

    session_id = _open_session(client, student["headers"])
    _upload(client, student["headers"], session_id, 90.0)

    # Deterministically age the existing alert past the cooldown window (no sleeping).
    alert = db_session.query(Alert).filter(Alert.user_id == student["user"]["id"]).one()
    alert.sent_at = datetime.utcnow() - timedelta(minutes=10)
    db_session.commit()

    _upload(client, student["headers"], session_id, 91.0)
    assert len(client.get(f"{API}/alerts/", headers=student["headers"]).json()) == 2


def test_unread_count_and_acknowledge(client, student):
    session_id = _open_session(client, student["headers"])
    _upload(client, student["headers"], session_id, 90.0)

    count = client.get(f"{API}/alerts/unread-count", headers=student["headers"])
    assert count.status_code == 200
    assert count.json() == {"unread_count": 1}

    alert_id = client.get(f"{API}/alerts/", headers=student["headers"]).json()[0]["id"]
    ack = client.put(f"{API}/alerts/{alert_id}/acknowledge", headers=student["headers"])
    assert ack.status_code == 200
    assert ack.json() == {"message": "Alert acknowledged", "alert_id": str(alert_id)}

    after = client.get(f"{API}/alerts/unread-count", headers=student["headers"])
    assert after.json() == {"unread_count": 0}
