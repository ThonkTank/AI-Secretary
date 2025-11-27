package com.secretary.features.dreams.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.secretary.features.dreams.data.entity.TaskMilestoneJunction

/**
 * Room DAO for task_milestone_junction table.
 * Dream-to-Task Feature - Data Layer
 *
 * Provides type-safe database queries for task-milestone link operations.
 */
@Dao
interface TaskMilestoneJunctionDao {

    // ========== CRUD Operations ==========

    /**
     * Insert a new task-milestone link
     */
    @Insert
    fun insertLink(link: TaskMilestoneJunction)

    /**
     * Delete a task-milestone link
     */
    @Delete
    fun deleteLink(link: TaskMilestoneJunction)

    // ========== Query Operations ==========

    /**
     * Get all milestone links for a specific task
     */
    @Query("SELECT * FROM task_milestone_junction WHERE task_id = :taskId")
    fun getLinksForTask(taskId: Long): List<TaskMilestoneJunction>

    /**
     * Get all task links for a specific milestone
     */
    @Query("SELECT * FROM task_milestone_junction WHERE milestone_id = :milestoneId")
    fun getLinksForMilestone(milestoneId: Long): List<TaskMilestoneJunction>

    /**
     * Delete all links for a specific task
     */
    @Query("DELETE FROM task_milestone_junction WHERE task_id = :taskId")
    fun deleteLinksForTask(taskId: Long)

    /**
     * Delete all links for a specific milestone
     */
    @Query("DELETE FROM task_milestone_junction WHERE milestone_id = :milestoneId")
    fun deleteLinksForMilestone(milestoneId: Long)

    /**
     * Check if a specific link exists
     */
    @Query("SELECT COUNT(*) FROM task_milestone_junction WHERE task_id = :taskId AND milestone_id = :milestoneId")
    fun linkExists(taskId: Long, milestoneId: Long): Int

    /**
     * Get count of tasks linked to a milestone
     */
    @Query("SELECT COUNT(*) FROM task_milestone_junction WHERE milestone_id = :milestoneId")
    fun getTaskCountForMilestone(milestoneId: Long): Int

    /**
     * Get count of milestones linked to a task
     */
    @Query("SELECT COUNT(*) FROM task_milestone_junction WHERE task_id = :taskId")
    fun getMilestoneCountForTask(taskId: Long): Int
}
