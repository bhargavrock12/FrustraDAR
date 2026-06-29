"""WebSocket: JWT-authenticated connect yields the `connected` event.

Only the `connected` event is verified here. Per docs/11 (I-D1), the full WS event schema is not yet
confirmed against the backend, so no other event shapes are asserted.
"""

import pytest
from starlette.websockets import WebSocketDisconnect

pytestmark = pytest.mark.api


def test_connect_with_valid_token(client, student):
    with client.websocket_connect(f"/ws?token={student['token']}") as ws:
        msg = ws.receive_json()
    assert msg["type"] == "connected"
    assert msg["data"]["user_id"] == student["user"]["id"]
    assert msg["data"]["role"] == "student"


def test_connect_without_token_rejected(client):
    # Missing token -> Query(...) required -> the connection is refused before the app accepts it.
    with pytest.raises((WebSocketDisconnect, Exception)):
        with client.websocket_connect("/ws") as ws:
            ws.receive_json()


def test_connect_with_invalid_token_rejected(client):
    with pytest.raises(WebSocketDisconnect):
        with client.websocket_connect("/ws?token=not-a-valid-jwt") as ws:
            ws.receive_json()
