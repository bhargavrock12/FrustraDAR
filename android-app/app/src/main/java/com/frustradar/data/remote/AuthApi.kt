package com.frustradar.data.remote

import com.frustradar.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface for `/auth` endpoints.
 * Verified against `backend/app/api/routes/auth.py`.
 *
 * Paths are relative — Retrofit base URL already includes `/api/v1`.
 */
interface AuthApi {

    /** Register a new student or parent account. Returns 201 + JWT. */
    @POST("auth/register")
    suspend fun register(@Body request: UserCreateRequest): Response<TokenResponse>

    /** Login with email/password. Returns 200 + JWT. */
    @POST("auth/login")
    suspend fun login(@Body request: UserLoginRequest): Response<TokenResponse>

    /** Get currently authenticated user profile. */
    @GET("auth/me")
    suspend fun getMe(): Response<UserResponse>

    /** Student links account to parent by parent email. */
    @POST("auth/link-parent")
    suspend fun linkParent(@Body request: LinkParentRequest): Response<UserResponse>

    /** Update Firebase push notification token. */
    @PUT("auth/fcm-token")
    suspend fun updateFcmToken(@Body request: UpdateFcmTokenRequest): Response<FcmTokenUpdateResponse>

    /** Parent gets list of linked children. */
    @GET("auth/children")
    suspend fun getChildren(): Response<List<ChildResponse>>
}
