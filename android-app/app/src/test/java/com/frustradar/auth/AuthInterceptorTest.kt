package com.frustradar.auth

import android.content.Context
import com.frustradar.data.remote.dto.TokenResponse
import com.frustradar.data.remote.dto.UserResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for [AuthInterceptor].
 * Verifies Bearer header injection and 401 token clearing.
 *
 * Uses [TokenStore.createForTest] with plain SharedPreferences
 * to avoid Android KeyStore failures under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AuthInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var tokenStore: TokenStore
    private lateinit var tokenManager: TokenManager
    private lateinit var interceptor: AuthInterceptor
    private lateinit var client: OkHttpClient

    private val testUser = UserResponse(
        id = "test-uuid",
        email = "test@test.com",
        username = "testuser",
        role = "student",
        parentEmail = null,
        isActive = true,
        createdAt = "2026-08-24T12:00:00"
    )

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()

        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_auth_interceptor", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        tokenStore = TokenStore.createForTest(prefs)
        tokenManager = TokenManager(tokenStore)
        interceptor = AuthInterceptor(tokenManager)
        client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `adds Bearer header for authenticated requests`() {
        tokenManager.saveToken(
            TokenResponse("my-jwt-token", "bearer", testUser)
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val request = Request.Builder()
            .url(server.url("/api/v1/sessions/active"))
            .build()

        client.newCall(request).execute()

        val recorded = server.takeRequest()
        assertEquals("Bearer my-jwt-token", recorded.getHeader("Authorization"))
    }

    @Test
    fun `skips auth header for register endpoint`() {
        tokenManager.saveToken(
            TokenResponse("my-jwt-token", "bearer", testUser)
        )
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        val request = Request.Builder()
            .url(server.url("/api/v1/auth/register"))
            .post(okhttp3.RequestBody.create(null, "{}"))
            .build()

        client.newCall(request).execute()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `skips auth header for login endpoint`() {
        tokenManager.saveToken(
            TokenResponse("my-jwt-token", "bearer", testUser)
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val request = Request.Builder()
            .url(server.url("/api/v1/auth/login"))
            .post(okhttp3.RequestBody.create(null, "{}"))
            .build()

        client.newCall(request).execute()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `no auth header when no token stored`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val request = Request.Builder()
            .url(server.url("/api/v1/sessions/active"))
            .build()

        client.newCall(request).execute()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `clears token on 401 for authenticated endpoint`() {
        tokenManager.saveToken(
            TokenResponse("expired-token", "bearer", testUser)
        )
        assertTrue(tokenManager.isLoggedIn())

        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Could not validate credentials"}"""))

        val request = Request.Builder()
            .url(server.url("/api/v1/sessions/active"))
            .build()

        val response = client.newCall(request).execute()

        assertEquals(401, response.code)
        assertFalse(tokenManager.isLoggedIn())
        assertNull(tokenManager.getToken())
    }

    @Test
    fun `does not clear token on 401 for login endpoint`() {
        tokenManager.saveToken(
            TokenResponse("valid-token", "bearer", testUser)
        )

        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Invalid email or password"}"""))

        val request = Request.Builder()
            .url(server.url("/api/v1/auth/login"))
            .post(okhttp3.RequestBody.create(null, "{}"))
            .build()

        client.newCall(request).execute()

        // Token should still be present — 401 on login is expected for bad credentials
        assertTrue(tokenManager.isLoggedIn())
    }
}
