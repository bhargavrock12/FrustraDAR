package com.frustradar.session

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class GameInfo(
    val packageName: String,
    val gameName: String? = null
)

/**
 * Detects the foreground game using UsageStatsManager.
 * This is the A-D3 default implementation.
 */
@Singleton
class ForegroundGameDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /**
     * Polls UsageStatsManager for the current foreground app.
     * Note: Requires PACKAGE_USAGE_STATS special permission granted through Settings.
     */
    fun isGameInForeground(): GameInfo? {
        val now = System.currentTimeMillis()
        // Query events from the last 10 minutes to be safe and catch the last resume event
        val events = usageStatsManager.queryEvents(now - 1000 * 60 * 10, now)
        
        var currentForegroundPackage: String? = null
        val event = UsageEvents.Event()
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                currentForegroundPackage = event.packageName
            } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                if (currentForegroundPackage == event.packageName) {
                    currentForegroundPackage = null
                }
            }
        }
        
        if (currentForegroundPackage != null && isGame(currentForegroundPackage)) {
            return GameInfo(packageName = currentForegroundPackage)
        }
        return null
    }

    private fun isGame(packageName: String): Boolean {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            applicationInfo.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }
}
