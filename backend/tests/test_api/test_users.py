"""Users routes: profile get/put, account deactivation (soft delete)."""

import uuid

import pytest

from tests.conftest import API

pytestmark = pytest.mark.api


def test_get_profile_returns_current_user(client, student):
    resp = client.get(f"{API}/users/profile", headers=student["headers"])
    assert resp.status_code == 200
    assert resp.json()["email"] == student["user"]["email"]


def test_update_profile(client, student):
    new_name = f"renamed_{uuid.uuid4().hex[:6]}"
    resp = client.put(
        f"{API}/users/profile",
        params={"username": new_name},
        headers=student["headers"],
    )
    assert resp.status_code == 200
    assert resp.json() == {"message": "Profile updated", "username": new_name}


def test_delete_account_is_soft_deactivation(client, student, db_session):
    from app.models.user import User

    resp = client.delete(f"{API}/users/account", headers=student["headers"])
    assert resp.status_code == 200
    assert resp.json() == {"message": "Account deactivated"}

    # The row must still exist, only flagged inactive (never physically deleted).
    row = db_session.query(User).filter(User.email == student["user"]["email"]).first()
    assert row is not None
    assert row.is_active is False
