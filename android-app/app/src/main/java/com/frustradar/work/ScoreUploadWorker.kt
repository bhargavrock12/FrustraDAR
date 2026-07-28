package com.frustradar.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frustradar.data.local.ScoreQueueDao
import com.frustradar.data.remote.ScoresApi
import com.frustradar.data.remote.dto.ScoreBatchCreateRequest
import com.frustradar.data.remote.dto.ScoreCreateRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker to batch upload locally buffered scores to the backend.
 * Uses exponential backoff provided by WorkManager on failure.
 */
@HiltWorker
class ScoreUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val scoreQueueDao: ScoreQueueDao,
    private val scoresApi: ScoresApi
) : CoroutineWorker(appContext, workerParams) {

    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type

    override suspend fun doWork(): Result {
        return try {
            val pendingScores = scoreQueueDao.getUnuploaded(limit = 100)
            if (pendingScores.isEmpty()) {
                return Result.success()
            }

            val groupedScores = pendingScores.groupBy { it.sessionId }
            var allSuccessful = true

            for ((sessionId, scores) in groupedScores) {
                val scoreRequests = scores.map { entity ->
                    ScoreCreateRequest(
                        timestamp = entity.timestamp,
                        facialScore = entity.facialScore,
                        audioScore = entity.audioScore,
                        motionScore = entity.motionScore,
                        behaviorScore = entity.behaviorScore,
                        fusionScore = entity.fusionScore,
                        signalsUsed = gson.fromJson(entity.signalsUsed, listType),
                        windowDurationSec = entity.windowDurationSec
                    )
                }

                val batchRequest = ScoreBatchCreateRequest(
                    sessionId = sessionId,
                    scores = scoreRequests
                )

                val response = scoresApi.uploadBatch(batchRequest)
                if (response.isSuccessful && response.code() == 201) {
                    val idsToMark = scores.map { it.id }
                    scoreQueueDao.markUploaded(idsToMark)
                } else {
                    allSuccessful = false
                }
            }

            if (allSuccessful) {
                Result.success()
            } else {
                // Failed at least one batch (e.g. network issue), retry with backoff.
                // Duplicate-on-retry is accepted for this API.
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
