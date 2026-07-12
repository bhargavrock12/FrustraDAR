package com.frustradar.auth

import com.frustradar.data.remote.dto.TokenResponse
import com.frustradar.data.remote.dto.UserResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages authentication state.
 *
 * **No refresh tokens exist** in the FrustraDAR backend (checklist §2).
 * On 401 → clear token → navigate to login screen.
 */
@Singleton
class TokenManager @Inject constructor(
    private val tokenStore: TokenStore
) {
    /**
     * Save auth state from a login/register response.
     */
    fun saveToken(response: TokenResponse) {
        tokenStore.saveToken(
            accessToken = response.accessToken,
            tokenType = response.tokenType,
            user = response.user
        )
    }

    /**
     * Get the current JWT access token, or null if not authenticated.
     */
    fun getToken(): String? = tokenStore.getAccessToken()

    /**
     * Get the cached user profile.
     */
    fun getUser(): UserResponse? = tokenStore.getUser()

    /**
     * Check if user is currently logged in (has a stored token).
     * Note: This does NOT verify token expiry — the backend will return 401 if expired.
     */
    fun isLoggedIn(): Boolean = tokenStore.hasToken()

    /**
     * Clear all auth state (logout).
     * Called when: user explicitly logs out, or backend returns 401 (token expired/invalid).
     */
    fun clearToken() {
        tokenStore.clear()
    }
}
