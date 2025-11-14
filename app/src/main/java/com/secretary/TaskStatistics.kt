package com.secretary

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.secretary.shared.database.DatabaseConstants.COLUMN_COMPLETED_AT
import com.secretary.shared.database.DatabaseConstants.COLUMN_COMPLETION_ID
import com.secretary.shared.database.DatabaseConstants.COLUMN_DIFFICULTY
import com.secretary.shared.database.DatabaseConstants.COLUMN_DUE_DATE
import com.secretary.shared.database.DatabaseConstants.COLUMN_IS_COMPLETED
import com.secretary.shared.database.DatabaseConstants.COLUMN_NOTES
import com.secretary.shared.database.DatabaseConstants.COLUMN_TASK_ID
import com.secretary.shared.database.DatabaseConstants.COLUMN_TIME_SPENT
import com.secretary.shared.database.DatabaseConstants.TABLE_COMPLETIONS
import com.secretary.shared.database.DatabaseConstants.TABLE_TASKS
import java.util.Calendar

/**
 * Helper class for task statistics and analytics.
 * Phase 4.5.3 Wave 2: Converted to Kotlin
 *
 * Extracted from TaskDatabaseHelper for better organization.
 * Provides statistical queries and analytics for tasks and completions.
 *
 * Note: This class will be refactored into Repository pattern in Phase 4.5.5
 * when Room ORM is integrated.
 */
class TaskStatistics(private val database: SQLiteDatabase) {

    /**
     * Get total task count
     */
    fun getTaskCount(): Int {
        database.rawQuery("SELECT COUNT(*) FROM $TABLE_TASKS", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /**
     * Get count of tasks completed today
     */
    fun getTasksCompletedToday(): Int {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val query = "SELECT COUNT(*) FROM $TABLE_COMPLETIONS WHERE $COLUMN_COMPLETED_AT >= ?"

        database.rawQuery(query, arrayOf(todayStart.toString())).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /**
     * Get count of tasks completed in last 7 days
     */
    fun getTasksCompletedLast7Days(): Int {
        val weekAgo = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val query = "SELECT COUNT(*) FROM $TABLE_COMPLETIONS WHERE $COLUMN_COMPLETED_AT >= ?"

        database.rawQuery(query, arrayOf(weekAgo.toString())).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /**
     * Get count of overdue tasks
     */
    fun getOverdueTasksCount(): Int {
        val currentTime = System.currentTimeMillis()
        val query = """
            SELECT COUNT(*) FROM $TABLE_TASKS
            WHERE $COLUMN_DUE_DATE > 0
            AND $COLUMN_DUE_DATE < ?
            AND $COLUMN_IS_COMPLETED = 0
        """.trimIndent()

        database.rawQuery(query, arrayOf(currentTime.toString())).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /**
     * Get average completion time for a task in minutes
     */
    fun getAverageCompletionTime(taskId: Long): Int {
        val query = """
            SELECT AVG($COLUMN_TIME_SPENT) FROM $TABLE_COMPLETIONS
            WHERE $COLUMN_TASK_ID = ? AND $COLUMN_TIME_SPENT > 0
        """.trimIndent()

        database.rawQuery(query, arrayOf(taskId.toString())).use { cursor ->
            return if (cursor.moveToFirst() && !cursor.isNull(0)) {
                cursor.getInt(0)
            } else {
                0
            }
        }
    }

    /**
     * Get task completion history
     * Returns list of completion records ordered by most recent first
     */
    fun getTaskCompletionHistory(taskId: Long): List<ContentValues> {
        val history = mutableListOf<ContentValues>()

        val query = """
            SELECT * FROM $TABLE_COMPLETIONS
            WHERE $COLUMN_TASK_ID = ?
            ORDER BY $COLUMN_COMPLETED_AT DESC
        """.trimIndent()

        database.rawQuery(query, arrayOf(taskId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val values = ContentValues().apply {
                    put(COLUMN_COMPLETION_ID, cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_COMPLETION_ID)))
                    put(COLUMN_TASK_ID, cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TASK_ID)))
                    put(COLUMN_COMPLETED_AT, cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_COMPLETED_AT)))
                    put(COLUMN_TIME_SPENT, cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_TIME_SPENT)))
                    put(COLUMN_DIFFICULTY, cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DIFFICULTY)))
                    put(COLUMN_NOTES, cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTES)))
                }
                history.add(values)
            }
        }

        return history
    }
}
