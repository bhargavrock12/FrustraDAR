package com.frustradar.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Provides the Retrofit instance and OkHttpClient.
 * Constructed via Hilt DI (see [com.frustradar.di.NetworkModule]).
 *
 * The base URL includes `/api/v1` and must end with `/` for Retrofit.
 * API interface paths are relative (e.g., `auth/register`).
 */
class ApiClient(
    val retrofit: Retrofit,
    val okHttpClient: OkHttpClient
) {
    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val sessionsApi: SessionsApi = retrofit.create(SessionsApi::class.java)
    val scoresApi: ScoresApi = retrofit.create(ScoresApi::class.java)
    val alertsApi: AlertsApi = retrofit.create(AlertsApi::class.java)
    val usersApi: UsersApi = retrofit.create(UsersApi::class.java)

    companion object {
        /**
         * Build a configured Retrofit instance.
         * Called from Hilt DI module.
         */
        fun create(baseUrl: String, okHttpClient: OkHttpClient): ApiClient {
            val retrofit = Retrofit.Builder()
                .baseUrl(ensureTrailingSlash(baseUrl))
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return ApiClient(retrofit, okHttpClient)
        }

        private fun ensureTrailingSlash(url: String): String =
            if (url.endsWith("/")) url else "$url/"
    }
}
