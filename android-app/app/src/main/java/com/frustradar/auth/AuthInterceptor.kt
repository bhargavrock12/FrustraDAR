package com.frustradar.auth

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that attaches `Authorization: Bearer <token>` to outgoing requests.
 *
 * Skips auth header for unauthenticated endpoints (register, login).
 * On 401 response → clears stored token (triggers re-login flow; no refresh tokens exist).
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        // Skip auth header for public endpoints
        val request = if (isPublicEndpoint(path)) {
            originalRequest
        } else {
            val token = tokenManager.getToken()
            if (token != null) {
                originalRequest.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                originalRequest
            }
        }

        val response = chain.proceed(request)

        // Backend returns 401 for invalid/expired tokens — clear and require re-login.
        // No refresh tokens exist in the FrustraDAR backend.
        if (response.code == 401 && !isPublicEndpoint(path)) {
            tokenManager.clearToken()
        }

        return response
    }

    /**
     * Public endpoints that do not require an Authorization header.
     * Paths are relative to the API base URL.
     */
    private fun isPublicEndpoint(path: String): Boolean {
        return path.endsWith("/auth/register") || path.endsWith("/auth/login")
    }
}
