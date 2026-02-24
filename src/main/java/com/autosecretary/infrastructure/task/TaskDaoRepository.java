package com.autosecretary.infrastructure.task;

import com.autosecretary.application.task.port.TaskRepository;
import com.autosecretary.database.task.Task;
import com.autosecretary.database.task.TaskDAO;
import com.autosecretary.database.task.TaskSlot;

import java.util.List;

public class TaskDaoRepository implements TaskRepository {
    private final TaskDAO taskDao;

    public TaskDaoRepository(TaskDAO taskDao) {
        this.taskDao = taskDao;
    }

    @Override
    public Task read(String id) {
        return taskDao.read(id);
    }

    @Override
    public List<Task> readAll() {
        return taskDao.readAll();
    }

    @Override
    public void write(Task task) {
        taskDao.write(task);
    }

    @Override
    public void writeList(List<Task> tasks) {
        taskDao.writeList(tasks);
    }

    @Override
    public void writeSlot(TaskSlot slot) {
        taskDao.writeSlot(slot);
    }

    @Override
    public void deleteAllCore() {
        taskDao.deleteAllCore();
    }
}
