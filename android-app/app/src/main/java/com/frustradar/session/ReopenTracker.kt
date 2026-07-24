package com.frustradar.session

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks app reopen events within a gaming session.
 */
@Singleton
class ReopenTracker @Inject constructor() {

    private var reopenCount = 0

    fun getReopenCount(): Int = reopenCount

    fun trackReopen() {
        reopenCount++
    }

    fun reset() {
        reopenCount = 0
    }
}
