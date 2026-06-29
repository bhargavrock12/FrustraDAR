"""Dashboard + reports routes, and the FD-18 guarantee: addiction_risk_score is never exposed."""

import pytest

from tests.conftest import API

pytestmark = pytest.mark.api


def _assert_no_addiction_field(obj):
    """Recursively assert the dormant legacy column never surfaces in any response (FD-18)."""
    if isinstance(obj, dict):
        assert "addiction_risk_score" not in obj
        for value in obj.values():
            _assert_no_addiction_field(value)
    elif isinstance(obj, list):
        for item in obj:
            _assert_no_addiction_field(item)


def test_student_dashboard(client, student):
    resp = client.get(f"{API}/dashboard/", headers=student["headers"])
    assert resp.status_code == 200
    _assert_no_addiction_field(resp.json())


def test_parent_dashboard(client, linked_pair):
    resp = client.get(f"{API}/dashboard/", headers=linked_pair["parent"]["headers"])
    assert resp.status_code == 200
    _assert_no_addiction_field(resp.json())


def test_child_dashboard_requires_link(client, linked_pair, parent):
    student_id = linked_pair["student"]["user"]["id"]

    # Linked parent may view the child.
    ok = client.get(
        f"{API}/dashboard/child/{student_id}",
        headers=linked_pair["parent"]["headers"],
    )
    assert ok.status_code == 200
    _assert_no_addiction_field(ok.json())

    # A different, unlinked parent must be denied.
    denied = client.get(f"{API}/dashboard/child/{student_id}", headers=parent["headers"])
    assert denied.status_code >= 400


def test_compute_daily_summary(client, student):
    resp = client.post(f"{API}/dashboard/compute-daily-summary", headers=student["headers"])
    assert resp.status_code == 200
    body = resp.json()
    assert body["message"] == "Daily summary computed"
    assert "date" in body


def test_weekly_and_monthly_reports(client, student):
    weekly = client.get(f"{API}/reports/weekly", headers=student["headers"])
    assert weekly.status_code == 200
    _assert_no_addiction_field(weekly.json())

    monthly = client.get(f"{API}/reports/monthly", headers=student["headers"])
    assert monthly.status_code == 200
    _assert_no_addiction_field(monthly.json())


def test_send_weekly_email_is_stubbed(client, linked_pair):
    # SendGrid is stubbed in conftest; the route should still report success for a parent.
    resp = client.post(
        f"{API}/reports/send-weekly-email",
        headers=linked_pair["parent"]["headers"],
    )
    assert resp.status_code == 200
    body = resp.json()
    assert "sent_to" in body
    assert body["sent_to"] == linked_pair["parent"]["user"]["email"]
