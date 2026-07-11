package com.frustradar.data.remote

import com.frustradar.data.remote.dto.AlertAcknowledgeResponse
import com.frustradar.data.remote.dto.AlertResponse
import com.frustradar.data.remote.dto.UnreadCountResponse
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface for `/alerts` endpoints.
 * Verified against `backend/app/api/routes/alerts.py`.
 *
 * Note: PUT /{id}/acknowledge sends NO request body (per backend source).
 */
interface AlertsApi {

    /** Get alerts — students get own, parents get children's. */
    @GET("alerts/")
    suspend fun getAlerts(
        @Query("limit") limit: Int = 50,
        @Query("skip") skip: Int = 0
    ): Response<List<AlertResponse>>

    /** Get count of unacknowledged alerts. */
    @GET("alerts/unread-count")
    suspend fun getUnreadCount(): Response<UnreadCountResponse>

    /** Mark alert as acknowledged. No request body. */
    @PUT("alerts/{alertId}/acknowledge")
    suspend fun acknowledgeAlert(
        @Path("alertId") alertId: String
    ): Response<AlertAcknowledgeResponse>
}
