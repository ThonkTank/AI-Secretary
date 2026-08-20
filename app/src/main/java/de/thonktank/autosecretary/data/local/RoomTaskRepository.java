package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.AppDatabase;
import de.thonktank.autosecretary.OccurrenceEntity;
import de.thonktank.autosecretary.OccurrenceStepEntity;
import de.thonktank.autosecretary.StatsEntity;
import de.thonktank.autosecretary.ComboEntity;
import de.thonktank.autosecretary.RewardBookingEntity;
import de.thonktank.autosecretary.RepetitionResultEntity;
import de.thonktank.autosecretary.TaskDao;
import de.thonktank.autosecretary.TaskEntity;
import de.thonktank.autosecretary.TaskStepEntity;
import de.thonktank.autosecretary.TaskScheduleEntity;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.repository.TaskRepository;
import de.thonktank.autosecretary.domain.repository.TransactionalRepository;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.RewardBooking;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public final class RoomTaskRepository implements TaskRepository {
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

    @Override public <T> T inTransaction(TransactionalRepository.Transaction<T> operation) {
        return database.runInTransaction((Callable<T>) operation::execute);
    }

    @Override public void insertTask(Task task) {
        dao.insertTask(mapper.toEntity(task));
        String owner = ComboProgress.taskOwner(task.id);
        if (dao.combo(owner) == null)
            putCombo(ComboProgress.fresh(owner, task.id, ComboProgress.Kind.TASK));
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

    @Override public void insertTemplates(List<TaskStepTemplate> steps) {
        List<TaskStepEntity> entities = new ArrayList<>();
        for (TaskStepTemplate step : steps) entities.add(mapper.toEntity(step));
        if (!entities.isEmpty()) dao.insertTemplates(entities);
        for (TaskStepTemplate step : steps) {
            String owner = ComboProgress.stepOwner(step.id);
            if (dao.combo(owner) == null)
                putCombo(ComboProgress.fresh(owner, step.taskId, ComboProgress.Kind.STEP));
        }
    }

    @Override public void deleteTemplates(TaskId taskId) {
        dao.deleteTemplates(taskId.value);
    }

    @Override public void deleteTemplate(String id) { dao.deleteTemplate(id); }

    @Override public List<TaskStepTemplate> templates(TaskId taskId) {
        List<TaskStepTemplate> result = new ArrayList<>();
        for (TaskStepEntity entity : dao.templates(taskId.value)) result.add(mapper.toDomain(entity));
        return result;
    }

    @Override public List<TaskStepTemplate> templatesFor(List<TaskId> taskIds) {
        if (taskIds.isEmpty()) return new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (TaskId id : taskIds) values.add(id.value);
        List<TaskStepTemplate> result = new ArrayList<>();
        for (TaskStepEntity entity : dao.templatesFor(values)) result.add(mapper.toDomain(entity));
        return result;
    }

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

    @Override public List<TaskScheduleEntry> scheduleEntriesFor(List<TaskId> taskIds) {
        if (taskIds.isEmpty()) return new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (TaskId id : taskIds) values.add(id.value);
        return mapScheduleEntries(dao.scheduleEntriesFor(values));
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

    @Override public Occurrence findOccurrence(TaskId taskId, LocalDate scheduledOn,
                                               TaskSlot slot) {
        OccurrenceEntity entity = dao.occurrence(taskId.value, scheduledOn.toString(),
                slot.storageCode);
        return entity == null ? null : mapper.toDomain(entity);
    }

    @Override public List<Occurrence> openOccurrences(TaskId taskId, LocalDate scheduledOn) {
        return mapOccurrences(dao.occurrences(taskId.value, scheduledOn.toString(),
                OccurrenceState.OPEN.storageCode()));
    }

    @Override public Occurrence openOccurrence(TaskId taskId) {
        OccurrenceEntity entity = dao.openForTask(taskId.value, OccurrenceState.OPEN.storageCode());
        return entity == null ? null : mapper.toDomain(entity);
    }

    @Override public List<Occurrence> openOccurrences() {
        return mapOccurrences(dao.occurrencesByState(OccurrenceState.OPEN.storageCode()));
    }

    @Override public List<Occurrence> allOccurrences() {
        return mapOccurrences(dao.allOccurrences());
    }

    @Override public List<Occurrence> occurrences(TaskId taskId) {
        return mapOccurrences(dao.occurrencesForTask(taskId.value));
    }

    @Override public Occurrence earliestOpenOccurrence(TaskId taskId) {
        OccurrenceEntity entity = dao.earliestOccurrence(taskId.value,
                OccurrenceState.OPEN.storageCode());
        return entity == null ? null : mapper.toDomain(entity);
    }

    @Override public Occurrence latestCompletedOccurrence(TaskId taskId) {
        OccurrenceEntity entity = dao.latestCompletedOccurrence(taskId.value,
                harvestedStates());
        return entity == null ? null : mapper.toDomain(entity);
    }

    @Override public List<Occurrence> completedOccurrences(LocalDate date) {
        return mapOccurrences(dao.completedOccurrences(harvestedStates(), date.toString()));
    }

    private static List<String> harvestedStates() {
        List<String> result = new ArrayList<>();
        result.add(OccurrenceState.COMPLETED.storageCode());
        result.add(OccurrenceState.HARVESTED_WITH_MISSED_STEPS.storageCode());
        return result;
    }

    @Override public void insertOccurrenceSteps(List<OccurrenceStep> steps) {
        database.runInTransaction(() -> {
            List<OccurrenceStepEntity> entities = new ArrayList<>();
            for (OccurrenceStep step : steps) entities.add(mapper.toEntity(step));
            if (!entities.isEmpty()) dao.insertOccurrenceSteps(entities);
            List<RepetitionResultEntity> results = new ArrayList<>();
            for (OccurrenceStep step : steps) {
                List<Integer> values = step.repetitionProgress == null
                        ? java.util.Collections.emptyList()
                        : step.repetitionProgress.actualRepetitions;
                for (int index = 0; index < values.size(); index++)
                    results.add(new RepetitionResultEntity(step.id, index, values.get(index)));
            }
            if (!results.isEmpty()) dao.putRepetitionResults(results);
        });
    }

    @Override public List<OccurrenceStep> occurrenceSteps(String occurrenceId) {
        return mapOccurrenceSteps(dao.occurrenceSteps(occurrenceId));
    }

    @Override public List<OccurrenceStep> occurrenceStepsFor(List<String> occurrenceIds) {
        if (occurrenceIds.isEmpty()) return new ArrayList<>();
        return mapOccurrenceSteps(dao.occurrenceStepsFor(occurrenceIds));
    }

    @Override public OccurrenceStep findOccurrenceStep(String id) {
        OccurrenceStepEntity entity = dao.occurrenceStep(id);
        return entity == null ? null : mapper.toDomain(entity, repetitions(id));
    }

    @Override public void updateOccurrenceStep(OccurrenceStep step) {
        database.runInTransaction(() -> {
            dao.updateOccurrenceStep(mapper.toEntity(step));
            syncRepetitionResults(step);
        });
    }

    @Override public void moveRewardBookings(String occurrenceStepId, String occurrenceId) {
        dao.moveRewardBookings(occurrenceStepId, occurrenceId);
    }

    @Override public int xp() {
        StatsEntity stats = dao.stats();
        return stats == null ? 0 : stats.xp;
    }

    @Override public void setXp(int xp) {
        dao.putStats(new StatsEntity(Math.max(0, xp)));
    }

    @Override public ComboProgress combo(String ownerId) {
        ComboEntity value = dao.combo(ownerId);
        return value == null ? null : combo(value);
    }

    @Override public void putCombo(ComboProgress combo) {
        dao.putCombo(new ComboEntity(combo.ownerId, combo.taskId.value, combo.kind.name(),
                combo.points, combo.settledThroughOn == null ? "" : combo.settledThroughOn.toString()));
    }

    @Override public List<ComboProgress> combos() {
        List<ComboProgress> result = new ArrayList<>();
        for (ComboEntity value : dao.allCombos()) result.add(combo(value));
        return result;
    }

    @Override public void insertRewardBooking(RewardBooking booking) {
        dao.insertRewardBooking(new RewardBookingEntity(booking.id, booking.transactionId,
                booking.occurrenceId, booking.occurrenceStepId, booking.ownerId,
                booking.kind.name(), booking.target.name(), booking.xpDelta,
                booking.comboPointDelta, booking.bookedOn.toString(), booking.reversesBookingId));
    }

    @Override public List<RewardBooking> rewardBookings(String occurrenceId) {
        return mapBookings(dao.rewardBookings(occurrenceId));
    }

    @Override public List<RewardBooking> rewardBookings(List<String> occurrenceIds) {
        if (occurrenceIds.isEmpty()) return new ArrayList<>();
        return mapBookings(dao.rewardBookings(occurrenceIds));
    }

    private static List<RewardBooking> mapBookings(List<RewardBookingEntity> entities) {
        List<RewardBooking> result = new ArrayList<>();
        for (RewardBookingEntity value : entities)
            result.add(new RewardBooking(value.id, value.transactionId, value.occurrenceId,
                    value.occurrenceStepId, value.ownerId, RewardBooking.Kind.valueOf(value.kind),
                    RewardBooking.Target.valueOf(value.target), value.xpDelta,
                    value.comboPointDelta, LocalDate.parse(value.bookedOn),
                    value.reversesBookingId));
        return result;
    }

    private static ComboProgress combo(ComboEntity value) {
        return new ComboProgress(value.ownerId, TaskId.of(value.taskId),
                ComboProgress.Kind.valueOf(value.kind), Math.max(0, value.points),
                value.settledThroughOn.isEmpty() ? null : LocalDate.parse(value.settledThroughOn));
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

    private List<Occurrence> mapOccurrences(List<OccurrenceEntity> entities) {
        List<Occurrence> result = new ArrayList<>();
        for (OccurrenceEntity entity : entities) result.add(mapper.toDomain(entity));
        return result;
    }

    private List<OccurrenceStep> mapOccurrenceSteps(List<OccurrenceStepEntity> entities) {
        if (entities.isEmpty()) return new ArrayList<>();
        List<String> ids = new ArrayList<>();
        Map<String, List<Integer>> repetitions = new LinkedHashMap<>();
        for (OccurrenceStepEntity entity : entities) {
            ids.add(entity.id);
            repetitions.put(entity.id, new ArrayList<>());
        }
        for (RepetitionResultEntity result : dao.repetitionResultsFor(ids)) {
            List<Integer> values = repetitions.get(result.stepId);
            if (values == null) continue;
            if (result.slotIndex == values.size()) values.add(result.actualRepetitions);
            else android.util.Log.w("RoomTaskRepository", "Ignoring non-contiguous repetition "
                    + "result for step " + result.stepId + " at slot " + result.slotIndex);
        }
        List<OccurrenceStep> result = new ArrayList<>();
        for (OccurrenceStepEntity entity : entities)
            result.add(mapper.toDomain(entity, repetitions.get(entity.id)));
        return result;
    }

    private List<Integer> repetitions(String stepId) {
        List<Integer> result = new ArrayList<>();
        for (RepetitionResultEntity value : dao.repetitionResults(stepId)) {
            if (value.slotIndex == result.size()) result.add(value.actualRepetitions);
            else android.util.Log.w("RoomTaskRepository", "Ignoring non-contiguous repetition "
                    + "result for step " + stepId + " at slot " + value.slotIndex);
        }
        return result;
    }

    /** Applies a minimal row diff, so correcting one slot writes only that slot. */
    private void syncRepetitionResults(OccurrenceStep step) {
        List<Integer> desired = step.repetitionProgress == null
                ? java.util.Collections.emptyList()
                : step.repetitionProgress.actualRepetitions;
        List<RepetitionResultEntity> stored = dao.repetitionResults(step.id);
        for (int index = 0; index < desired.size(); index++) {
            int value = desired.get(index);
            if (index >= stored.size() || stored.get(index).slotIndex != index
                    || stored.get(index).actualRepetitions != value)
                dao.putRepetitionResult(new RepetitionResultEntity(step.id, index, value));
        }
        dao.deleteRepetitionResultsFrom(step.id, desired.size());
    }
}
