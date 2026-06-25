from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session
from app.db.database import get_db
from app.schemas.user import (
    UserCreate, UserLogin, UserResponse,
    TokenResponse, LinkParent, UpdateFCMToken, ChildResponse
)
from app.services.auth_service import AuthService
from app.core.dependencies import get_current_user, get_current_student, get_current_parent
from app.models.user import User
from typing import List

router = APIRouter()


@router.post(
    "/register",
    response_model=TokenResponse,
    status_code=status.HTTP_201_CREATED
)
def register(
    user_data: UserCreate,
    db: Session = Depends(get_db)
):
    """Register a new student or parent account"""
    result = AuthService(db).register(user_data)
    return result


@router.post("/login", response_model=TokenResponse)
def login(
    credentials: UserLogin,
    db: Session = Depends(get_db)
):
    """Login and get JWT token"""
    result = AuthService(db).login(credentials)
    return result


@router.get("/me", response_model=UserResponse)
def get_me(current_user: User = Depends(get_current_user)):
    """Get currently logged in user info"""
    return current_user


@router.post("/link-parent", response_model=UserResponse)
def link_parent(
    data: LinkParent,
    current_user: User = Depends(get_current_student),
    db: Session = Depends(get_db)
):
    """Student links their account to parent"""
    result = AuthService(db).link_parent(current_user, data.parent_email)
    return result


@router.put("/fcm-token")
def update_fcm_token(
    data: UpdateFCMToken,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Update Firebase push notification token"""
    AuthService(db).update_fcm_token(current_user, data.fcm_token)
    return {"message": "FCM token updated"}


@router.get("/children", response_model=List[ChildResponse])
def get_children(
    current_user: User = Depends(get_current_parent),
    db: Session = Depends(get_db)
):
    """Parent gets list of their linked children"""
    children = AuthService(db).get_children(current_user)
    return children