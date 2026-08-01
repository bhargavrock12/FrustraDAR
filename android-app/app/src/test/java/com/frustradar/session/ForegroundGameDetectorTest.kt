package com.frustradar.session

import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ForegroundGameDetectorTest {

    @Test
    fun testForegroundDetectionEmpty() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val detector = ForegroundGameDetector(context)
        // Robolectric doesn't have usage events populated by default
        val result = detector.isGameInForeground()
        assertNull(result)
    }
}
