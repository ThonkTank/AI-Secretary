package com.autosecretary.features.task.application;

import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.data.TaskCategoryDao;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.model.TaskCategory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the task widget's read model through the application layer.
 *
 * <p>The widget shows a flat, priority-sorted list of open tasks (no day scoping), optionally
 * filtered to a single category. This use case keeps that data loading behind the application
 * boundary: "load all tasks -> map to read model with categories -> one row per task -> filter
 * to open tasks (and the selected category) -> sort by priority".
 */
public class LoadTaskWidgetItemsUseCase {
    /** Selected-category sentinel meaning "no filter" (show every category). */
    public static final String CATEGORY_ALL = "__all__";

    /** Highest priority first, then nearest deadline, then title. */
    private static final Comparator<TaskListItem> BY_PRIORITY =
            Comparator.comparingInt((TaskListItem item) -> item.priorityWeight).reversed()
                    .thenComparing(item -> item.deadline, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(item -> item.title, Comparator.nullsLast(Comparator.naturalOrder()));

    private final TaskDao taskDao;
    private final TaskCategoryDao taskCategoryDao;
    private final TaskListItemMapper mapper;

    public LoadTaskWidgetItemsUseCase(TaskDao taskDao, TaskCategoryDao taskCategoryDao, TaskListItemMapper mapper) {
        this.taskDao = taskDao;
        this.taskCategoryDao = taskCategoryDao;
        this.mapper = mapper;
    }

    /**
     * Returns the open tasks to render, one row per task, sorted by priority.
     *
     * @param selectedCategoryId a category id to filter to, or {@link #CATEGORY_ALL}/null for no filter
     */
    public List<TaskListItem> execute(String selectedCategoryId) {
        List<TaskListItem> items = mapper.map(taskDao.readAll(), taskCategoryDao.readAll());

        // Collapse to one representative row per task, preferring an open (non-completed) occurrence.
        Map<String, TaskListItem> byTaskId = new LinkedHashMap<>();
        for (TaskListItem item : items) {
            if (item.taskId == null) {
                continue;
            }
            TaskListItem current = byTaskId.get(item.taskId);
            if (current == null || (current.completed && !item.completed)) {
                byTaskId.put(item.taskId, item);
            }
        }

        boolean filterByCategory = selectedCategoryId != null && !CATEGORY_ALL.equals(selectedCategoryId);
        List<TaskListItem> result = new ArrayList<>();
        for (TaskListItem item : byTaskId.values()) {
            if (item.completed) {
                continue;
            }
            if (filterByCategory && !selectedCategoryId.equals(item.categoryId)) {
                continue;
            }
            result.add(item);
        }
        result.sort(BY_PRIORITY);
        return result;
    }

    /** All categories, for the widget's filter chips (ordered by sortOrder then name). */
    public List<TaskCategory> categories() {
        return taskCategoryDao.readAll();
    }
}
