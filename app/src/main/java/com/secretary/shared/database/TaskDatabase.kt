package com.secretary.shared.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.secretary.features.dreams.data.dao.DreamAnalyticsDao
import com.secretary.features.dreams.data.dao.DreamDao
import com.secretary.features.dreams.data.dao.MilestoneDao
import com.secretary.features.dreams.data.dao.MilestoneInterdependencyDao
import com.secretary.features.dreams.data.dao.TaskMilestoneJunctionDao
import com.secretary.features.dreams.data.entity.DreamDailySnapshotEntity
import com.secretary.features.dreams.data.entity.DreamEntity
import com.secretary.features.dreams.data.entity.DreamXpHistoryEntity
import com.secretary.features.dreams.data.entity.MilestoneEntity
import com.secretary.features.dreams.data.entity.MilestoneInterdependencyEntity
import com.secretary.features.dreams.data.entity.TaskMilestoneJunction
import com.secretary.features.statistics.data.CompletionDao
import com.secretary.features.statistics.data.CompletionEntity
import com.secretary.features.tasks.data.TaskDao
import com.secretary.features.tasks.data.TaskEntity

/**
 * Room Database for AI Secretary app.
 * Phase 4.5.3 Wave 5: Converted to Kotlin
 * Phase 5.1 Wave 1: Added Dream feature entities
 * Phase 5.2 Wave 1: Added Dream Analytics entities
 *
 * Central database configuration with:
 * - TaskEntity (tasks table, 17 columns)
 * - CompletionEntity (completions table, 6 columns)
 * - DreamEntity (dreams table, 9 columns)
 * - MilestoneEntity (milestones table, 10 columns)
 * - TaskMilestoneJunction (task-milestone links)
 * - MilestoneInterdependencyEntity (milestone relationships)
 * - DreamXpHistoryEntity (XP transaction history)
 * - DreamDailySnapshotEntity (daily XP aggregates)
 * - Migration from SQLite v4 to Room v5, v5 to v6, v6 to v7
 */
@Database(
    entities = [
        TaskEntity::class,
        CompletionEntity::class,
        DreamEntity::class,
        MilestoneEntity::class,
        TaskMilestoneJunction::class,
        MilestoneInterdependencyEntity::class,
        DreamXpHistoryEntity::class,
        DreamDailySnapshotEntity::class
    ],
    version = 7,
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

    /**
     * DAO for dreams table
     */
    abstract fun dreamDao(): DreamDao

    /**
     * DAO for milestones table
     */
    abstract fun milestoneDao(): MilestoneDao

    /**
     * DAO for task-milestone junction table
     */
    abstract fun taskMilestoneJunctionDao(): TaskMilestoneJunctionDao

    /**
     * DAO for milestone interdependencies table
     */
    abstract fun milestoneInterdependencyDao(): MilestoneInterdependencyDao

    /**
     * DAO for dream analytics (XP history and daily snapshots)
     */
    abstract fun dreamAnalyticsDao(): DreamAnalyticsDao

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
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
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
         * Migration from Room v5 to v6: Add Dream feature tables
         *
         * Adds 4 new tables for Dream-to-Task feature:
         * - dreams: User's long-term goals/dreams
         * - milestones: Progress markers for dreams
         * - task_milestone_junction: Links tasks to milestones
         * - milestone_interdependencies: Tracks milestone relationships
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Create dreams table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS dreams (
                        dream_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        icon_name TEXT,
                        color INTEGER NOT NULL DEFAULT -14575885,
                        total_xp INTEGER NOT NULL DEFAULT 0,
                        current_level INTEGER NOT NULL DEFAULT 1,
                        created_at INTEGER NOT NULL,
                        is_archived INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // 2. Create milestones table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS milestones (
                        milestone_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        dream_id INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        target_xp INTEGER NOT NULL DEFAULT 1000,
                        current_xp INTEGER NOT NULL DEFAULT 0,
                        status INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        completed_at INTEGER,
                        order_index INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (dream_id) REFERENCES dreams(dream_id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_milestones_dream_id ON milestones(dream_id)")

                // 3. Create task_milestone_junction table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS task_milestone_junction (
                        task_id INTEGER NOT NULL,
                        milestone_id INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        PRIMARY KEY (task_id, milestone_id),
                        FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
                        FOREIGN KEY (milestone_id) REFERENCES milestones(milestone_id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_task_milestone_junction_task_id ON task_milestone_junction(task_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_task_milestone_junction_milestone_id ON task_milestone_junction(milestone_id)")

                // 4. Create milestone_interdependencies table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS milestone_interdependencies (
                        source_milestone_id INTEGER NOT NULL,
                        target_milestone_id INTEGER NOT NULL,
                        impact_rating INTEGER NOT NULL,
                        description TEXT,
                        created_at INTEGER NOT NULL,
                        PRIMARY KEY (source_milestone_id, target_milestone_id),
                        FOREIGN KEY (source_milestone_id) REFERENCES milestones(milestone_id) ON DELETE CASCADE,
                        FOREIGN KEY (target_milestone_id) REFERENCES milestones(milestone_id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_milestone_interdependencies_source ON milestone_interdependencies(source_milestone_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_milestone_interdependencies_target ON milestone_interdependencies(target_milestone_id)")
            }
        }

        /**
         * Migration from Room v6 to v7: Add Dream Analytics tables
         *
         * Adds 2 new tables for Dream Analytics feature:
         * - dream_xp_history: Tracks individual XP transactions
         * - dream_daily_snapshots: Aggregates daily progress for charts
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Create dream_xp_history table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS dream_xp_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        dream_id INTEGER NOT NULL,
                        xp_change INTEGER NOT NULL,
                        source_type TEXT NOT NULL,
                        source_id INTEGER,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (dream_id) REFERENCES dreams(dream_id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_dream_xp_history_dream_id ON dream_xp_history(dream_id)")

                // 2. Create dream_daily_snapshots table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS dream_daily_snapshots (
                        dream_id INTEGER NOT NULL,
                        date_day INTEGER NOT NULL,
                        total_xp INTEGER NOT NULL,
                        milestones_completed INTEGER NOT NULL,
                        PRIMARY KEY (dream_id, date_day),
                        FOREIGN KEY (dream_id) REFERENCES dreams(dream_id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_dream_daily_snapshots_dream_id ON dream_daily_snapshots(dream_id)")
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
