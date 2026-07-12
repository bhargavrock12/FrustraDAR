package com.frustradar.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.frustradar.data.remote.dto.UserResponse
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage for JWT access token and cached user profile.
 * Uses [EncryptedSharedPreferences] in production — data is AES-encrypted at rest.
 *
 * Stores:
 * - `access_token` — JWT bearer token
 * - `token_type` — always "bearer"
 * - `user_json` — serialized [UserResponse] for offline display
 *
 * For unit testing, use the [createForTest] factory to supply a plain SharedPreferences
 * (avoids Android KeyStore dependency under Robolectric).
 */
@Singleton
class TokenStore private constructor(
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    /**
     * Production constructor — used by Hilt DI.
     * Creates EncryptedSharedPreferences backed by Android KeyStore.
     */
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ),
        Gson()
    )

    /** Save the JWT token and user profile from a login/register response. */
    fun saveToken(accessToken: String, tokenType: String, user: UserResponse) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_TOKEN_TYPE, tokenType)
            .putString(KEY_USER_JSON, gson.toJson(user))
            .apply()
    }

    /** Get the stored access token, or null if not logged in. */
    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    /** Get the stored token type (default "bearer"). */
    fun getTokenType(): String = prefs.getString(KEY_TOKEN_TYPE, "bearer") ?: "bearer"

    /** Get the cached user profile, or null. */
    fun getUser(): UserResponse? {
        val json = prefs.getString(KEY_USER_JSON, null) ?: return null
        return try {
            gson.fromJson(json, UserResponse::class.java)
        } catch (_: Exception) {
            null
        }
    }

    /** Clear all stored auth data (logout). */
    fun clear() {
        prefs.edit().clear().apply()
    }

    /** Check if a token is stored. */
    fun hasToken(): Boolean = getAccessToken() != null

    companion object {
        private const val PREFS_NAME = "frustradar_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_TOKEN_TYPE = "token_type"
        private const val KEY_USER_JSON = "user_json"

        /**
         * Create a test-friendly TokenStore backed by a plain [SharedPreferences].
         * Avoids Android KeyStore which is unavailable under Robolectric/JVM tests.
         */
        fun createForTest(prefs: SharedPreferences): TokenStore {
            return TokenStore(prefs, Gson())
        }
    }
}
