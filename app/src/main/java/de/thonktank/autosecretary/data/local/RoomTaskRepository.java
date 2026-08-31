package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.AppDatabase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.model.FlowRunResourceSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunStepSnapshot;
import de.thonktank.autosecretary.domain.model.StepFlowRun;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.repository.ApplicationTaskRepository;
import de.thonktank.autosecretary.domain.repository.TransactionalRepository;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.ComboObligation;
import de.thonktank.autosecretary.domain.model.ComboDecayEvent;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TrainingAdjustment;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;
import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.model.TrainingObservation;
import de.thonktank.autosecretary.domain.today.TodayStepPositionUpdate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public final class RoomTaskRepository implements ApplicationTaskRepository {
    private final AppDatabase database;
    private final TaskDao dao;
    private final TaskEntityMapper mapper;
    private final StepFlowEntityMapper flowMapper;

    public RoomTaskRepository(AppDatabase database) {
        this(database, new TaskEntityMapper());
    }

    RoomTaskRepository(AppDatabase database, TaskEntityMapper mapper) {
        this.database = database;
        this.dao = database.tasks();
        this.mapper = mapper;
        this.flowMapper = new StepFlowEntityMapper();
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

    @Override public void updateTrainingTemplate(TaskStepTemplate template) {
        dao.updateTemplate(mapper.toEntity(template));
    }

    @Override public List<TaskStepTemplate> templates(TaskId taskId) {
        List<TaskStepTemplate> result = new ArrayList<>();
        for (TaskStepEntity entity : dao.templates(taskId.value)) result.add(mapper.toDomain(entity));
        return result;
    }

    @Override public TaskStepTemplate findTemplate(String id) {
        TaskStepEntity entity = dao.template(id);
        return entity == null ? null : mapper.toDomain(entity);
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

    @Override public TaskScheduleEntry findScheduleEntry(String id) {
        TaskScheduleEntity value = dao.scheduleEntry(id);
        return value == null ? null : mapper.toDomain(value);
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

    @Override public void insertOccurrence(Occurrence occurrence) {
        dao.insertOccurrence(mapper.toEntity(occurrence));
    }

    @Override public void updateOccurrence(Occurrence occurrence) {
        dao.updateOccurrence(mapper.toEntity(occurrence));
    }

    @Override public void deleteOccurrence(String id) { dao.deleteOccurrence(id); }

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

    @Override public List<Occurrence> openOccurrences(TaskId taskId) {
        return mapOccurrences(dao.openOccurrencesForTask(taskId.value,
                OccurrenceState.OPEN.storageCode()));
    }

    @Override public Occurrence openOccurrence(TaskId taskId, TaskSlot slot) {
        OccurrenceEntity entity = dao.openForTaskSlot(taskId.value, slot.storageCode,
                OccurrenceState.OPEN.storageCode());
        return entity == null ? null : mapper.toDomain(entity);
    }

    @Override public Occurrence openOccurrence(TaskId taskId) {
        OccurrenceEntity entity = dao.openForTask(taskId.value, OccurrenceState.OPEN.storageCode());
        return entity == null ? null : mapper.toDomain(entity);
    }

    @Override public List<Occurrence> openOccurrences() {
        return mapOccurrences(dao.occurrencesByState(OccurrenceState.OPEN.storageCode()));
    }

    @Override public List<Occurrence> openOccurrences(TaskSlot slot) {
        return mapOccurrences(dao.occurrencesByStateAndSlot(
                OccurrenceState.OPEN.storageCode(), slot.storageCode));
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

    @Override public void deleteOccurrenceStep(String id) { dao.deleteOccurrenceStep(id); }

    @Override public void updateOccurrenceStepPositions(List<TodayStepPositionUpdate> updates) {
        for (TodayStepPositionUpdate update : updates)
            dao.updateOccurrenceStepPosition(update.stepId, update.position);
    }

    @Override public void assignRewardBookings(String occurrenceStepId, String occurrenceId) {
        List<RewardAssignmentEntity> assignments = new ArrayList<>();
        for (String bookingId : dao.rewardBookingIds(occurrenceStepId))
            assignments.add(new RewardAssignmentEntity(bookingId, occurrenceId));
        if (!assignments.isEmpty()) dao.putRewardAssignments(assignments);
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
                booking.comboPointDelta, booking.bookedOn.toString(), booking.reversesBookingId,
                booking.plannedXp));
    }

    @Override public List<RewardBooking> rewardBookings(String occurrenceId) {
        return mapBookings(dao.rewardBookings(occurrenceId));
    }

    @Override public List<RewardBooking> rewardBookings(List<String> occurrenceIds) {
        if (occurrenceIds.isEmpty()) return new ArrayList<>();
        return mapBookings(dao.rewardBookings(occurrenceIds));
    }

    @Override public List<CapacityResource> capacityResources() {
        List<CapacityResource> result = new ArrayList<>();
        for (CapacityResourceEntity value : dao.capacityResources())
            result.add(flowMapper.toDomain(value));
        return result;
    }

    @Override public CapacityResource findCapacityResource(String id) {
        CapacityResourceEntity value = dao.capacityResource(id);
        return value == null ? null : flowMapper.toDomain(value);
    }

    @Override public void putCapacityResource(CapacityResource resource) {
        CapacityResourceEntity value = flowMapper.toEntity(resource);
        if (dao.insertCapacityResource(value) == -1L) dao.updateCapacityResource(value);
    }

    @Override public void deleteCapacityResource(String id) {
        dao.deleteCapacityResource(id);
    }

    @Override public List<StepTransition> stepTransitions(TaskId taskId) {
        List<StepTransition> result = new ArrayList<>();
        for (StepTransitionEntity value : dao.stepTransitions(taskId.value))
            result.add(flowMapper.toDomain(value));
        return result;
    }

    @Override public List<StepResourceLease> stepResourceLeases(TaskId taskId) {
        List<StepResourceLease> result = new ArrayList<>();
        for (StepResourceLeaseEntity value : dao.stepResourceLeases(taskId.value))
            result.add(flowMapper.toDomain(value));
        return result;
    }

    @Override public void replaceStepFlow(TaskId taskId, List<StepTransition> transitions,
                                          List<StepResourceLease> leases) {
        dao.deleteStepResourceLeases(taskId.value);
        dao.deleteStepTransitions(taskId.value);
        List<StepTransitionEntity> transitionEntities = new ArrayList<>();
        for (StepTransition value : transitions)
            transitionEntities.add(flowMapper.toEntity(value));
        if (!transitionEntities.isEmpty()) dao.putStepTransitions(transitionEntities);
        List<StepResourceLeaseEntity> leaseEntities = new ArrayList<>();
        for (StepResourceLease value : leases) leaseEntities.add(flowMapper.toEntity(value));
        if (!leaseEntities.isEmpty()) dao.putStepResourceLeases(leaseEntities);
    }

    @Override public void updateStepTransition(StepTransition transition) {
        dao.putStepTransition(flowMapper.toEntity(transition));
    }

    @Override public boolean insertFlowRun(FlowRunSnapshot snapshot) {
        return database.runInTransaction(() -> {
            if (dao.insertStepFlowRun(flowMapper.toEntity(snapshot.run)) == -1L) return false;
            List<FlowRunStepEntity> steps = new ArrayList<>();
            for (FlowRunStepSnapshot value : snapshot.steps)
                steps.add(flowMapper.toEntity(value));
            dao.insertFlowRunSteps(steps);
            List<FlowRunResourceEntity> resources = new ArrayList<>();
            for (FlowRunResourceSnapshot value : snapshot.resources)
                resources.add(flowMapper.toEntity(value));
            if (!resources.isEmpty()) dao.insertFlowRunResources(resources);
            return true;
        });
    }

    @Override public void updateFlowRun(StepFlowRun run) {
        dao.updateStepFlowRun(flowMapper.toEntity(run));
    }

    @Override public StepFlowRun findFlowRun(String id) {
        StepFlowRunEntity value = dao.stepFlowRun(id);
        return value == null ? null : flowMapper.toDomain(value);
    }

    @Override public StepFlowRun findFlowRunBySourceKey(String sourceKey) {
        StepFlowRunEntity value = dao.stepFlowRunBySourceKey(sourceKey);
        return value == null ? null : flowMapper.toDomain(value);
    }

    @Override public List<StepFlowRun> activeFlowRuns() {
        return mapFlowRuns(dao.activeStepFlowRuns());
    }

    @Override public List<StepFlowRun> activeFlowRuns(TaskId taskId) {
        return mapFlowRuns(dao.activeStepFlowRuns(taskId.value));
    }

    @Override public List<FlowRunStepSnapshot> flowRunSteps(String runId) {
        List<FlowRunStepSnapshot> result = new ArrayList<>();
        for (FlowRunStepEntity value : dao.flowRunSteps(runId))
            result.add(flowMapper.toDomain(value));
        return result;
    }

    @Override public List<FlowRunStepSnapshot> flowRunStepsFor(List<String> runIds) {
        if (runIds.isEmpty()) return new ArrayList<>();
        List<FlowRunStepSnapshot> result = new ArrayList<>();
        for (FlowRunStepEntity value : dao.flowRunStepsFor(runIds))
            result.add(flowMapper.toDomain(value));
        return result;
    }

    @Override public void updateFlowRunStep(FlowRunStepSnapshot step) {
        dao.updateFlowRunStep(flowMapper.toEntity(step));
    }

    @Override public List<FlowRunResourceSnapshot> flowRunResources(String runId) {
        List<FlowRunResourceSnapshot> result = new ArrayList<>();
        for (FlowRunResourceEntity value : dao.flowRunResources(runId))
            result.add(flowMapper.toDomain(value));
        return result;
    }

    @Override public List<FlowRunResourceSnapshot> flowRunResourcesFor(List<String> runIds) {
        if (runIds.isEmpty()) return new ArrayList<>();
        List<FlowRunResourceSnapshot> result = new ArrayList<>();
        for (FlowRunResourceEntity value : dao.flowRunResourcesFor(runIds))
            result.add(flowMapper.toDomain(value));
        return result;
    }

    @Override public List<FlowRunResourceSnapshot> consumingFlowResources() {
        List<FlowRunResourceSnapshot> result = new ArrayList<>();
        for (FlowRunResourceEntity value : dao.consumingFlowResources())
            result.add(flowMapper.toDomain(value));
        return result;
    }

    @Override public void updateFlowRunResource(FlowRunResourceSnapshot resource) {
        dao.updateFlowRunResource(flowMapper.toEntity(resource));
    }

    private List<StepFlowRun> mapFlowRuns(List<StepFlowRunEntity> values) {
        List<StepFlowRun> result = new ArrayList<>();
        for (StepFlowRunEntity value : values) result.add(flowMapper.toDomain(value));
        return result;
    }

    @Override public List<ComboObligation> comboObligations() {
        List<ComboObligation> result = new ArrayList<>();
        for (ComboObligationEntity value : dao.comboObligations())
            result.add(new ComboObligation(value.id, value.ownerId, TaskId.of(value.taskId),
                    ComboProgress.Kind.valueOf(value.kind), TaskSlot.fromStorage(value.slot),
                    LocalDate.parse(value.scheduledOn), value.occurrenceId,
                    ComboObligation.State.valueOf(value.state),
                    value.resolvedOn == null ? null : LocalDate.parse(value.resolvedOn)));
        return result;
    }

    @Override public void insertComboObligations(List<ComboObligation> obligations) {
        List<ComboObligationEntity> entities = new ArrayList<>();
        for (ComboObligation value : obligations) entities.add(obligation(value));
        if (!entities.isEmpty()) dao.insertComboObligations(entities);
    }

    @Override public void updateComboObligation(ComboObligation obligation) {
        dao.updateComboObligation(obligation(obligation));
    }

    @Override public ComboDecayEvent comboDecayEvent(String ownerId, LocalDate eventOn) {
        ComboDecayEventEntity value = dao.comboDecayEvent(ownerId, eventOn.toString());
        return value == null ? null : new ComboDecayEvent(value.ownerId,
                LocalDate.parse(value.eventOn), value.bookingId);
    }

    @Override public void insertComboDecayEvent(ComboDecayEvent event) {
        dao.insertComboDecayEvent(new ComboDecayEventEntity(event.ownerId,
                event.eventOn.toString(), event.bookingId));
    }

    @Override public double effectiveSetsSince(TrainingMuscleGroup muscle, LocalDate start,
                                               LocalDate end) {
        if (muscle == null) return 0;
        String first = start.toString();
        String last = end.toString();
        return dao.effectivePrimarySets(muscle.name(), first, last)
                + dao.effectiveSecondarySets(muscle.name(), first, last) * 0.5;
    }

    @Override public void insertTrainingAdjustment(TrainingAdjustment adjustment) {
        dao.insertTrainingAdjustment(adjustment(adjustment));
    }

    @Override public TrainingAdjustment latestTrainingAdjustment(String templateId) {
        TrainingAdjustmentEntity value = dao.latestTrainingAdjustment(templateId);
        return value == null ? null : adjustment(value);
    }

    @Override public void updateTrainingAdjustment(TrainingAdjustment adjustment) {
        dao.updateTrainingAdjustment(adjustment(adjustment));
    }

    private static TrainingAdjustmentEntity adjustment(TrainingAdjustment value) {
        return new TrainingAdjustmentEntity(value.id, value.templateId,
                value.sourceOccurrenceStepId, value.reason.name(), value.before.sets,
                value.before.repetitions, value.beforeLoad.mode.name(),
                value.beforeLoad.unit.name(), value.beforeLoad.milliUnits, value.after.sets,
                value.after.repetitions, value.afterLoad.mode.name(), value.afterLoad.unit.name(),
                value.afterLoad.milliUnits, value.createdOn.toString(), value.state.name());
    }

    private static TrainingAdjustment adjustment(TrainingAdjustmentEntity value) {
        return new TrainingAdjustment(value.id, value.templateId, value.sourceOccurrenceStepId,
                de.thonktank.autosecretary.domain.training.TrainingAdaptationEngine.Reason
                        .valueOf(value.reason),
                (StepAmount.SetsReps) StepAmount.setsReps(value.beforeSets, value.beforeReps),
                ResistanceLoad.restore(value.beforeLoadMode, value.beforeLoadUnit,
                        value.beforeLoadMilli),
                (StepAmount.SetsReps) StepAmount.setsReps(value.afterSets, value.afterReps),
                ResistanceLoad.restore(value.afterLoadMode, value.afterLoadUnit,
                        value.afterLoadMilli), LocalDate.parse(value.createdOn),
                TrainingAdjustment.State.valueOf(value.state));
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try { return Enum.valueOf(type, value); }
        catch (RuntimeException invalid) { return fallback; }
    }

    private static ComboObligationEntity obligation(ComboObligation value) {
        return new ComboObligationEntity(value.id, value.ownerId, value.taskId.value,
                value.kind.name(), value.slot.storageCode, value.scheduledOn.toString(),
                value.occurrenceId, value.state.name(),
                value.resolvedOn == null ? null : value.resolvedOn.toString());
    }

    private static List<RewardBooking> mapBookings(List<RewardBookingEntity> entities) {
        List<RewardBooking> result = new ArrayList<>();
        for (RewardBookingEntity value : entities)
            result.add(new RewardBooking(value.id, value.transactionId, value.occurrenceId,
                    value.occurrenceStepId, value.ownerId, RewardBooking.Kind.valueOf(value.kind),
                    RewardBooking.Target.valueOf(value.target), value.xpDelta,
                    value.comboPointDelta, LocalDate.parse(value.bookedOn),
                    value.reversesBookingId, value.plannedXp));
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
        Map<String, List<SetResult>> results = new LinkedHashMap<>();
        for (OccurrenceStepEntity entity : entities) {
            ids.add(entity.id);
            results.put(entity.id, new ArrayList<>());
        }
        for (RepetitionResultEntity result : dao.repetitionResultsFor(ids)) {
            List<SetResult> values = results.get(result.stepId);
            if (values == null) continue;
            if (result.slotIndex == values.size()) values.add(setResult(result));
            else android.util.Log.w("RoomTaskRepository", "Ignoring non-contiguous repetition "
                    + "result for step " + result.stepId + " at slot " + result.slotIndex);
        }
        List<OccurrenceStep> result = new ArrayList<>();
        for (OccurrenceStepEntity entity : entities)
            result.add(mapper.toDomain(entity, results.get(entity.id)));
        return result;
    }

    private List<SetResult> repetitions(String stepId) {
        List<SetResult> result = new ArrayList<>();
        for (RepetitionResultEntity value : dao.repetitionResults(stepId)) {
            if (value.slotIndex == result.size()) result.add(setResult(value));
            else android.util.Log.w("RoomTaskRepository", "Ignoring non-contiguous repetition "
                    + "result for step " + stepId + " at slot " + value.slotIndex);
        }
        return result;
    }

    /** Applies a minimal row diff, so correcting one slot writes only that slot. */
    private void syncRepetitionResults(OccurrenceStep step) {
        List<SetResult> desired = step.repetitionProgress == null
                ? java.util.Collections.emptyList()
                : step.repetitionProgress.results;
        List<RepetitionResultEntity> stored = dao.repetitionResults(step.id);
        for (int index = 0; index < desired.size(); index++) {
            SetResult value = desired.get(index);
            if (index >= stored.size() || stored.get(index).slotIndex != index
                    || !value.equals(setResult(stored.get(index))))
                dao.putRepetitionResult(entity(step.id, index, value));
        }
        dao.deleteRepetitionResultsFrom(step.id, desired.size());
    }

    private static SetResult setResult(RepetitionResultEntity value) {
        ResistanceLoad load = ResistanceLoad.restore(value.loadMode, value.loadUnit,
                value.loadMilli);
        TrainingObservation.Origin origin = enumValue(TrainingObservation.Origin.class,
                value.source, TrainingObservation.Origin.LEGACY);
        TrainingObservation.Safety safety = enumValue(TrainingObservation.Safety.class,
                value.safetyFlag, TrainingObservation.Safety.NONE);
        TrainingObservation observation = load.mode == ResistanceLoad.Mode.UNSPECIFIED
                && value.rir == null && origin == TrainingObservation.Origin.LEGACY
                && safety == TrainingObservation.Safety.NONE ? null
                : new TrainingObservation(load, value.rir, safety, origin);
        return SetResult.restore(value.actualRepetitions, observation);
    }

    private static RepetitionResultEntity entity(String stepId, int index, SetResult value) {
        if (value.training == null)
            return new RepetitionResultEntity(stepId, index, value.repetitions);
        TrainingObservation training = value.training;
        return new RepetitionResultEntity(stepId, index, value.repetitions,
                training.load.mode.name(), training.load.unit.name(), training.load.milliUnits,
                training.rir, training.origin.name(), training.safety.name());
    }
}
