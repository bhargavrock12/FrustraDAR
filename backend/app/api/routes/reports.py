from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.services.trend_service import TrendService
from app.utils.email import send_weekly_report_email
from app.core.dependencies import get_current_user, get_current_parent
from app.models.user import User

router = APIRouter()


@router.get("/weekly")
def get_weekly_report(
    weeks_ago:    int     = 0,
    current_user: User    = Depends(get_current_user),
    db:           Session = Depends(get_db)
):
    """
    Weekly behavioral analytics report.
    weeks_ago=0 → current week
    weeks_ago=1 → previous week
    Includes current vs previous week comparison and trend indicators.
    """
    return TrendService(db).get_weekly_report(current_user.id, weeks_ago)


@router.get("/monthly")
def get_monthly_report(
    months_ago:   int     = 0,
    current_user: User    = Depends(get_current_user),
    db:           Session = Depends(get_db)
):
    """
    Monthly behavioral analytics report.
    months_ago=0 → current month
    months_ago=1 → previous month
    Includes current vs previous month comparison and trend indicators.
    """
    return TrendService(db).get_monthly_report(current_user.id, months_ago)


@router.post("/send-weekly-email")
def send_weekly_email(
    current_user: User    = Depends(get_current_parent),
    db:           Session = Depends(get_db)
):
    """Send weekly report email to parent for all linked children."""
    children = db.query(User).filter(
        User.parent_id == current_user.id
    ).all()

    sent_count = 0
    for child in children:
        report = TrendService(db).get_weekly_report(child.id)
        send_weekly_report_email(
            parent_email=current_user.email,
            child_name=child.username,
            report=report
        )
        sent_count += 1

    return {
        "message": f"Weekly reports sent for {sent_count} children",
        "sent_to": current_user.email,
    }