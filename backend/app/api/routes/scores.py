import logging
from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session
from typing import List
from uuid import UUID
from app.db.database import get_db
from app.schemas.score import ScoreBatchCreate, ScoreResponse
from app.services.score_service import ScoreService
from app.services.event_pipeline import EventPipeline
from app.core.dependencies import get_current_student
from app.models.user import User

logger = logging.getLogger(__name__)
router = APIRouter()


@router.post("/batch", status_code=status.HTTP_201_CREATED)
async def upload_scores_batch(
    batch:        ScoreBatchCreate,
    current_user: User    = Depends(get_current_student),
    db:           Session = Depends(get_db)
):
    """
    Upload batch of frustration scores from Android app.
    Score persistence + alert/WS/FCM pipeline runs here.
    """
    score_svc = ScoreService(db)
    result    = score_svc.upload_batch(current_user, batch)

    # Trigger event pipeline (orchestrator)
    pipeline = EventPipeline(db)
    await pipeline.on_scores_uploaded(
        user=current_user,
        session=result["session"],
        max_score=result["max_score"],
        latest_score=result["latest_score"]
    )

    return {
        "message":  "Scores uploaded successfully",
        "uploaded": result["uploaded"],
        "max_score": result["max_score"]
    }


@router.get("/latest", response_model=List[ScoreResponse])
def get_latest_scores(
    limit:        int     = 20,
    current_user: User    = Depends(get_current_student),
    db:           Session = Depends(get_db)
):
    return ScoreService(db).get_latest_scores(current_user.id, limit)


@router.get("/trends")
def get_score_trends(
    days:         int     = 7,
    current_user: User    = Depends(get_current_student),
    db:           Session = Depends(get_db)
):
    return ScoreService(db).get_trend_data(current_user.id, days)


@router.get("/session/{session_id}", response_model=List[ScoreResponse])
def get_session_scores(
    session_id:   UUID,
    current_user: User    = Depends(get_current_student),
    db:           Session = Depends(get_db)
):
    return ScoreService(db).get_session_scores(session_id, current_user.id)