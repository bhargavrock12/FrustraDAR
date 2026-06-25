from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session
from typing import List
from uuid import UUID
from app.db.database import get_db
from app.schemas.alert import AlertResponse, AlertAcknowledge
from app.services.alert_service import AlertService
from app.core.dependencies import get_current_user, get_current_parent
from app.models.user import User

router = APIRouter()


@router.get("/", response_model=List[AlertResponse])
def get_alerts(
    limit: int = 50,
    skip:  int = 0,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Get alerts — students get own, parents get children's"""
    alert_svc = AlertService(db)

    # If parent, get all children alerts combined
    if current_user.role.value == "parent":
        children = db.query(User).filter(
            User.parent_id == current_user.id
        ).all()

        all_alerts = []
        for child in children:
            alerts = alert_svc.get_user_alerts(child.id, limit, skip)
            all_alerts.extend(alerts)

        # Sort by time
        all_alerts.sort(key=lambda a: a.sent_at, reverse=True)
        return all_alerts[:limit]

    return alert_svc.get_user_alerts(current_user.id, limit, skip)


@router.get("/unread-count")
def get_unread_count(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Get count of unread alerts"""
    count = AlertService(db).get_unread_count(current_user.id)
    return {"unread_count": count}


@router.put("/{alert_id}/acknowledge")
def acknowledge_alert(
    alert_id: UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Mark alert as read"""
    alert = AlertService(db).acknowledge_alert(alert_id, current_user.id)
    return {"message": "Alert acknowledged", "alert_id": str(alert_id)}