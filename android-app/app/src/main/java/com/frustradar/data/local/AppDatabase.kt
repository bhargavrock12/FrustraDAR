package com.frustradar.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for FrustraDAR local storage.
 *
 * Currently holds:
 * - [ScoreQueueEntity]: Offline buffer for frustration scores awaiting upload.
 *
 * Version 1. Exported schemas stored in `app/schemas/` for migration testing.
 */
@Database(
    entities = [ScoreQueueEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scoreQueueDao(): ScoreQueueDao

    companion object {
        private const val DB_NAME = "frustradar.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Get or create the singleton database instance.
         * Thread-safe via double-checked locking.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                ).build().also { INSTANCE = it }
            }
        }
    }
}
