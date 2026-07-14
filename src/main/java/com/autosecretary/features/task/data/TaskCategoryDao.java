package com.autosecretary.features.task.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.autosecretary.features.task.domain.model.TaskCategory;

import java.util.List;

/**
 * Room DAO for {@link TaskCategory} persistence. Categories are a one-model concept
 * (the entity is the domain model), so callers use this DAO directly — there is no
 * repository interface.
 */
@Dao
public interface TaskCategoryDao {

    @Query("SELECT * FROM task_category ORDER BY sortOrder ASC, name ASC")
    List<TaskCategory> readAll();

    @Query("SELECT * FROM task_category WHERE id = :id")
    TaskCategory read(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void write(TaskCategory category);

    @Query("DELETE FROM task_category WHERE id = :id")
    void delete(String id);

    /**
     * Clears the category assignment from every task pointing at the given category.
     * Called before {@link #delete(String)} to emulate ON DELETE SET NULL (the
     * {@code task_core.categoryId} column has no {@code @ForeignKey}).
     */
    @Query("UPDATE task_core SET categoryId = NULL WHERE categoryId = :categoryId")
    void clearCategoryFromTasks(String categoryId);
}
