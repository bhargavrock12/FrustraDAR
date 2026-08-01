package com.frustradar.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.frustradar.data.local.ScoreQueueDao
import com.frustradar.data.local.ScoreQueueEntity
import com.frustradar.data.remote.ScoresApi
import com.frustradar.data.remote.dto.ScoreBatchCreateRequest
import com.frustradar.data.remote.dto.ScoreBatchResponse
import com.frustradar.data.remote.dto.ScoreResponse
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
class ScoreUploadWorkerTest {

    private lateinit var context: Context
    private lateinit var fakeDao: FakeScoreQueueDao
    private lateinit var fakeApi: FakeScoresApi

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        fakeDao = FakeScoreQueueDao()
        fakeApi = FakeScoresApi()
    }

    @Test
    fun testUploadSuccessMarksAsUploaded() = runTest {
        fakeDao.items.add(ScoreQueueEntity(id = 1L, sessionId = "s1", timestamp = "time", fusionScore = 50f, windowDurationSec = 30))
        
        val worker = TestListenableWorkerBuilder<ScoreUploadWorker>(context)
            .setWorkerFactory(TestWorkerFactory(fakeDao, fakeApi))
            .build()
            
        val result = worker.doWork()
        assertTrue(result is Result.Success)
        
        // Assert Dao was called to mark uploaded
        assertEquals(listOf(1L), fakeDao.markedIds)
        
        // Assert API payload mapped 30 for windowDurationSec
        assertEquals(30, fakeApi.lastRequest?.scores?.first()?.windowDurationSec)
    }
    
    @Test
    fun testUploadFailureReturnsRetry() = runTest {
        fakeDao.items.add(ScoreQueueEntity(id = 1L, sessionId = "s1", timestamp = "time", fusionScore = 50f, windowDurationSec = 30))
        fakeApi.shouldFail = true
        
        val worker = TestListenableWorkerBuilder<ScoreUploadWorker>(context)
            .setWorkerFactory(TestWorkerFactory(fakeDao, fakeApi))
            .build()
            
        val result = worker.doWork()
        assertTrue(result is Result.Retry)
        assertTrue(fakeDao.markedIds.isEmpty())
    }
}

class TestWorkerFactory(
    private val dao: ScoreQueueDao,
    private val api: ScoresApi
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker {
        return ScoreUploadWorker(appContext, workerParameters, dao, api)
    }
}

class FakeScoreQueueDao : ScoreQueueDao {
    val items = mutableListOf<ScoreQueueEntity>()
    val markedIds = mutableListOf<Long>()
    
    override suspend fun insert(score: ScoreQueueEntity): Long = 0L
    override suspend fun insertAll(scores: List<ScoreQueueEntity>): List<Long> = emptyList()
    override suspend fun getUnuploaded(limit: Int): List<ScoreQueueEntity> = items.filter { !it.uploaded }.take(limit)
    override suspend fun countUnuploaded(): Int = items.count { !it.uploaded }
    override suspend fun markUploaded(ids: List<Long>) { markedIds.addAll(ids) }
    override suspend fun deleteUploaded(): Int = 0
    override suspend fun getAll(): List<ScoreQueueEntity> = items
}

class FakeScoresApi : ScoresApi {
    var shouldFail = false
    var lastRequest: ScoreBatchCreateRequest? = null
    
    override suspend fun uploadBatch(request: ScoreBatchCreateRequest): Response<ScoreBatchResponse> {
        lastRequest = request
        return if (shouldFail) {
            Response.error(500, "".toResponseBody(null))
        } else {
            Response.success(201, ScoreBatchResponse("ok", request.scores.size, 100f))
        }
    }
    
    override suspend fun getLatestScores(limit: Int): Response<List<ScoreResponse>> = Response.success(emptyList())
    override suspend fun getTrends(days: Int): Response<Map<String, Any>> = Response.success(emptyMap())
    override suspend fun getSessionScores(sessionId: String): Response<List<ScoreResponse>> = Response.success(emptyList())
}
