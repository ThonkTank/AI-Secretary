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
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.ComboObligation;
import de.thonktank.autosecretary.domain.model.ComboDecayEvent;
import de.thonktank.autosecretary.domain.model.TrainingAdjustment;
import de.thonktank.autosecretary.domain.model.TrainingLoadRequest;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;
import de.thonktank.autosecretary.domain.today.TodayStepPositionUpdate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class RoomTaskRepository implements TaskStore {
    private final AppDatabase database;
    private final TaskDao dao;
    private final TaskEntityMapper mapper;
    private final StepFlowEntityMapper flowMapper;
    private final RoomTransactionRunner transactions;
    private final RoomTrainingRepository training;
    private final RoomStepRepository steps;

    public RoomTaskRepository(AppDatabase database) {
        this(database, new TaskEntityMapper());
    }

    RoomTaskRepository(AppDatabase database, TaskEntityMapper mapper) {
        this.database = database;
        this.dao = database.tasks();
        this.mapper = mapper;
        this.flowMapper = new StepFlowEntityMapper();
        this.transactions = new RoomTransactionRunner(database);
        this.training = new RoomTrainingRepository(database, mapper);
        this.steps = new RoomStepRepository(database, mapper);
    }

    @Override public <T> T inTransaction(TransactionRunner.Transaction<T> operation) {
        return transactions.inTransaction(operation);
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
        this.steps.insertTemplates(steps);
        for (TaskStepTemplate step : steps) {
            String owner = ComboProgress.stepOwner(step.id);
            if (dao.combo(owner) == null)
                putCombo(ComboProgress.fresh(owner, step.taskId, ComboProgress.Kind.STEP));
        }
    }

    @Override public void deleteTemplates(TaskId taskId) {
        steps.deleteTemplates(taskId);
    }

    @Override public void deleteTemplate(String id) { steps.deleteTemplate(id); }

    @Override public void updateTrainingTemplate(TaskStepTemplate template) {
        training.updateTrainingTemplate(template);
    }

    @Override public List<TaskStepTemplate> templates(TaskId taskId) {
        return steps.templates(taskId);
    }

    @Override public TaskStepTemplate findTemplate(String id) {
        return steps.findTemplate(id);
    }

    @Override public List<TaskStepTemplate> templatesFor(List<TaskId> taskIds) {
        return steps.templatesFor(taskIds);
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
        this.steps.insertOccurrenceSteps(steps);
    }

    @Override public List<OccurrenceStep> occurrenceSteps(String occurrenceId) {
        return steps.occurrenceSteps(occurrenceId);
    }

    @Override public List<OccurrenceStep> occurrenceStepsFor(List<String> occurrenceIds) {
        return steps.occurrenceStepsFor(occurrenceIds);
    }

    @Override public OccurrenceStep findOccurrenceStep(String id) {
        return steps.findOccurrenceStep(id);
    }

    @Override public void updateOccurrenceStep(OccurrenceStep step) {
        steps.updateOccurrenceStep(step);
    }

    @Override public void deleteOccurrenceStep(String id) { steps.deleteOccurrenceStep(id); }

    @Override public void updateOccurrenceStepPositions(List<TodayStepPositionUpdate> updates) {
        steps.updateOccurrenceStepPositions(updates);
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
        return training.effectiveSetsSince(muscle, start, end);
    }

    @Override public void insertTrainingAdjustment(TrainingAdjustment adjustment) {
        training.insertTrainingAdjustment(adjustment);
    }

    @Override public TrainingAdjustment latestTrainingAdjustment(String templateId) {
        return training.latestTrainingAdjustment(templateId);
    }

    @Override public List<TrainingAdjustment> recentTrainingAdjustments(String templateId,
                                                                        int limit) {
        return training.recentTrainingAdjustments(templateId, limit);
    }

    @Override public void updateTrainingAdjustment(TrainingAdjustment adjustment) {
        training.updateTrainingAdjustment(adjustment);
    }

    @Override public long nextTrainingAuditOrder() {
        return training.nextTrainingAuditOrder();
    }

    @Override public void insertTrainingLoadRequest(TrainingLoadRequest request) {
        training.insertTrainingLoadRequest(request);
    }

    @Override public TrainingLoadRequest openTrainingLoadRequest(String templateId) {
        return training.openTrainingLoadRequest(templateId);
    }

    @Override public List<TrainingLoadRequest> recentTrainingLoadRequests(String templateId,
                                                                          int limit) {
        return training.recentTrainingLoadRequests(templateId, limit);
    }

    @Override public void updateTrainingLoadRequest(TrainingLoadRequest request) {
        training.updateTrainingLoadRequest(request);
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

}
