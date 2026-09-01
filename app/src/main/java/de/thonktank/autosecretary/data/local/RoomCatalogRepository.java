package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.AppDatabase;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;

import java.util.ArrayList;
import java.util.List;

/** Room adapter for tasks, catalog reads and schedules. */
public final class RoomCatalogRepository implements CatalogRepository {
    private final CatalogDao dao;
    private final TaskEntityMapper mapper = new TaskEntityMapper();

    public RoomCatalogRepository(AppDatabase database) { this.dao = database.catalog(); }

    @Override public void insertTask(Task task) { dao.insertTask(mapper.toEntity(task)); }
    @Override public void updateTask(Task task) { dao.updateTask(mapper.toEntity(task)); }

    @Override public Task findTask(TaskId id) {
        TaskEntity value = dao.task(id.value);
        return value == null ? null : mapper.toDomain(value);
    }

    @Override public List<Task> activeTasks() { return mapTasks(dao.activeTasks()); }
    @Override public List<Task> allTasks() { return mapTasks(dao.allTasks()); }
    @Override public void deleteTask(TaskId id) { dao.deleteTask(id.value); }

    @Override public void putScheduleEntries(List<TaskScheduleEntry> entries) {
        List<TaskScheduleEntity> values = new ArrayList<>();
        for (TaskScheduleEntry entry : entries) values.add(mapper.toEntity(entry));
        if (!values.isEmpty()) dao.putScheduleEntries(values);
    }

    @Override public void deleteScheduleEntry(String id) { dao.deleteScheduleEntry(id); }
    @Override public List<TaskScheduleEntry> scheduleEntries() {
        return mapScheduleEntries(dao.scheduleEntries());
    }
    @Override public List<TaskScheduleEntry> scheduleEntries(TaskId taskId) {
        return mapScheduleEntries(dao.scheduleEntries(taskId.value));
    }
    @Override public List<TaskScheduleEntry> scheduleEntries(TaskSlot slot) {
        return mapScheduleEntries(dao.scheduleEntriesInSlot(slot.name()));
    }
    @Override public List<TaskScheduleEntry> scheduleEntriesFor(List<TaskId> taskIds) {
        if (taskIds.isEmpty()) return new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (TaskId id : taskIds) values.add(id.value);
        return mapScheduleEntries(dao.scheduleEntriesFor(values));
    }
    @Override public TaskScheduleEntry findScheduleEntry(String id) {
        TaskScheduleEntity value = dao.scheduleEntry(id);
        return value == null ? null : mapper.toDomain(value);
    }

    private List<Task> mapTasks(List<TaskEntity> entities) {
        List<Task> result = new ArrayList<>();
        for (TaskEntity entity : entities) result.add(mapper.toDomain(entity));
        return result;
    }

    private List<TaskScheduleEntry> mapScheduleEntries(List<TaskScheduleEntity> entities) {
        List<TaskScheduleEntry> result = new ArrayList<>();
        for (TaskScheduleEntity entity : entities) result.add(mapper.toDomain(entity));
        return result;
    }
}
