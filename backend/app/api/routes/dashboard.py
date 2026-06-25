from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from uuid import UUID

from app.db.database import get_db
from app.services.dashboard_service import DashboardService
from app.services.trend_service import TrendService
from app.core.dependencies import get_current_user, get_current_parent
from app.models.user import User

router = APIRouter()


@router.get("/")
def get_dashboard(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Main dashboard endpoint.
    Returns student or parent view depending on JWT role.
    """
    svc = DashboardService(db)
    if current_user.role.value == "student":
        return svc.get_student_dashboard(current_user)
    return svc.get_parent_dashboard(current_user)


@router.get("/child/{child_id}")
def get_child_dashboard(
    child_id: UUID,
    current_user: User = Depends(get_current_parent),
    db: Session = Depends(get_db)
):
    """Detailed dashboard for a specific child — parent only."""
    return DashboardService(db).get_child_detail(current_user, child_id)


@router.get("/weekly-report")
def get_weekly_report(
    weeks_ago: int = 0,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Weekly behavioral analytics for current user."""
    return TrendService(db).get_weekly_report(current_user.id, weeks_ago)


@router.post("/compute-daily-summary")
def compute_daily_summary(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Manually trigger daily summary computation.
    Normally triggered at end of day or by a scheduler.
    """
    summary = TrendService(db).compute_daily_summary(current_user.id)
    return {
        "message": "Daily summary computed",
        "date": str(summary.date)
    }