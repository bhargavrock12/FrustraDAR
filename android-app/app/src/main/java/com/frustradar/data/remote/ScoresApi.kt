package com.frustradar.data.remote

import com.frustradar.data.remote.dto.ScoreBatchCreateRequest
import com.frustradar.data.remote.dto.ScoreBatchResponse
import com.frustradar.data.remote.dto.ScoreResponse
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface for `/scores` endpoints.
 * Verified against `backend/app/api/routes/scores.py`.
 */
interface ScoresApi {

    /** Upload a batch of frustration scores. Returns 201. */
    @POST("scores/batch")
    suspend fun uploadBatch(@Body request: ScoreBatchCreateRequest): Response<ScoreBatchResponse>

    /** Get latest scores for the authenticated user. */
    @GET("scores/latest")
    suspend fun getLatestScores(
        @Query("limit") limit: Int = 20
    ): Response<List<ScoreResponse>>

    /** Get score trend data over a day range. */
    @GET("scores/trends")
    suspend fun getTrends(
        @Query("days") days: Int = 7
    ): Response<Map<String, Any>>

    /** Get all scores for a specific session. */
    @GET("scores/session/{sessionId}")
    suspend fun getSessionScores(
        @Path("sessionId") sessionId: String
    ): Response<List<ScoreResponse>>
}
