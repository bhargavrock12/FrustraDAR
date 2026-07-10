package com.frustradar.data.remote

import com.frustradar.data.remote.dto.AccountDeactivateResponse
import com.frustradar.data.remote.dto.ProfileUpdateResponse
import com.frustradar.data.remote.dto.UserResponse
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface for `/users` endpoints.
 * Verified against `backend/app/api/routes/users.py`.
 *
 * Note: PUT /profile uses a QUERY PARAMETER for username (not request body).
 * This is a backend contract quirk documented in the checklist.
 */
interface UsersApi {

    /** Get user profile. */
    @GET("users/profile")
    suspend fun getProfile(): Response<UserResponse>

    /**
     * Update username. Backend accepts `username` as a **query parameter** (not body).
     * Verified in `routes/users.py`: `def update_profile(username: str, ...)`.
     */
    @PUT("users/profile")
    suspend fun updateProfile(
        @Query("username") username: String
    ): Response<ProfileUpdateResponse>

    /**
     * Soft-deactivate account (sets `is_active = false`).
     * Not a permanent deletion — documented in 04_API_CONTRACT.md.
     */
    @DELETE("users/account")
    suspend fun deactivateAccount(): Response<AccountDeactivateResponse>
}
