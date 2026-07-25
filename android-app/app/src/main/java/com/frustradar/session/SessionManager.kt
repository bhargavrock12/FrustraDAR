package com.frustradar.session

import com.frustradar.data.remote.SessionsApi
import com.frustradar.data.remote.dto.SessionEndRequest
import com.frustradar.data.remote.dto.SessionResponse
import com.frustradar.data.remote.dto.SessionStartRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.frustradar.work.ScoreUploadWorker

/**
 * Manages session start/end lifecycle using the existing SessionsApi contracts.
 */
@Singleton
class SessionManager @Inject constructor(
    private val sessionsApi: SessionsApi,
    private val reopenTracker: ReopenTracker,
    private val workManager: WorkManager,
    private val coroutineScope: CoroutineScope
) {
    private val _activeSession = MutableStateFlow<SessionResponse?>(null)
    val activeSession: StateFlow<SessionResponse?> = _activeSession.asStateFlow()

    private var sessionJob: Job? = null

    private val formatter = DateTimeFormatter.ISO_INSTANT

    suspend fun startSession(gamePackage: String?, gameName: String? = null): Result<SessionResponse> {
        val startTime = formatter.format(Instant.now().atOffset(ZoneOffset.UTC))
        val request = SessionStartRequest(
            gamePackage = gamePackage,
            gameName = gameName,
            startTime = startTime
        )
        
        return try {
            val response = sessionsApi.startSession(request)
            if (response.isSuccessful && response.body() != null) {
                reopenTracker.reset()
                _activeSession.value = response.body()
                startSessionPipelineLoop()
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to start session: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun endSession(): Result<SessionResponse> {
        val currentSession = _activeSession.value ?: return Result.failure(Exception("No active session to end"))
        
        val endTime = formatter.format(Instant.now().atOffset(ZoneOffset.UTC))
        val request = SessionEndRequest(
            endTime = endTime,
            reopenCount = reopenTracker.getReopenCount()
        )
        
        return try {
            val response = sessionsApi.endSession(currentSession.id, request)
            if (response.isSuccessful && response.body() != null) {
                stopSessionPipelineLoop()
                _activeSession.value = null
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to end session: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchActiveSession(): Result<SessionResponse?> {
        return try {
            val response = sessionsApi.getActiveSession()
            if (response.isSuccessful) {
                _activeSession.value = response.body()
                Result.success(response.body())
            } else if (response.code() == 404) {
                _activeSession.value = null
                Result.success(null)
            } else {
                Result.failure(Exception("Failed to fetch active session: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun startSessionPipelineLoop() {
        sessionJob?.cancel()
        sessionJob = coroutineScope.launch {
            var scoreCount = 0
            while (isActive) {
                delay(30_000L) // 30-second fused-score/window cadence
                
                // TODO (Phase 4/5): Generate score via ML runtime and buffer to ScoreQueueDao
                scoreCount++

                if (scoreCount >= 4) { // 4-score / 120-second upload trigger
                    val uploadWorkRequest = OneTimeWorkRequestBuilder<ScoreUploadWorker>().build()
                    workManager.enqueueUniqueWork(
                        "ScoreUploadBatch",
                        ExistingWorkPolicy.REPLACE,
                        uploadWorkRequest
                    )
                    scoreCount = 0
                }
            }
        }
    }

    private fun stopSessionPipelineLoop() {
        sessionJob?.cancel()
        sessionJob = null
    }
}
