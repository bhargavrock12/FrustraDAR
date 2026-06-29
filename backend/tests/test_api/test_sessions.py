"""Sessions routes: start / end / active / history."""

import pytest

from tests.conftest import API

pytestmark = pytest.mark.api

# A daytime window (avoids the night-alert branch, which is exercised elsewhere).
START = "2026-08-24T14:00:00"
END = "2026-08-24T15:00:00"


def _start_session(client, headers, start=START):
    return client.post(
        f"{API}/sessions/start",
        json={"game_package": "com.example.game", "game_name": "Example", "start_time": start},
        headers=headers,
    )


def test_start_session_creates_active_session(client, student):
    resp = _start_session(client, student["headers"])
    assert resp.status_code == 201, resp.text
    body = resp.json()
    assert body["is_active"] is True
    assert body["game_name"] == "Example"
    assert body["end_time"] is None


def test_end_session_finalizes_duration(client, student):
    started = _start_session(client, student["headers"]).json()
    resp = client.put(
        f"{API}/sessions/{started['id']}/end",
        json={"end_time": END, "reopen_count": 0},
        headers=student["headers"],
    )
    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["is_active"] is False
    assert body["end_time"] is not None
    assert body["duration_sec"] == 3600  # 14:00 -> 15:00


def test_active_session_endpoint(client, student):
    # No active session yet -> 404 per the frozen route.
    assert client.get(f"{API}/sessions/active", headers=student["headers"]).status_code == 404

    _start_session(client, student["headers"])
    resp = client.get(f"{API}/sessions/active", headers=student["headers"])
    assert resp.status_code == 200
    assert resp.json()["is_active"] is True


def test_history_lists_sessions(client, student):
    _start_session(client, student["headers"])
    resp = client.get(f"{API}/sessions/history", headers=student["headers"])
    assert resp.status_code == 200
    assert isinstance(resp.json(), list)
    assert len(resp.json()) >= 1
