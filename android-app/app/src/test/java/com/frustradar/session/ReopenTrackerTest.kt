package com.frustradar.session

import org.junit.Assert.assertEquals
import org.junit.Test

class ReopenTrackerTest {
    @Test
    fun testReopenCounter() {
        val tracker = ReopenTracker()
        assertEquals(0, tracker.getReopenCount())
        
        tracker.trackReopen()
        tracker.trackReopen()
        assertEquals(2, tracker.getReopenCount())
        
        tracker.reset()
        assertEquals(0, tracker.getReopenCount())
    }
}
