package com.autosecretary.features.task.application;

import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.data.TaskDao;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Loads the task widget's read model through the application layer.
 *
 * <p>The widget needs a synchronous, render-ready snapshot for {@code RemoteViews} rendering.
 * This use case keeps widget data loading behind the application boundary and centralizes the
 * "load all tasks -> map to read model -> filter to day -> sort by start time" flow.
 */
public class LoadTaskWidgetItemsUseCase {
    private static final Comparator<TaskListItem> BY_START_TIME =
            Comparator.comparing(item -> item.start, Comparator.nullsLast(Comparator.naturalOrder()));

    private final TaskDao taskDao;
    private final TaskListItemMapper mapper;

    public LoadTaskWidgetItemsUseCase(TaskDao taskDao, TaskListItemMapper mapper) {
        this.taskDao = taskDao;
        this.mapper = mapper;
    }

    public List<TaskListItem> execute(LocalDate selectedDate) {
        return mapper.map(taskDao.readAll()).stream()
                .filter(item -> item.isScheduledOn(selectedDate))
                .sorted(BY_START_TIME)
                .toList();
    }
}
