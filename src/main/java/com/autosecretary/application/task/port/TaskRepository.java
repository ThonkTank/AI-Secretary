package com.autosecretary.application.task.port;

import com.autosecretary.database.task.Task;
import com.autosecretary.database.task.TaskSlot;

import java.util.List;

public interface TaskRepository {
    Task read(String id);

    List<Task> readAll();

    void write(Task task);

    void writeList(List<Task> tasks);

    void writeSlot(TaskSlot slot);

    void deleteAllCore();
}
