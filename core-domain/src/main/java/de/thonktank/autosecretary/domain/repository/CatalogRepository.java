package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.util.List;

/** Tasks, catalog queries, schedules and reusable task definitions. */
public interface CatalogRepository {
    void insertTask(Task task);
    void updateTask(Task task);
    Task findTask(TaskId id);
    List<Task> activeTasks();
    List<Task> allTasks();
    void deleteTask(TaskId id);
    void putScheduleEntries(List<TaskScheduleEntry> entries);
    void deleteScheduleEntry(String id);
    List<TaskScheduleEntry> scheduleEntries();
    List<TaskScheduleEntry> scheduleEntries(TaskId taskId);
    List<TaskScheduleEntry> scheduleEntries(TaskSlot slot);
    List<TaskScheduleEntry> scheduleEntriesFor(List<TaskId> taskIds);
    TaskScheduleEntry findScheduleEntry(String id);
}
