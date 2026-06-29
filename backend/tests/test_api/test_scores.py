"""Scores routes: batch upload, latest, trends, session scores, validation.

The backend stores the *precomputed* on-device fusion_score (FD-9); it performs no server-side
fusion. `audio_score` is the Voice Score at the DB/contract layer.
"""

import uuid

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
    assert resp.status_code == 201, resp.text
    return resp.json()["id"]


def _score(fusion=40.0, **overrides):
    payload = {
        "timestamp": TS,
        "facial_score": 30.0,
        "audio_score": 35.0,
        "motion_score": 25.0,
        "behavior_score": 20.0,
        "fusion_score": fusion,
        "signals_used": ["facial", "voice", "motion"],
        "window_duration_sec": 90,
    }
    payload.update(overrides)
    return payload


def test_batch_upload_persists_scores(client, student):
    session_id = _open_session(client, student["headers"])
    resp = client.post(
        f"{API}/scores/batch",
        json={"session_id": session_id, "scores": [_score(40.0), _score(55.0)]},
        headers=student["headers"],
    )
    assert resp.status_code == 201, resp.text
    body = resp.json()
    assert body["message"] == "Scores uploaded successfully"
    assert body["uploaded"] == 2
    assert body["max_score"] == 55.0


def test_fusion_score_is_required(client, student):
    session_id = _open_session(client, student["headers"])
    bad = _score()
    del bad["fusion_score"]
    resp = client.post(
        f"{API}/scores/batch",
        json={"session_id": session_id, "scores": [bad]},
        headers=student["headers"],
    )
    assert resp.status_code == 422


def test_batch_for_unowned_session_rejected(client, student):
    resp = client.post(
        f"{API}/scores/batch",
        json={"session_id": str(uuid.uuid4()), "scores": [_score()]},
        headers=student["headers"],
    )
    assert resp.status_code == 404


def test_latest_and_session_scores(client, student):
    session_id = _open_session(client, student["headers"])
    client.post(
        f"{API}/scores/batch",
        json={"session_id": session_id, "scores": [_score(40.0)]},
        headers=student["headers"],
    )

    latest = client.get(f"{API}/scores/latest", headers=student["headers"])
    assert latest.status_code == 200
    assert isinstance(latest.json(), list)
    assert len(latest.json()) >= 1

    session_scores = client.get(f"{API}/scores/session/{session_id}", headers=student["headers"])
    assert session_scores.status_code == 200
    assert len(session_scores.json()) >= 1


def test_trends_returns_data(client, student):
    session_id = _open_session(client, student["headers"])
    client.post(
        f"{API}/scores/batch",
        json={"session_id": session_id, "scores": [_score(40.0)]},
        headers=student["headers"],
    )
    resp = client.get(f"{API}/scores/trends", params={"days": 7}, headers=student["headers"])
    assert resp.status_code == 200
