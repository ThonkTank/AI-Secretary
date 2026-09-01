package de.thonktank.autosecretary.testing;

import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.ComboObligation;
import de.thonktank.autosecretary.domain.model.ComboDecayEvent;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TrainingAdjustment;
import de.thonktank.autosecretary.domain.model.TrainingLoadRequest;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.FlowRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;
import de.thonktank.autosecretary.domain.today.TodayStepPositionUpdate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Cross-use-case execution store for completion acceptance tests. Management use cases use
 * focused schedule/step doubles and cannot reach this aggregate by accident.
 */
public final class InMemoryExecutionRepository {
    public final CatalogRepository catalog = adapter(CatalogRepository.class);
    public final StepRepository steps = adapter(StepRepository.class);
    public final TodayRepository today = adapter(TodayRepository.class);
    public final FlowRepository flows = adapter(FlowRepository.class);
    public final TrainingRepository training = adapter(TrainingRepository.class);
    public final TransactionRunner transactions = adapter(TransactionRunner.class);
    private Map<TaskId, Task> tasks = new LinkedHashMap<>();
    private Map<String, TaskStepTemplate> templates = new LinkedHashMap<>();
    private Map<String, TaskScheduleEntry> schedule = new LinkedHashMap<>();
    private Map<String, Occurrence> occurrences = new LinkedHashMap<>();
    private Map<String, OccurrenceStep> occurrenceSteps = new LinkedHashMap<>();
    private Map<String, ComboProgress> combos = new LinkedHashMap<>();
    private Map<String, RewardBooking> bookings = new LinkedHashMap<>();
    private Map<String, String> rewardAssignments = new LinkedHashMap<>();
    private Map<String, ComboObligation> comboObligations = new LinkedHashMap<>();
    private Map<String, ComboDecayEvent> comboDecayEvents = new LinkedHashMap<>();
    private Map<String, TrainingAdjustment> trainingAdjustments = new LinkedHashMap<>();
    private Map<String, TrainingLoadRequest> trainingLoadRequests = new LinkedHashMap<>();
    private boolean failTrainingAdjustmentInsert;
    private int xp;

    private <T> T adapter(Class<T> port) {
        Object value = Proxy.newProxyInstance(port.getClassLoader(), new Class<?>[]{port},
                (proxy, method, arguments) -> invokePort(port, proxy, method, arguments));
        return port.cast(value);
    }

    private Object invokePort(Class<?> port, Object proxy, Method method, Object[] arguments)
            throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            if ("toString".equals(method.getName())) return port.getSimpleName() + "Fake";
            if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
            if ("equals".equals(method.getName())) return proxy == arguments[0];
        }
        try {
            Method implementation = getClass().getMethod(method.getName(), method.getParameterTypes());
            return implementation.invoke(this, arguments);
        } catch (NoSuchMethodException missing) {
            if (List.class.isAssignableFrom(method.getReturnType())) return new ArrayList<>();
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == void.class)
                throw new UnsupportedOperationException(port.getSimpleName() + '.'
                        + method.getName() + " is not supported by this test fixture");
            return null;
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    public synchronized <T> T inTransaction(TransactionRunner.Transaction<T> operation) {
        Snapshot before = snapshot();
        try {
            return operation.execute();
        } catch (RuntimeException | Error failure) {
            restore(before);
            throw failure;
        }
    }

    public synchronized void insertTask(Task task) { tasks.put(task.id, task); }
    public synchronized void updateTask(Task task) { tasks.put(task.id, task); }
    public synchronized Task findTask(TaskId id) { return tasks.get(id); }
    public synchronized List<Task> activeTasks() {
        List<Task> result = new ArrayList<>();
        for (Task task : tasks.values()) if (!task.archived && !task.conditionDone) result.add(task);
        return result;
    }
    public synchronized List<Task> allTasks() { return new ArrayList<>(tasks.values()); }
    public synchronized void deleteTask(TaskId id) {
        tasks.remove(id);
        deleteTemplates(id);
        schedule.values().removeIf(value -> value.taskId.equals(id));
        Set<String> occurrenceIds = new HashSet<>();
        occurrences.values().removeIf(value -> {
            boolean remove = value.taskId.equals(id);
            if (remove) occurrenceIds.add(value.id);
            return remove;
        });
        occurrenceSteps.values().removeIf(value -> occurrenceIds.contains(value.occurrenceId));
        bookings.values().removeIf(value -> occurrenceIds.contains(value.occurrenceId));
        rewardAssignments.entrySet().removeIf(value -> occurrenceIds.contains(value.getValue())
                || !bookings.containsKey(value.getKey()));
        combos.values().removeIf(value -> value.taskId.equals(id));
        comboObligations.values().removeIf(value -> value.taskId.equals(id));
    }

    public synchronized void insertTemplates(List<TaskStepTemplate> values) {
        for (TaskStepTemplate value : values) templates.put(value.id, value);
    }
    public synchronized void deleteTemplates(TaskId taskId) {
        templates.values().removeIf(value -> value.taskId.equals(taskId));
    }
    public synchronized void deleteTemplate(String id) { templates.remove(id); }
    public synchronized void updateTemplate(TaskStepTemplate template) {
        templates.put(template.id, template);
    }
    public synchronized List<TaskStepTemplate> templates(TaskId taskId) {
        List<TaskStepTemplate> result = new ArrayList<>();
        for (TaskStepTemplate value : templates.values())
            if (value.taskId.equals(taskId)) result.add(value);
        result.sort(Comparator.comparingInt(value -> value.position));
        return result;
    }
    public synchronized TaskStepTemplate findTemplate(String id) {
        return templates.get(id);
    }
    public synchronized List<TaskStepTemplate> templatesFor(List<TaskId> taskIds) {
        Set<TaskId> selected = new HashSet<>(taskIds);
        List<TaskStepTemplate> result = new ArrayList<>();
        for (TaskStepTemplate value : templates.values())
            if (selected.contains(value.taskId)) result.add(value);
        result.sort(Comparator.comparing((TaskStepTemplate value) -> value.taskId.value)
                .thenComparingInt(value -> value.position));
        return result;
    }
    public synchronized void putScheduleEntries(List<TaskScheduleEntry> values) {
        for (TaskScheduleEntry value : values) schedule.put(value.id, value);
    }
    public synchronized void deleteScheduleEntry(String id) { schedule.remove(id); }
    public synchronized List<TaskScheduleEntry> scheduleEntries() {
        List<TaskScheduleEntry> result = new ArrayList<>(schedule.values());
        result.sort(scheduleOrder());
        return result;
    }
    public synchronized List<TaskScheduleEntry> scheduleEntries(TaskId taskId) {
        List<TaskScheduleEntry> result = new ArrayList<>();
        for (TaskScheduleEntry value : schedule.values())
            if (value.taskId.equals(taskId)) result.add(value);
        result.sort(scheduleOrder());
        return result;
    }
    public synchronized TaskScheduleEntry findScheduleEntry(String id) {
        return schedule.get(id);
    }
    public synchronized List<TaskScheduleEntry> scheduleEntries(TaskSlot slot) {
        List<TaskScheduleEntry> result = new ArrayList<>();
        for (TaskScheduleEntry value : schedule.values())
            if (value.slot == slot) result.add(value);
        result.sort(scheduleOrder());
        return result;
    }
    public synchronized List<TaskScheduleEntry> scheduleEntriesFor(List<TaskId> taskIds) {
        Set<TaskId> selected = new HashSet<>(taskIds);
        List<TaskScheduleEntry> result = new ArrayList<>();
        for (TaskScheduleEntry value : schedule.values())
            if (selected.contains(value.taskId)) result.add(value);
        result.sort(scheduleOrder());
        return result;
    }

    public synchronized void insertOccurrence(Occurrence occurrence) {
        occurrences.putIfAbsent(occurrence.id, occurrence);
    }
    public synchronized void updateOccurrence(Occurrence occurrence) {
        occurrences.put(occurrence.id, occurrence);
    }
    public synchronized void deleteOccurrence(String id) {
        occurrences.remove(id);
        occurrenceSteps.values().removeIf(value -> value.occurrenceId.equals(id));
    }
    public synchronized Occurrence findOccurrence(String id) {
        return occurrences.get(id);
    }
    public synchronized Occurrence findOccurrence(TaskId taskId, LocalDate scheduledOn,
                                                            TaskSlot slot) {
        for (Occurrence value : occurrences.values())
            if (value.taskId.equals(taskId) && value.scheduledOn.equals(scheduledOn)
                    && value.slot == slot) return value;
        return null;
    }
    public synchronized List<Occurrence> openOccurrences(TaskId taskId,
                                                                   LocalDate scheduledOn) {
        List<Occurrence> result = new ArrayList<>();
        for (Occurrence value : occurrences.values())
            if (value.taskId.equals(taskId) && value.scheduledOn.equals(scheduledOn)
                    && value.state == OccurrenceState.OPEN) result.add(value);
        return result;
    }
    public synchronized List<Occurrence> openOccurrences(TaskId taskId) {
        List<Occurrence> result = new ArrayList<>();
        for (Occurrence value : occurrences.values())
            if (value.taskId.equals(taskId) && value.state == OccurrenceState.OPEN)
                result.add(value);
        result.sort(Comparator.comparing((Occurrence value) -> value.scheduledOn)
                .thenComparingInt(value -> value.slot.rank));
        return result;
    }
    public synchronized Occurrence openOccurrence(TaskId taskId, TaskSlot slot) {
        for (Occurrence value : openOccurrences(taskId))
            if (value.slot == slot) return value;
        return null;
    }
    public synchronized Occurrence openOccurrence(TaskId taskId) {
        for (Occurrence value : occurrences.values())
            if (value.taskId.equals(taskId) && value.state == OccurrenceState.OPEN) return value;
        return null;
    }
    public synchronized List<Occurrence> openOccurrences() {
        return byState(OccurrenceState.OPEN);
    }
    public synchronized List<Occurrence> openOccurrences(TaskSlot slot) {
        List<Occurrence> result = new ArrayList<>();
        for (Occurrence value : occurrences.values())
            if (value.slot == slot && value.state == OccurrenceState.OPEN) result.add(value);
        result.sort(Comparator.comparingInt((Occurrence value) -> value.sortOrder)
                .thenComparing(value -> value.scheduledOn).thenComparing(value -> value.id));
        return result;
    }
    public synchronized List<Occurrence> allOccurrences() {
        return new ArrayList<>(occurrences.values());
    }
    public synchronized List<Occurrence> occurrences(TaskId taskId) {
        List<Occurrence> result = new ArrayList<>();
        for (Occurrence value : occurrences.values())
            if (value.taskId.equals(taskId)) result.add(value);
        return result;
    }
    public synchronized Occurrence earliestOpenOccurrence(TaskId taskId) {
        return occurrences(taskId).stream().filter(value -> value.state == OccurrenceState.OPEN)
                .min(Comparator.comparing(value -> value.scheduledOn)).orElse(null);
    }
    public synchronized Occurrence latestCompletedOccurrence(TaskId taskId) {
        return occurrences(taskId).stream()
                .filter(value -> value.state.isHarvested())
                .max(Comparator.comparing((Occurrence value) -> value.completedOn)
                        .thenComparing(value -> value.scheduledOn)).orElse(null);
    }
    public synchronized List<Occurrence> completedOccurrences(LocalDate date) {
        List<Occurrence> result = new ArrayList<>();
        for (Occurrence value : occurrences.values())
            if (value.state.isHarvested() && date.equals(value.completedOn))
                result.add(value);
        return result;
    }
    public synchronized void insertOccurrenceSteps(List<OccurrenceStep> values) {
        for (OccurrenceStep value : values) occurrenceSteps.put(value.id, value);
    }
    public synchronized List<OccurrenceStep> occurrenceSteps(String occurrenceId) {
        List<OccurrenceStep> result = new ArrayList<>();
        for (OccurrenceStep value : occurrenceSteps.values())
            if (value.occurrenceId.equals(occurrenceId)) result.add(value);
        result.sort(Comparator.comparingInt(value -> value.position));
        return result;
    }
    public synchronized List<OccurrenceStep> occurrenceStepsFor(
            List<String> occurrenceIds) {
        Set<String> selected = new HashSet<>(occurrenceIds);
        List<OccurrenceStep> result = new ArrayList<>();
        for (OccurrenceStep value : occurrenceSteps.values())
            if (selected.contains(value.occurrenceId)) result.add(value);
        result.sort(Comparator.comparing((OccurrenceStep value) -> value.occurrenceId)
                .thenComparingInt(value -> value.position));
        return result;
    }
    public synchronized OccurrenceStep findOccurrenceStep(String id) {
        return occurrenceSteps.get(id);
    }
    public synchronized void updateOccurrenceStep(OccurrenceStep step) {
        occurrenceSteps.put(step.id, step);
    }
    public synchronized void deleteOccurrenceStep(String id) { occurrenceSteps.remove(id); }
    public synchronized void updateOccurrenceStepPositions(
            List<TodayStepPositionUpdate> updates) {
        for (TodayStepPositionUpdate update : updates) {
            OccurrenceStep step = occurrenceSteps.get(update.stepId);
            if (step != null)
                occurrenceSteps.put(step.id, step.relocate(step.occurrenceId, update.position));
        }
    }
    public synchronized void assignRewardBookings(String occurrenceStepId,
                                                             String occurrenceId) {
        for (RewardBooking value : bookings.values())
            if (occurrenceStepId.equals(value.occurrenceStepId))
                rewardAssignments.put(value.id, occurrenceId);
    }

    public synchronized int xp() { return xp; }
    public synchronized void setXp(int value) { xp = Math.max(0, value); }
    public synchronized ComboProgress combo(String ownerId) { return combos.get(ownerId); }
    public synchronized void putCombo(ComboProgress combo) {
        combos.put(combo.ownerId, combo);
    }
    public synchronized List<ComboProgress> combos() {
        return new ArrayList<>(combos.values());
    }

    public synchronized void insertRewardBooking(RewardBooking booking) {
        if (bookings.containsKey(booking.id))
            throw new IllegalStateException("Duplicate reward booking " + booking.id);
        if (booking.reversesBookingId != null)
            for (RewardBooking value : bookings.values())
                if (booking.reversesBookingId.equals(value.reversesBookingId))
                    throw new IllegalStateException("Reward booking already reversed");
        bookings.put(booking.id, booking);
    }
    public synchronized List<RewardBooking> rewardBookings(String occurrenceId) {
        List<RewardBooking> result = new ArrayList<>();
        for (RewardBooking value : bookings.values())
            if (effectiveOccurrence(value).equals(occurrenceId))
                result.add(project(value, occurrenceId));
        result.sort(bookingOrder());
        return result;
    }
    public synchronized List<RewardBooking> rewardBookings(
            List<String> occurrenceIds) {
        Set<String> selected = new HashSet<>(occurrenceIds);
        List<RewardBooking> result = new ArrayList<>();
        for (RewardBooking value : bookings.values())
            if (selected.contains(effectiveOccurrence(value)))
                result.add(project(value, effectiveOccurrence(value)));
        result.sort(bookingOrder());
        return result;
    }

    public synchronized List<ComboObligation> comboObligations() {
        return new ArrayList<>(comboObligations.values());
    }

    public synchronized void insertComboObligations(List<ComboObligation> values) {
        for (ComboObligation value : values) comboObligations.putIfAbsent(value.id, value);
    }

    public synchronized void updateComboObligation(ComboObligation value) {
        if (comboObligations.containsKey(value.id)) comboObligations.put(value.id, value);
    }

    public synchronized ComboDecayEvent comboDecayEvent(String ownerId,
                                                                   LocalDate eventOn) {
        return comboDecayEvents.get(decayKey(ownerId, eventOn));
    }

    public synchronized void insertComboDecayEvent(ComboDecayEvent event) {
        String key = decayKey(event.ownerId, event.eventOn);
        if (comboDecayEvents.putIfAbsent(key, event) != null)
            throw new IllegalStateException("Duplicate combo decay event " + key);
    }

    public synchronized double effectiveSetsSince(TrainingMuscleGroup muscle,
                                                             LocalDate start, LocalDate end) {
        return 0.0;
    }

    public synchronized void insertTrainingAdjustment(TrainingAdjustment adjustment) {
        if (failTrainingAdjustmentInsert) {
            failTrainingAdjustmentInsert = false;
            throw new IllegalStateException("Injected training adjustment failure");
        }
        trainingAdjustments.put(adjustment.id, adjustment);
    }

    public synchronized void failNextTrainingAdjustmentInsert() {
        failTrainingAdjustmentInsert = true;
    }

    public synchronized TrainingAdjustment latestTrainingAdjustment(String templateId) {
        TrainingAdjustment latest = null;
        for (TrainingAdjustment value : trainingAdjustments.values()) {
            if (!templateId.equals(value.templateId)) continue;
            if (latest == null || value.auditOrder > latest.auditOrder) latest = value;
        }
        return latest;
    }

    public synchronized List<TrainingAdjustment> recentTrainingAdjustments(
            String templateId, int limit) {
        List<TrainingAdjustment> result = new ArrayList<>();
        for (TrainingAdjustment value : trainingAdjustments.values())
            if (templateId.equals(value.templateId)) result.add(value);
        result.sort(Comparator.comparingLong((TrainingAdjustment value) -> value.auditOrder)
                .reversed());
        return new ArrayList<>(result.subList(0, Math.min(Math.max(limit, 0), result.size())));
    }

    public synchronized void updateTrainingAdjustment(TrainingAdjustment adjustment) {
        trainingAdjustments.put(adjustment.id, adjustment);
    }

    public synchronized long nextTrainingAuditOrder() {
        long maximum = 0;
        for (TrainingAdjustment value : trainingAdjustments.values())
            maximum = Math.max(maximum, value.auditOrder);
        for (TrainingLoadRequest value : trainingLoadRequests.values())
            maximum = Math.max(maximum, value.auditOrder);
        return maximum + 1;
    }

    public synchronized void insertTrainingLoadRequest(TrainingLoadRequest request) {
        if (openTrainingLoadRequest(request.templateId) != null)
            throw new IllegalStateException("An open training load request already exists");
        trainingLoadRequests.put(request.id, request);
    }

    public synchronized TrainingLoadRequest openTrainingLoadRequest(String templateId) {
        TrainingLoadRequest latest = null;
        for (TrainingLoadRequest value : trainingLoadRequests.values()) {
            if (!templateId.equals(value.templateId)
                    || value.state != TrainingLoadRequest.State.OPEN) continue;
            if (latest == null || value.auditOrder > latest.auditOrder) latest = value;
        }
        return latest;
    }

    public synchronized List<TrainingLoadRequest> recentTrainingLoadRequests(
            String templateId, int limit) {
        List<TrainingLoadRequest> result = new ArrayList<>();
        for (TrainingLoadRequest value : trainingLoadRequests.values())
            if (templateId.equals(value.templateId)) result.add(value);
        result.sort(Comparator.comparingLong((TrainingLoadRequest value) -> value.auditOrder)
                .reversed());
        return new ArrayList<>(result.subList(0, Math.min(Math.max(limit, 0), result.size())));
    }

    public synchronized void updateTrainingLoadRequest(TrainingLoadRequest request) {
        trainingLoadRequests.put(request.id, request);
    }

    private List<Occurrence> byState(OccurrenceState state) {
        List<Occurrence> result = new ArrayList<>();
        for (Occurrence value : occurrences.values()) if (value.state == state) result.add(value);
        return result;
    }

    private static Comparator<RewardBooking> bookingOrder() {
        return Comparator.comparing((RewardBooking value) -> value.bookedOn)
                .thenComparing(value -> value.id);
    }

    private String effectiveOccurrence(RewardBooking booking) {
        return rewardAssignments.getOrDefault(booking.id, booking.occurrenceId);
    }

    private static RewardBooking project(RewardBooking value, String occurrenceId) {
        return new RewardBooking(value.id, value.transactionId, occurrenceId,
                value.occurrenceStepId, value.ownerId, value.kind, value.target,
                value.xpDelta, value.comboPointDelta, value.bookedOn, value.reversesBookingId,
                value.plannedXp);
    }

    private static Comparator<TaskScheduleEntry> scheduleOrder() {
        return Comparator.comparingInt((TaskScheduleEntry value) -> value.slot.rank)
                .thenComparingLong(value -> value.displayOrder).thenComparing(value -> value.id);
    }

    private Snapshot snapshot() {
        return new Snapshot(new LinkedHashMap<>(tasks), new LinkedHashMap<>(templates),
                new LinkedHashMap<>(schedule), new LinkedHashMap<>(occurrences),
                new LinkedHashMap<>(occurrenceSteps),
                new LinkedHashMap<>(combos), new LinkedHashMap<>(bookings),
                new LinkedHashMap<>(rewardAssignments),
                new LinkedHashMap<>(comboObligations),
                new LinkedHashMap<>(comboDecayEvents),
                new LinkedHashMap<>(trainingAdjustments),
                new LinkedHashMap<>(trainingLoadRequests), xp);
    }

    private void restore(Snapshot value) {
        tasks = value.tasks;
        templates = value.templates;
        schedule = value.schedule;
        occurrences = value.occurrences;
        occurrenceSteps = value.occurrenceSteps;
        combos = value.combos;
        bookings = value.bookings;
        rewardAssignments = value.rewardAssignments;
        comboObligations = value.comboObligations;
        comboDecayEvents = value.comboDecayEvents;
        trainingAdjustments = value.trainingAdjustments;
        trainingLoadRequests = value.trainingLoadRequests;
        xp = value.xp;
    }

    private static final class Snapshot {
        final Map<TaskId, Task> tasks;
        final Map<String, TaskStepTemplate> templates;
        final Map<String, TaskScheduleEntry> schedule;
        final Map<String, Occurrence> occurrences;
        final Map<String, OccurrenceStep> occurrenceSteps;
        final Map<String, ComboProgress> combos;
        final Map<String, RewardBooking> bookings;
        final Map<String, String> rewardAssignments;
        final Map<String, ComboObligation> comboObligations;
        final Map<String, ComboDecayEvent> comboDecayEvents;
        final Map<String, TrainingAdjustment> trainingAdjustments;
        final Map<String, TrainingLoadRequest> trainingLoadRequests;
        final int xp;

        Snapshot(Map<TaskId, Task> tasks, Map<String, TaskStepTemplate> templates,
                 Map<String, TaskScheduleEntry> schedule,
                 Map<String, Occurrence> occurrences,
                 Map<String, OccurrenceStep> occurrenceSteps,
                 Map<String, ComboProgress> combos, Map<String, RewardBooking> bookings,
                 Map<String, String> rewardAssignments,
                 Map<String, ComboObligation> comboObligations,
                 Map<String, ComboDecayEvent> comboDecayEvents,
                 Map<String, TrainingAdjustment> trainingAdjustments,
                 Map<String, TrainingLoadRequest> trainingLoadRequests, int xp) {
            this.tasks = tasks;
            this.templates = templates;
            this.schedule = schedule;
            this.occurrences = occurrences;
            this.occurrenceSteps = occurrenceSteps;
            this.combos = combos;
            this.bookings = bookings;
            this.rewardAssignments = rewardAssignments;
            this.comboObligations = comboObligations;
            this.comboDecayEvents = comboDecayEvents;
            this.trainingAdjustments = trainingAdjustments;
            this.trainingLoadRequests = trainingLoadRequests;
            this.xp = xp;
        }
    }

    private static String decayKey(String ownerId, LocalDate eventOn) {
        return ownerId + '|' + eventOn;
    }
}
