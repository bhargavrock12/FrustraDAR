from pydantic import BaseModel, EmailStr, field_validator
from typing import Optional
from datetime import datetime
from uuid import UUID
from enum import Enum


class UserRoleEnum(str, Enum):
    student = "student"
    parent  = "parent"


# ---- REQUEST SCHEMAS ----

class UserCreate(BaseModel):
    """Schema for registering a new user"""
    email:        str
    username:     str
    password:     str
    role:         UserRoleEnum
    parent_email: Optional[str] = None  # students fill this

    @field_validator('password')
    @classmethod
    def password_min_length(cls, v):
        if len(v) < 6:
            raise ValueError('Password must be at least 6 characters')
        return v

    @field_validator('username')
    @classmethod
    def username_min_length(cls, v):
        if len(v) < 2:
            raise ValueError('Username must be at least 2 characters')
        return v


class UserLogin(BaseModel):
    """Schema for logging in"""
    email:    str
    password: str


class LinkParent(BaseModel):
    """Schema for linking student to parent"""
    parent_email: str


class UpdateFCMToken(BaseModel):
    """Schema for updating Firebase push notification token"""
    fcm_token: str


# ---- RESPONSE SCHEMAS ----

class UserResponse(BaseModel):
    """What we return after register/login"""
    id:           UUID
    email:        str
    username:     str
    role:         UserRoleEnum
    parent_email: Optional[str] = None
    is_active:    bool
    created_at:   datetime

    class Config:
        from_attributes = True


class TokenResponse(BaseModel):
    """JWT token response"""
    access_token: str
    token_type:   str = "bearer"
    user:         UserResponse


class ChildResponse(BaseModel):
    """Basic child info for parent's view"""
    id:       UUID
    username: str
    email:    str

    class Config:
        from_attributes = True