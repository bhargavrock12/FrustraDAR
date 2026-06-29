"""Root + health endpoints (app.main)."""

import pytest

pytestmark = pytest.mark.api


def test_root_returns_running_payload(client):
    resp = client.get("/")
    assert resp.status_code == 200
    body = resp.json()
    # The frozen root handler reports the app is running and advertises version 1.0.0.
    assert body.get("version") == "1.0.0"


def test_health_ok(client):
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "healthy"}
