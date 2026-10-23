package com.promptly.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.promptly.app.data.model.ReminderEntity

/**
 * The one and only Room database for Promptly.
 *
 * Bump [version] and add a migration in [MIGRATIONS] whenever the schema changes.
 * For dev convenience we also fall back to destructive migration when a missing
 * migration is encountered — DO NOT ship that to production.
 */
@Database(
    entities = [ReminderEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class PromptlyDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao

    companion object {
        const val DB_NAME = "promptly.db"

        fun build(context: Context): PromptlyDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                PromptlyDatabase::class.java,
                DB_NAME
            )
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
