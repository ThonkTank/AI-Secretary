package com.secretary.shared.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.secretary.features.statistics.data.CompletionDao
import com.secretary.features.statistics.data.CompletionEntity
import com.secretary.features.tasks.data.TaskDao
import com.secretary.features.tasks.data.TaskEntity

/**
 * Room Database for AI Secretary app.
 * Phase 4.5.3 Wave 5: Converted to Kotlin
 *
 * Central database configuration with:
 * - TaskEntity (tasks table, 17 columns)
 * - CompletionEntity (completions table, 6 columns)
 * - Migration from SQLite v4 to Room v5
 */
@Database(
    entities = [TaskEntity::class, CompletionEntity::class],
    version = 5,
    exportSchema = true
)
abstract class TaskDatabase : RoomDatabase() {

    // ========== DAOs ==========

    /**
     * DAO for tasks table
     */
    abstract fun taskDao(): TaskDao

    /**
     * DAO for completions table
     */
    abstract fun completionDao(): CompletionDao

    companion object {
        // ========== Singleton Pattern ==========

        @Volatile
        private var INSTANCE: TaskDatabase? = null

        /**
         * Get database instance (singleton)
         */
        fun getDatabase(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    DatabaseConstants.DATABASE_NAME
                )
                    .addMigrations(MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // ========== Database Migrations ==========

        /**
         * Migration from SQLite v4 (TaskDatabaseHelper) to Room v5
         *
         * The schema is compatible, so no structural changes needed.
         * Room will take over managing the existing database.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Schema v4 already has all required columns:
                // - tasks table: 17 columns (id, title, description, category, created_at, due_date,
                //                           is_completed, priority, recurrence_type, recurrence_amount,
                //                           recurrence_unit, last_completed_date, completions_this_period,
                //                           current_period_start, current_streak, longest_streak, last_streak_date)
                // - completions table: 6 columns (completion_id, task_id, completed_at, time_spent_minutes,
                //                                 difficulty, notes)
                //
                // No structural migration needed, Room will validate schema compatibility.
                // This migration simply marks the transition from SQLite to Room.
            }
        }

        /**
         * Close database instance (for testing purposes)
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
