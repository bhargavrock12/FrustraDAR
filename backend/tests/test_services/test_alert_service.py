"""Service-level tests for AlertService: threshold + cooldown logic, isolated from HTTP.

Cooldown is time-based (settings.ALERT_COOLDOWN_MINUTES, via datetime.utcnow()). Elapsed-cooldown is
simulated deterministically by ageing the prior alert's sent_at — never by sleeping.
"""

import uuid
from datetime import datetime, timedelta

import pytest

from app.models.alert import Alert
from app.models.user import User, UserRole
from app.services.alert_service import AlertService

pytestmark = pytest.mark.services


def _make_student(db):
    user = User(
        email=f"svc-{uuid.uuid4().hex[:8]}@example.com",
        username="svcstudent",
        hashed_password="x",  # not exercised by alert logic
        role=UserRole.STUDENT,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def test_below_high_threshold_returns_none(db_session):
    user = _make_student(db_session)
    assert AlertService(db_session).check_and_alert(user, 50.0) is None


def test_high_and_critical_severity(db_session):
    svc = AlertService(db_session)

    high_user = _make_student(db_session)
    high = svc.check_and_alert(high_user, 75.0)
    assert high is not None
    assert high.severity == "high"
    assert high.alert_type == "high_frustration"

    crit_user = _make_student(db_session)
    crit = svc.check_and_alert(crit_user, 90.0)
    assert crit is not None
    assert crit.severity == "critical"
    assert crit.alert_type == "critical_frustration"


def test_cooldown_suppresses_second_alert(db_session):
    user = _make_student(db_session)
    svc = AlertService(db_session)

    first = svc.check_and_alert(user, 90.0)
    assert first is not None
    # Same type within the cooldown window -> suppressed.
    assert svc.check_and_alert(user, 92.0) is None


def test_alert_recreated_after_cooldown(db_session):
    user = _make_student(db_session)
    svc = AlertService(db_session)

    first = svc.check_and_alert(user, 90.0)
    assert first is not None

    # Age the prior alert beyond the cooldown window.
    first.sent_at = datetime.utcnow() - timedelta(minutes=10)
    db_session.commit()

    second = svc.check_and_alert(user, 91.0)
    assert second is not None
    assert db_session.query(Alert).filter(Alert.user_id == user.id).count() == 2
