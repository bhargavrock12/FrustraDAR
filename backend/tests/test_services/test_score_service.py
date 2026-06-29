"""Service-level tests for ScoreService: batch persistence + query helpers.

No server-side fusion is performed (FD-9); the precomputed fusion_score is stored verbatim.
"""

import uuid
from datetime import datetime

import pytest

from app.models.session import GameSession
from app.models.user import User, UserRole
from app.schemas.score import ScoreBatchCreate, ScoreCreate
from app.services.score_service import ScoreService

pytestmark = pytest.mark.services


def _student_with_session(db):
    user = User(
        email=f"svc-{uuid.uuid4().hex[:8]}@example.com",
        username="svcstudent",
        hashed_password="x",
        role=UserRole.STUDENT,
    )
    db.add(user)
    db.commit()
    db.refresh(user)

    session = GameSession(
        user_id=user.id,
        game_name="Example",
        start_time=datetime.utcnow(),
    )
    db.add(session)
    db.commit()
    db.refresh(session)
    return user, session


def _batch(session_id, fusions):
    return ScoreBatchCreate(
        session_id=session_id,
        scores=[
            ScoreCreate(timestamp=datetime.utcnow(), fusion_score=f, signals_used=["facial"])
            for f in fusions
        ],
    )


def test_upload_batch_persists_and_reports(db_session):
    user, session = _student_with_session(db_session)
    result = ScoreService(db_session).upload_batch(user, _batch(session.id, [40.0, 55.0]))
    assert result["uploaded"] == 2
    assert result["max_score"] == 55.0


def test_upload_batch_unowned_session_raises(db_session):
    user, _ = _student_with_session(db_session)
    from fastapi import HTTPException

    with pytest.raises(HTTPException) as exc:
        ScoreService(db_session).upload_batch(user, _batch(uuid.uuid4(), [40.0]))
    assert exc.value.status_code == 404


def test_query_helpers_return_persisted_rows(db_session):
    user, session = _student_with_session(db_session)
    svc = ScoreService(db_session)
    svc.upload_batch(user, _batch(session.id, [40.0, 60.0]))

    latest = svc.get_latest_scores(user.id, limit=20)
    assert len(latest) == 2

    session_scores = svc.get_session_scores(session.id, user.id)
    assert len(session_scores) == 2
