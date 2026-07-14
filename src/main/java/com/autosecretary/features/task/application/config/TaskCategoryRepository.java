package com.autosecretary.features.task.application.config;

import com.autosecretary.features.task.data.TaskCategoryDao;
import com.autosecretary.features.task.data.TaskCategoryWindowDao;
import com.autosecretary.features.task.domain.model.TaskCategory;

import java.util.List;

/**
 * Application-layer seam for category CRUD, so UI (dialogs/ViewModels) never touches the DAOs
 * directly. Deleting a category cascades: it clears the category from any task (emulating
 * ON DELETE SET NULL, since {@code task_core.categoryId} has no {@code @ForeignKey}) and removes
 * that category's time windows.
 */
public class TaskCategoryRepository {

    private final TaskCategoryDao categoryDao;
    private final TaskCategoryWindowDao windowDao;

    public TaskCategoryRepository(TaskCategoryDao categoryDao, TaskCategoryWindowDao windowDao) {
        this.categoryDao = categoryDao;
        this.windowDao = windowDao;
    }

    public List<TaskCategory> loadAll() {
        return categoryDao.readAll();
    }

    /**
     * Persists the surviving categories (assigning ascending {@code sortOrder}) and removes the
     * deleted ones, cascading each deletion to its tasks and time windows.
     */
    public void saveAll(List<TaskCategory> categories, List<String> deletedIds) {
        for (String deletedId : deletedIds) {
            categoryDao.clearCategoryFromTasks(deletedId);
            windowDao.deleteByCategory(deletedId);
            categoryDao.delete(deletedId);
        }
        int sortOrder = 0;
        for (TaskCategory category : categories) {
            category.sortOrder = sortOrder++;
            categoryDao.write(category);
        }
    }
}
