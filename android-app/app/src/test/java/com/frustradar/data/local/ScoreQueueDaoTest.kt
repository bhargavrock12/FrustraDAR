package com.frustradar.data.local

import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for [ScoreQueueDao] using Room in-memory database.
 * Verifies insert, query, mark-uploaded, and delete operations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ScoreQueueDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ScoreQueueDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        dao = database.scoreQueueDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createTestEntity(
        sessionId: String = "session-uuid",
        fusionScore: Float = 50.0f,
        uploaded: Boolean = false
    ) = ScoreQueueEntity(
        sessionId = sessionId,
        timestamp = "2026-08-24T12:00:00Z",
        facialScore = 45.0f,
        audioScore = 30.0f,
        motionScore = null,
        behaviorScore = null,
        fusionScore = fusionScore,
        signalsUsed = """["facial","voice"]""",
        windowDurationSec = 30,
        uploaded = uploaded
    )

    @Test
    fun `insert and retrieve single score`() = runTest {
        val entity = createTestEntity()
        val id = dao.insert(entity)

        assertTrue(id > 0)

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("session-uuid", all[0].sessionId)
        assertEquals(50.0f, all[0].fusionScore, 0.01f)
        assertEquals(45.0f, all[0].facialScore!!, 0.01f)
        assertEquals(30.0f, all[0].audioScore!!, 0.01f)
        assertNull(all[0].motionScore)
        assertNull(all[0].behaviorScore)
        assertEquals(30, all[0].windowDurationSec)
        assertFalse(all[0].uploaded)
    }

    @Test
    fun `insertAll inserts multiple scores`() = runTest {
        val entities = listOf(
            createTestEntity(fusionScore = 40.0f),
            createTestEntity(fusionScore = 60.0f),
            createTestEntity(fusionScore = 80.0f)
        )

        val ids = dao.insertAll(entities)
        assertEquals(3, ids.size)

        val all = dao.getAll()
        assertEquals(3, all.size)
    }

    @Test
    fun `getUnuploaded returns only non-uploaded scores`() = runTest {
        dao.insert(createTestEntity(fusionScore = 40.0f, uploaded = false))
        dao.insert(createTestEntity(fusionScore = 60.0f, uploaded = true))
        dao.insert(createTestEntity(fusionScore = 80.0f, uploaded = false))

        val unuploaded = dao.getUnuploaded()
        assertEquals(2, unuploaded.size)
        assertTrue(unuploaded.all { !it.uploaded })
    }

    @Test
    fun `getUnuploaded respects limit`() = runTest {
        repeat(10) {
            dao.insert(createTestEntity(fusionScore = it.toFloat()))
        }

        val limited = dao.getUnuploaded(limit = 3)
        assertEquals(3, limited.size)
    }

    @Test
    fun `getUnuploaded returns oldest first`() = runTest {
        dao.insert(createTestEntity(fusionScore = 10.0f))
        dao.insert(createTestEntity(fusionScore = 20.0f))
        dao.insert(createTestEntity(fusionScore = 30.0f))

        val unuploaded = dao.getUnuploaded()

        // IDs are auto-incremented, so lowest ID = oldest
        assertTrue(unuploaded[0].id < unuploaded[1].id)
        assertTrue(unuploaded[1].id < unuploaded[2].id)
    }

    @Test
    fun `countUnuploaded returns correct count`() = runTest {
        dao.insert(createTestEntity(uploaded = false))
        dao.insert(createTestEntity(uploaded = true))
        dao.insert(createTestEntity(uploaded = false))
        dao.insert(createTestEntity(uploaded = false))

        assertEquals(3, dao.countUnuploaded())
    }

    @Test
    fun `markUploaded updates correct rows`() = runTest {
        val id1 = dao.insert(createTestEntity(fusionScore = 40.0f))
        val id2 = dao.insert(createTestEntity(fusionScore = 60.0f))
        dao.insert(createTestEntity(fusionScore = 80.0f))

        dao.markUploaded(listOf(id1, id2))

        val unuploaded = dao.getUnuploaded()
        assertEquals(1, unuploaded.size)
        assertEquals(80.0f, unuploaded[0].fusionScore, 0.01f)

        assertEquals(1, dao.countUnuploaded())
    }

    @Test
    fun `deleteUploaded removes only uploaded scores`() = runTest {
        dao.insert(createTestEntity(fusionScore = 40.0f, uploaded = false))
        dao.insert(createTestEntity(fusionScore = 60.0f, uploaded = true))
        dao.insert(createTestEntity(fusionScore = 80.0f, uploaded = true))

        val deleted = dao.deleteUploaded()
        assertEquals(2, deleted)

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals(40.0f, all[0].fusionScore, 0.01f)
    }

    @Test
    fun `empty table operations work correctly`() = runTest {
        val unuploaded = dao.getUnuploaded()
        assertTrue(unuploaded.isEmpty())

        assertEquals(0, dao.countUnuploaded())
        assertEquals(0, dao.deleteUploaded())
    }

    @Test
    fun `signals_used stored as JSON string`() = runTest {
        val entity = createTestEntity().copy(
            signalsUsed = """["facial","voice","motion"]"""
        )
        dao.insert(entity)

        val stored = dao.getAll()[0]
        assertEquals("""["facial","voice","motion"]""", stored.signalsUsed)
    }
}
