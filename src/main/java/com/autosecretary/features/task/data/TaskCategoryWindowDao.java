package com.autosecretary.features.task.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.autosecretary.features.task.domain.model.TaskCategoryWindow;

import java.util.List;

/**
 * Room DAO for {@link TaskCategoryWindow}. One-model concept (the entity is the domain model),
 * so callers use this DAO directly — no repository interface.
 */
@Dao
public interface TaskCategoryWindowDao {

    @Query("SELECT * FROM task_category_window ORDER BY dayOfWeek ASC, startTime ASC")
    List<TaskCategoryWindow> readAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void write(TaskCategoryWindow window);

    @Query("DELETE FROM task_category_window WHERE id = :id")
    void delete(String id);

    /** Removes every window bound to a category (used when a category is deleted). */
    @Query("DELETE FROM task_category_window WHERE categoryId = :categoryId")
    void deleteByCategory(String categoryId);
}
