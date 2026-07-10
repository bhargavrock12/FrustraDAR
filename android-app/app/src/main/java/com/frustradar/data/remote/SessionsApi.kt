package com.frustradar.data.remote

import com.frustradar.data.remote.dto.SessionEndRequest
import com.frustradar.data.remote.dto.SessionResponse
import com.frustradar.data.remote.dto.SessionStartRequest
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface for `/sessions` endpoints.
 * Verified against `backend/app/api/routes/sessions.py`.
 */
interface SessionsApi {

    /** Start a gaming session. Returns 201 + SessionResponse. */
    @POST("sessions/start")
    suspend fun startSession(@Body request: SessionStartRequest): Response<SessionResponse>

    /** End a gaming session. */
    @PUT("sessions/{sessionId}/end")
    suspend fun endSession(
        @Path("sessionId") sessionId: String,
        @Body request: SessionEndRequest
    ): Response<SessionResponse>

    /** Get the current active session. Returns 404 if none active. */
    @GET("sessions/active")
    suspend fun getActiveSession(): Response<SessionResponse>

    /** Get session history. */
    @GET("sessions/history")
    suspend fun getSessionHistory(
        @Query("limit") limit: Int = 20,
        @Query("skip") skip: Int = 0
    ): Response<List<SessionResponse>>
}
