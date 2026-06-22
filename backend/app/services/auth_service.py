from sqlalchemy.orm import Session
from fastapi import HTTPException, status
from datetime import timedelta
from app.models.user import User, UserRole
from app.schemas.user import UserCreate, UserLogin
from app.core.security import hash_password, verify_password, create_access_token
from app.core.config import settings


class AuthService:
    def __init__(self, db: Session):
        self.db = db

    # ============================================
    # REGISTER
    # ============================================
    def register(self, user_data: UserCreate) -> dict:
        """
        Register a new user (student or parent)
        Returns user object and JWT token
        """

        # Check if email already exists
        existing_user = self.db.query(User).filter(
            User.email == user_data.email
        ).first()

        if existing_user:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Email already registered"
            )

        # Hash password
        hashed_pw = hash_password(user_data.password)

        # Create user object
        new_user = User(
            email=user_data.email,
            username=user_data.username,
            hashed_password=hashed_pw,
            role=UserRole(user_data.role.value),
            parent_email=user_data.parent_email
        )

        # If student provided parent email, try to link parent
        if user_data.role.value == "student" and user_data.parent_email:
            parent = self.db.query(User).filter(
                User.email == user_data.parent_email,
                User.role == UserRole.PARENT
            ).first()

            if parent:
                new_user.parent_id = parent.id

        # Save to DB
        self.db.add(new_user)
        self.db.commit()
        self.db.refresh(new_user)

        # Create token
        token = create_access_token(data={
            "sub": str(new_user.id),
            "role": new_user.role.value,
            "email": new_user.email
        })

        return {
            "access_token": token,
            "token_type": "bearer",
            "user": new_user
        }

    # ============================================
    # LOGIN
    # ============================================
    def login(self, credentials: UserLogin) -> dict:
        """
        Login with email and password
        Returns JWT token and user info
        """

        # Find user by email
        user = self.db.query(User).filter(
            User.email == credentials.email
        ).first()

        # User not found
        if not user:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid email or password"
            )

        # Wrong password
        if not verify_password(credentials.password, user.hashed_password):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid email or password"
            )

        # Account deactivated
        if not user.is_active:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Account is deactivated"
            )

        # Create token
        token = create_access_token(data={
            "sub": str(user.id),
            "role": user.role.value,
            "email": user.email
        })

        return {
            "access_token": token,
            "token_type": "bearer",
            "user": user
        }

    # ============================================
    # LINK PARENT
    # ============================================
    def link_parent(self, student: User, parent_email: str) -> User:
        """
        Link a student to their parent account
        """

        # Find parent by email
        parent = self.db.query(User).filter(
            User.email == parent_email,
            User.role == UserRole.PARENT
        ).first()

        if not parent:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="No parent account found with that email"
            )

        # Link
        student.parent_id    = parent.id
        student.parent_email = parent_email

        self.db.commit()
        self.db.refresh(student)

        return student

    # ============================================
    # UPDATE FCM TOKEN
    # ============================================
    def update_fcm_token(self, user: User, fcm_token: str) -> User:
        """Update Firebase Cloud Messaging token for push notifications"""
        user.fcm_token = fcm_token
        self.db.commit()
        self.db.refresh(user)
        return user

    # ============================================
    # GET CHILDREN (for parent)
    # ============================================
    def get_children(self, parent: User) -> list:
        """Get all children linked to a parent account"""
        children = self.db.query(User).filter(
            User.parent_id == parent.id
        ).all()
        return children