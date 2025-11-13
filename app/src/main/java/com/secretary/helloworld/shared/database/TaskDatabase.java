package com.secretary.helloworld.shared.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.secretary.helloworld.features.tasks.data.TaskEntity;
import com.secretary.helloworld.features.tasks.data.TaskDao;
import com.secretary.helloworld.features.statistics.data.CompletionEntity;
import com.secretary.helloworld.features.statistics.data.CompletionDao;

/**
 * Room Database for AI Secretary app.
 * Phase 4.5.3: Data Layer - Room Migration
 *
 * Central database configuration with:
 * - TaskEntity (tasks table, 17 columns)
 * - CompletionEntity (completions table, 6 columns)
 * - Migration from SQLite v4 to Room v5
 */
@Database(
    entities = {TaskEntity.class, CompletionEntity.class},
    version = 5,
    exportSchema = true
)
public abstract class TaskDatabase extends RoomDatabase {

    // ========== DAOs ==========

    /**
     * DAO for tasks table
     */
    public abstract TaskDao taskDao();

    /**
     * DAO for completions table
     */
    public abstract CompletionDao completionDao();

    // ========== Singleton Pattern ==========

    private static volatile TaskDatabase INSTANCE;

    /**
     * Get database instance (singleton)
     */
    public static TaskDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (TaskDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            TaskDatabase.class,
                            DatabaseConstants.DATABASE_NAME
                        )
                        .addMigrations(MIGRATION_4_5)
                        .build();
                }
            }
        }
        return INSTANCE;
    }

    // ========== Database Migrations ==========

    /**
     * Migration from SQLite v4 (TaskDatabaseHelper) to Room v5
     *
     * The schema is compatible, so no structural changes needed.
     * Room will take over managing the existing database.
     */
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
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
    };

    /**
     * Close database instance (for testing purposes)
     */
    public static void closeDatabase() {
        if (INSTANCE != null) {
            INSTANCE.close();
            INSTANCE = null;
        }
    }
}
