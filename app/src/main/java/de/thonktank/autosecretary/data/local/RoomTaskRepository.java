package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.AppDatabase;
import de.thonktank.autosecretary.OccurrenceEntity;
import de.thonktank.autosecretary.OccurrenceStepEntity;
import de.thonktank.autosecretary.StatsEntity;
import de.thonktank.autosecretary.TaskDao;
import de.thonktank.autosecretary.TaskEntity;
import de.thonktank.autosecretary.TaskStepEntity;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class RoomTaskRepository implements TaskRepository {
    private static final long SLOT_RANGE = 1_000_000L;
    private static final long ORDER_STEP = 1_000L;

    private final AppDatabase database;
    private final TaskDao dao;
    private final TaskEntityMapper mapper;

    public RoomTaskRepository(AppDatabase database) {
        this(database, new TaskEntityMapper());
    }

    RoomTaskRepository(AppDatabase database, TaskEntityMapper mapper) {
        this.database = database;
        this.dao = database.tasks();
        this.mapper = mapper;
    }

    @Override public void inTransaction(Runnable operation) {
        database.runInTransaction(operation);
    }

    @Override public void insertTask(Task task) {
        dao.insertTask(mapper.toEntity(task));
    }

    @Override public void updateTask(Task task) {
        dao.updateTask(mapper.toEntity(task));
    }

    @Override public Task findTask(TaskId id) {
        TaskEntity entity = dao.task(id.value);
        return entity == null ? null : mapper.toDomain(entity);
    }

    @Override public List<Task> activeTasks() {
        return mapTasks(dao.activeTasks());
    }

    @Override public List<Task> allTasks() {
        return mapTasks(dao.allTasks());
    }

    @Override public void deleteTask(TaskId id) {
        dao.deleteTask(id.value);
    }

    @Override public long nextTaskOrder(TaskSlot slot) {
        long floor = (slot.rank + 1L) * SLOT_RANGE;
        Long max = dao.maxTaskOrder(floor, floor + SLOT_RANGE);
        return max == null || max < floor || max >= floor + SLOT_RANGE ? floor + ORDER_STEP : max + ORDER_STEP;
    }

    @Override public void insertTemplates(List<TaskStepTemplate> steps) {
        List<TaskStepEntity> entities = new ArrayList<>();
        for (TaskStepTemplate step : steps) entities.add(mapper.toEntity(step));
        if (!entities.isEmpty()) dao.insertTemplates(entities);
    }

    @Override public List<TaskStepTemplate> templates(TaskId taskId) {
        List<TaskStepTemplate> result = new ArrayList<>();
        for (TaskStepEntity entity : dao.templates(taskId.value)) result.add(mapper.toDomain(entity));
        return result;
    }

    @Override public void insertOccurrence(Occurrence occurrence) {
        dao.insertOccurrence(mapper.toEntity(occurrence));
    }

    @Override public void updateOccurrence(Occurrence occurrence) {
        dao.updateOccurrence(mapper.toEntity(occurrence));
    }

    @Override public Occurrence findOccurrence(String id) {
        OccurrenceEntity entity = dao.occurrence(id);
        return entity == null ? null : mapper.toDomain(entity);
    }

    @Override public Occurrence openOccurrence(TaskId taskId) {
        OccurrenceEntity entity = dao.openForTask(taskId.value, OccurrenceState.OPEN.storageCode());
        return entity == null ? null : mapper.toDomain(entity);
    }

    @Override public List<Occurrence> openOccurrences() {
        return mapOccurrences(dao.occurrencesByState(OccurrenceState.OPEN.storageCode()));
    }

    @Override public List<Occurrence> completedOccurrences(LocalDate date) {
        return mapOccurrences(dao.completedOccurrences(OccurrenceState.COMPLETED.storageCode(), date.toString()));
    }

    @Override public int nextOpenOccurrenceOrder(TaskSlot slot) {
        Integer max = dao.maxOpenOrder(OccurrenceState.OPEN.storageCode(), slot.storageCode);
        return max == null ? 1000 : max + 1000;
    }

    @Override public void insertOccurrenceSteps(List<OccurrenceStep> steps) {
        List<OccurrenceStepEntity> entities = new ArrayList<>();
        for (OccurrenceStep step : steps) entities.add(mapper.toEntity(step));
        if (!entities.isEmpty()) dao.insertOccurrenceSteps(entities);
    }

    @Override public List<OccurrenceStep> occurrenceSteps(String occurrenceId) {
        List<OccurrenceStep> result = new ArrayList<>();
        for (OccurrenceStepEntity entity : dao.occurrenceSteps(occurrenceId)) result.add(mapper.toDomain(entity));
        return result;
    }

    @Override public OccurrenceStep findOccurrenceStep(String id) {
        OccurrenceStepEntity entity = dao.occurrenceStep(id);
        return entity == null ? null : mapper.toDomain(entity);
    }

    @Override public void updateOccurrenceStep(OccurrenceStep step) {
        dao.updateOccurrenceStep(mapper.toEntity(step));
    }

    @Override public int xp() {
        StatsEntity stats = dao.stats();
        return stats == null ? 0 : stats.xp;
    }

    @Override public void setXp(int xp) {
        dao.putStats(new StatsEntity(Math.max(0, xp)));
    }

    private List<Task> mapTasks(List<TaskEntity> entities) {
        List<Task> result = new ArrayList<>();
        for (TaskEntity entity : entities) result.add(mapper.toDomain(entity));
        return result;
    }

    private List<Occurrence> mapOccurrences(List<OccurrenceEntity> entities) {
        List<Occurrence> result = new ArrayList<>();
        for (OccurrenceEntity entity : entities) result.add(mapper.toDomain(entity));
        return result;
    }
}
