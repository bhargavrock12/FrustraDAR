package com.frustradar.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTOs for `/api/v1/auth` endpoints.
 * Field names and types verified against backend `schemas/user.py`.
 */

// ── Request DTOs ──────────────────────────────────────────────────────────

/** POST /auth/register — body */
data class UserCreateRequest(
    val email: String,
    val username: String,
    val password: String,
    val role: String, // "student" | "parent"
    @SerializedName("parent_email")
    val parentEmail: String? = null
)

/** POST /auth/login — body */
data class UserLoginRequest(
    val email: String,
    val password: String
)

/** POST /auth/link-parent — body */
data class LinkParentRequest(
    @SerializedName("parent_email")
    val parentEmail: String
)

/** PUT /auth/fcm-token — body */
data class UpdateFcmTokenRequest(
    @SerializedName("fcm_token")
    val fcmToken: String
)

// ── Response DTOs ──────────────────────────────────────────────────────────

/**
 * User profile in responses. Matches backend `UserResponse` schema.
 * `id` is String (UUID serialized by FastAPI as string).
 */
data class UserResponse(
    val id: String,
    val email: String,
    val username: String,
    val role: String,
    @SerializedName("parent_email")
    val parentEmail: String?,
    @SerializedName("is_active")
    val isActive: Boolean,
    @SerializedName("created_at")
    val createdAt: String
)

/**
 * JWT token response. Matches backend `TokenResponse` schema.
 * register → 201, login → 200.
 */
data class TokenResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String,
    val user: UserResponse
)

/** GET /auth/children — each child */
data class ChildResponse(
    val id: String,
    val username: String,
    val email: String
)

/** PUT /auth/fcm-token — response */
data class FcmTokenUpdateResponse(
    val message: String
)
