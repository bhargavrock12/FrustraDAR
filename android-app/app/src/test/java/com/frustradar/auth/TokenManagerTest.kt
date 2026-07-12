package com.frustradar.auth

import android.content.Context
import com.frustradar.data.remote.dto.TokenResponse
import com.frustradar.data.remote.dto.UserResponse
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for [TokenManager] and [TokenStore].
 *
 * Uses [TokenStore.createForTest] with plain SharedPreferences
 * to avoid Android KeyStore / EncryptedSharedPreferences failures under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TokenManagerTest {

    private lateinit var tokenStore: TokenStore
    private lateinit var tokenManager: TokenManager

    private val testUser = UserResponse(
        id = "550e8400-e29b-41d4-a716-446655440000",
        email = "test@test.com",
        username = "testuser",
        role = "student",
        parentEmail = "parent@test.com",
        isActive = true,
        createdAt = "2026-08-24T12:00:00"
    )

    private val testTokenResponse = TokenResponse(
        accessToken = "jwt-token-123",
        tokenType = "bearer",
        user = testUser
    )

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_auth", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        tokenStore = TokenStore.createForTest(prefs)
        tokenManager = TokenManager(tokenStore)
    }

    @Test
    fun `initially not logged in`() {
        assertFalse(tokenManager.isLoggedIn())
        assertNull(tokenManager.getToken())
        assertNull(tokenManager.getUser())
    }

    @Test
    fun `saveToken stores and retrieves token`() {
        tokenManager.saveToken(testTokenResponse)

        assertTrue(tokenManager.isLoggedIn())
        assertEquals("jwt-token-123", tokenManager.getToken())
    }

    @Test
    fun `saveToken stores and retrieves user profile`() {
        tokenManager.saveToken(testTokenResponse)

        val user = tokenManager.getUser()
        assertNotNull(user)
        assertEquals("test@test.com", user!!.email)
        assertEquals("testuser", user.username)
        assertEquals("student", user.role)
        assertEquals("parent@test.com", user.parentEmail)
    }

    @Test
    fun `clearToken removes all auth state`() {
        tokenManager.saveToken(testTokenResponse)
        assertTrue(tokenManager.isLoggedIn())

        tokenManager.clearToken()

        assertFalse(tokenManager.isLoggedIn())
        assertNull(tokenManager.getToken())
        assertNull(tokenManager.getUser())
    }

    @Test
    fun `overwriting token replaces previous`() {
        tokenManager.saveToken(testTokenResponse)

        val newResponse = testTokenResponse.copy(
            accessToken = "new-jwt-token",
            user = testUser.copy(username = "newuser")
        )
        tokenManager.saveToken(newResponse)

        assertEquals("new-jwt-token", tokenManager.getToken())
        assertEquals("newuser", tokenManager.getUser()?.username)
    }

    @Test
    fun `TokenStore hasToken returns correct state`() {
        assertFalse(tokenStore.hasToken())

        tokenStore.saveToken("test-token", "bearer", testUser)
        assertTrue(tokenStore.hasToken())

        tokenStore.clear()
        assertFalse(tokenStore.hasToken())
    }

    @Test
    fun `TokenStore getTokenType returns bearer`() {
        assertEquals("bearer", tokenStore.getTokenType())

        tokenStore.saveToken("test-token", "bearer", testUser)
        assertEquals("bearer", tokenStore.getTokenType())
    }
}
