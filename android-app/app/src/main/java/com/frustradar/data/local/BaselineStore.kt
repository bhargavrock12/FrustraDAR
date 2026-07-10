package com.frustradar.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted local storage for motion baseline calibration data.
 *
 * Baseline data is computed on-device from 20 normal motion windows (Phase 5)
 * and is **never uploaded** to the backend (privacy requirement).
 *
 * Stored as encrypted JSON string for the 26 engineered feature baselines.
 */
@Singleton
class BaselineStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        PREFS_NAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** Save baseline JSON (serialized feature baselines). */
    fun saveBaseline(baselineJson: String) {
        prefs.edit()
            .putString(KEY_BASELINE, baselineJson)
            .putLong(KEY_CALIBRATED_AT, System.currentTimeMillis())
            .apply()
    }

    /** Get stored baseline JSON, or null if not calibrated. */
    fun getBaseline(): String? = prefs.getString(KEY_BASELINE, null)

    /** Check if a baseline has been calibrated. */
    fun isCalibrated(): Boolean = prefs.contains(KEY_BASELINE)

    /** Get calibration timestamp (epoch millis), or -1 if not calibrated. */
    fun getCalibratedAt(): Long = prefs.getLong(KEY_CALIBRATED_AT, -1L)

    /** Clear baseline data (for recalibration). */
    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "frustradar_baseline"
        private const val KEY_BASELINE = "motion_baseline"
        private const val KEY_CALIBRATED_AT = "calibrated_at"
    }
}
