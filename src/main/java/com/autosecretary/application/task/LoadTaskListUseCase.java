package com.autosecretary.application.task;

import com.autosecretary.application.task.mapper.TaskListItemMapper;
import com.autosecretary.application.task.model.TaskListItem;
import com.autosecretary.database.task.Task;
import com.autosecretary.application.task.port.TaskRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class LoadTaskListUseCase {
    private final TaskRepository taskRepository;
    private final TaskListItemMapper mapper;
    private final ExecutorService executor;

    public LoadTaskListUseCase(TaskRepository taskRepository, TaskListItemMapper mapper, ExecutorService executor) {
        this.taskRepository = taskRepository;
        this.mapper = mapper;
        this.executor = executor;
    }

    public void execute(Consumer<List<TaskListItem>> callback) {
        executor.execute(() -> callback.accept(mapper.map(taskRepository.readAll())));
    }

    public void loadTask(String taskId, Consumer<Task> callback) {
        executor.execute(() -> callback.accept(taskRepository.read(taskId)));
    }
}
