package com.autosecretary.features.task.application;

import com.autosecretary.features.task.application.mapper.TaskListItemMapper;
import com.autosecretary.features.task.application.model.TaskListItem;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDAO;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class LoadTaskListUseCase {
    private final TaskDAO taskDao;
    private final TaskListItemMapper mapper;
    private final ExecutorService executor;

    public LoadTaskListUseCase(TaskDAO taskDao, TaskListItemMapper mapper, ExecutorService executor) {
        this.taskDao = taskDao;
        this.mapper = mapper;
        this.executor = executor;
    }

    public void execute(Consumer<List<TaskListItem>> callback) {
        executor.execute(() -> callback.accept(mapper.map(taskDao.readAll())));
    }

    public void loadTask(String taskId, Consumer<Task> callback) {
        executor.execute(() -> callback.accept(taskDao.read(taskId)));
    }
}
