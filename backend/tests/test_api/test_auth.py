"""Auth routes: register/login/JWT, /me, link-parent, children, fcm-token."""

import uuid

import pytest

from tests.conftest import API, register

pytestmark = pytest.mark.api


def test_register_returns_token_and_user(client):
    headers, user, token = register(client, "student")
    assert token
    assert user["role"] == "student"
    assert user["is_active"] is True


def test_login_succeeds_with_valid_credentials(client):
    # Register with a known password, then log in.
    email = f"login-{uuid.uuid4().hex[:8]}@example.com"
    payload = {
        "email": email,
        "username": "loginuser",
        "password": "secret123",
        "role": "student",
    }
    assert client.post(f"{API}/auth/register", json=payload).status_code == 201
    resp = client.post(f"{API}/auth/login", json={"email": email, "password": "secret123"})
    assert resp.status_code == 200
    assert resp.json()["access_token"]


def test_login_bad_password_rejected(client):
    _, user, _ = register(client, "student")
    resp = client.post(
        f"{API}/auth/login",
        json={"email": user["email"], "password": "wrong-password"},
    )
    assert resp.status_code == 401


def test_duplicate_email_rejected(client):
    _, user, _ = register(client, "student")
    dup = {
        "email": user["email"],
        "username": "another",
        "password": "secret123",
        "role": "student",
    }
    resp = client.post(f"{API}/auth/register", json=dup)
    assert resp.status_code == 400


def test_me_reflects_current_user(client):
    headers, user, _ = register(client, "parent")
    resp = client.get(f"{API}/auth/me", headers=headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["email"] == user["email"]
    assert body["role"] == "parent"


def test_link_parent_and_children(client):
    p_headers, p_user, _ = register(client, "parent")
    s_headers, s_user, _ = register(client, "student")

    link = client.post(
        f"{API}/auth/link-parent",
        json={"parent_email": p_user["email"]},
        headers=s_headers,
    )
    assert link.status_code == 200

    children = client.get(f"{API}/auth/children", headers=p_headers)
    assert children.status_code == 200
    child_ids = [c["id"] for c in children.json()]
    assert s_user["id"] in child_ids


def test_update_fcm_token(client):
    headers, _, _ = register(client, "student")
    resp = client.put(
        f"{API}/auth/fcm-token",
        json={"fcm_token": "device-token-abc"},
        headers=headers,
    )
    assert resp.status_code == 200
    assert resp.json() == {"message": "FCM token updated"}
