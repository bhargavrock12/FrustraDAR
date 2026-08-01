package com.frustradar.session

import com.frustradar.data.remote.SessionsApi
import com.frustradar.data.remote.dto.SessionEndRequest
import com.frustradar.data.remote.dto.SessionResponse
import com.frustradar.data.remote.dto.SessionStartRequest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionManagerTest {

    private lateinit var fakeApi: FakeSessionsApi
    private lateinit var reopenTracker: ReopenTracker
    private lateinit var workManager: WorkManager
    private lateinit var manager: SessionManager
    private val testScope = TestScope()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)

        fakeApi = FakeSessionsApi()
        reopenTracker = ReopenTracker()
        manager = SessionManager(fakeApi, reopenTracker, workManager, testScope)
    }

    @Test
    fun startSession_success() = runTest {
        val result = manager.startSession("com.test", "Test Game")
        assertTrue(result.isSuccess)
        assertNotNull(manager.activeSession.value)
        assertEquals("com.test", fakeApi.startReq?.gamePackage)
        assertNotNull(fakeApi.startReq?.startTime)
    }
    
    @Test
    fun endSession_success() = runTest {
        manager.startSession("com.test")
        reopenTracker.trackReopen()
        
        val result = manager.endSession()
        assertTrue(result.isSuccess)
        assertNull(manager.activeSession.value)
        assertEquals(1, fakeApi.endReq?.reopenCount)
        assertNotNull(fakeApi.endReq?.endTime)
    }
    
    class FakeSessionsApi : SessionsApi {
        var startReq: SessionStartRequest? = null
        var endReq: SessionEndRequest? = null
        var activeSessionResp: Response<SessionResponse>? = null
        
        override suspend fun startSession(request: SessionStartRequest): Response<SessionResponse> {
            startReq = request
            val resp = SessionResponse("id1", request.gamePackage, request.gameName, request.startTime, null, null, false, 0, true)
            return Response.success(201, resp)
        }
        
        override suspend fun endSession(sessionId: String, request: SessionEndRequest): Response<SessionResponse> {
            endReq = request
            val resp = SessionResponse(sessionId, "pkg", "name", "start", request.endTime, 10, false, request.reopenCount ?: 0, false)
            return Response.success(resp)
        }
        
        override suspend fun getActiveSession(): Response<SessionResponse> {
            return activeSessionResp ?: Response.error(404, "".toResponseBody(null))
        }
        
        override suspend fun getSessionHistory(limit: Int, skip: Int): Response<List<SessionResponse>> {
            return Response.success(emptyList())
        }
    }
}
