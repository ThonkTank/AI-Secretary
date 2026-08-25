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
import de.thonktank.autosecretary.domain.repository.ApplicationTaskRepository;
import de.thonktank.autosecretary.domain.today.TodayStepPositionUpdate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cross-use-case execution store for completion acceptance tests. Management use cases use
 * focused schedule/step doubles and cannot reach this aggregate by accident.
 */
public final class InMemoryExecutionRepository implements ApplicationTaskRepository {
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
    private int xp;

    @Override public synchronized <T> T inTransaction(Transaction<T> operation) {
        Snapshot before = snapshot();
        try {
            return operation.execute();
        } catch (RuntimeException | Error failure) {
            restore(before);
            throw failure;
        }
    }

    @Override public synchronized void insertTask(Task task) { tasks.put(task.id, task); }
    @Override public synchronized void updateTask(Task task) { tasks.put(task.id, task); }
    @Override public synchronized Task findTask(TaskId id) { return tasks.get(id); }
    @Override public synchronized List<Task> activeTasks() {
        List<Task> result = new ArrayList<>();
        for (Task task : tasks.values()) if (!task.archived && !task.conditionDone) result.add(task);
        return result;
    }
    @Override public synchronized List<Task> allTasks() { return new ArrayList<>(tasks.values()); }
    @Override public synchronized void deleteTask(TaskId id) {
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

    @Override public synchronized void insertTemplates(List<TaskStepTemplate> values) {
        for (TaskStepTemplate value : values) templates.put(value.id, value);
    }
    @Override public synchronized void deleteTemplates(TaskId taskId) {
        templates.values().removeIf(value -> value.taskId.equals(taskId));
    }
    @Override public synchronized void deleteTemplate(String id) { templates.remove(id); }
    @Override public synchronized List<TaskStepTemplate> templates(TaskId taskId) {
        List<TaskStepTemplate> result = new ArrayList<>();
        for (TaskStepTemplate value : templates.values())
            if (value.taskId.equals(taskId)) result.add(value);
        result.sort(Comparator.comparingInt(value -> value.position));
        return result;
    }
    @Override public synchronized TaskStepTemplate findTemplate(String id) {
        return templates.get(id);
    }
    @Override public synchronized List<TaskStepTemplate> templatesFor(List<TaskId> taskIds) {
        Set<TaskId> selected = new HashSet<>(taskIds);
        List<TaskStepTemplate> result = new ArrayList<>();
        for (TaskStepTemplate value : templates.values())
            if (selected.contains(value.taskId)) result.add(value);
        result.sort(Comparator.comparing((TaskStepTemplate value) -> value.taskId.value)
                .thenComparingInt(value -> value.position));
        return result;
    }
    @Override public synchronized void putScheduleEntries(List<TaskScheduleEntry> values) {
        for (TaskScheduleEntry value : values) schedule.put(value.id, value);
    }
    @Override public synchronized void deleteScheduleEntry(String id) { schedule.remove(id); }
    @Override public synchronized List<TaskScheduleEntry> scheduleEntries() {
        List<TaskScheduleEntry> result = new ArrayList<>(schedule.values());
        result.sort(scheduleOrder());
        return result;
    }
    @Override public synchronized List<TaskScheduleEntry> scheduleEntries(TaskId taskId) {
        List<TaskScheduleEntry> result = new ArrayList<>();
        for (TaskScheduleEntry value : schedule.values())
            if (value.taskId.equals(taskId)) result.add(value);
        result.sort(scheduleOrder());
        return result;
    }
    @Override public synchronized TaskScheduleEntry findScheduleEntry(String id) {
        return schedule.get(id);
    }
    @Override public synchronized List<TaskScheduleEntry> scheduleEntries(TaskSlot slot) {
        List<TaskScheduleEntry> result = new ArrayList<>();
        for (TaskScheduleEntry value : schedule.values())
            if (value.slot == slot) result.add(value);
        result.sort(scheduleOrder());
        return result;
    }
    @Override public synchronized List<TaskScheduleEntry> scheduleEntriesFor(List<TaskId> taskIds) {
        Set<TaskId> selected = new HashSet<>(taskIds);
        List<TaskScheduleEntry> result = new ArrayList<>();
        for (TaskScheduleEntry value : schedule.values())
            if (selected.contains(value.taskId)) result.add(value);
        result.sort(scheduleOrder());
        return result;
    }

    @Override public synchronized void insertOccurrence(Occurrence occurrence) {
        occurrences.putIfAbsent(occurrence.id, occurrence);
    }
    @Override public synchronized void updateOccurrence(Occurrence occurrence) {
        occurrences.put(occurrence.id, occurrence);
    }
    @Override public synchronized Occurrence findOccurrence(String id) {
        return occurrences.get(id);
    }
    @Override public synchronized Occurrence findOccurrence(TaskId taskId, LocalDate scheduledOn,
                                                            TaskSlot slot) {
        for (Occurrence value : occurrences.values())
            if (value.taskId.equals(taskId) && value.scheduledOn.equals(scheduledOn)
                    && value.slot == slot) return value;
        return null;
    }
    @Override public synchronized List<Occurrence> openOccurrences(TaskId taskId,
                                                                   LocalDate scheduledOn) {
        List<Occurrence> result = new ArrayList<>();
        for (Occurrence value : occurrences.values())
            if (value.taskId.equals(taskId) && value.scheduledOn.equals(scheduledOn)
                    && value.state == OccurrenceState.OPEN) result.add(value);
        return result;
    }
    @Override public synchronized List<Occurrence> openOccurrences(TaskId taskId) {
        List<Occurrence> result = new ArrayList<>();
        for (Occurrence value : occurrences.values())
            if (value.taskId.equals(taskId) && value.state == OccurrenceState.OPEN)
                result.add(value);
        result.sort(Comparator.comparing((Occurrence value) -> value.scheduledOn)
                .thenComparingInt(value -> value.slot.rank));
        return result;
    }
    @Override public synchronized Occurrence openOccurrence(TaskId taskId, TaskSlot slot) {
        for (Occurrence value : openOccurrences(taskId))
            if (value.slot == slot) return value;
        return null;
    }
    @Override public synchronized Occurrence openOccurrence(TaskId taskId) {
        for (Occurrence value : occurrences.values())
            if (value.taskId.equals(taskId) && value.state == OccurrenceState.OPEN) return value;
        return null;
    }
    @Override public synchronized List<Occurrence> openOccurrences() {
        return byState(OccurrenceState.OPEN);
    }
    @Override public synchronized List<Occurrence> openOccurrences(TaskSlot slot) {
        List<Occurrence> result = new ArrayList<>();
        for (Occurrence value : occurrences.values())
            if (value.slot == slot && value.state == OccurrenceState.OPEN) result.add(value);
        result.sort(Comparator.comparingInt((Occurrence value) -> value.sortOrder)
                .thenComparing(value -> value.scheduledOn).thenComparing(value -> value.id));
        return result;
    }
    @Override public synchronized List<Occurrence> allOccurrences() {
        return new ArrayList<>(occurrences.values());
    }
    @Override public synchronized List<Occurrence> occurrences(TaskId taskId) {
        List<Occurrence> result = new ArrayList<>();
        for (Occurrence value : occurrences.values())
            if (value.taskId.equals(taskId)) result.add(value);
        return result;
    }
    @Override public synchronized Occurrence earliestOpenOccurrence(TaskId taskId) {
        return occurrences(taskId).stream().filter(value -> value.state == OccurrenceState.OPEN)
                .min(Comparator.comparing(value -> value.scheduledOn)).orElse(null);
    }
    @Override public synchronized Occurrence latestCompletedOccurrence(TaskId taskId) {
        return occurrences(taskId).stream()
                .filter(value -> value.state.isHarvested())
                .max(Comparator.comparing((Occurrence value) -> value.completedOn)
                        .thenComparing(value -> value.scheduledOn)).orElse(null);
    }
    @Override public synchronized List<Occurrence> completedOccurrences(LocalDate date) {
        List<Occurrence> result = new ArrayList<>();
        for (Occurrence value : occurrences.values())
            if (value.state.isHarvested() && date.equals(value.completedOn))
                result.add(value);
        return result;
    }
    @Override public synchronized void insertOccurrenceSteps(List<OccurrenceStep> values) {
        for (OccurrenceStep value : values) occurrenceSteps.put(value.id, value);
    }
    @Override public synchronized List<OccurrenceStep> occurrenceSteps(String occurrenceId) {
        List<OccurrenceStep> result = new ArrayList<>();
        for (OccurrenceStep value : occurrenceSteps.values())
            if (value.occurrenceId.equals(occurrenceId)) result.add(value);
        result.sort(Comparator.comparingInt(value -> value.position));
        return result;
    }
    @Override public synchronized List<OccurrenceStep> occurrenceStepsFor(
            List<String> occurrenceIds) {
        Set<String> selected = new HashSet<>(occurrenceIds);
        List<OccurrenceStep> result = new ArrayList<>();
        for (OccurrenceStep value : occurrenceSteps.values())
            if (selected.contains(value.occurrenceId)) result.add(value);
        result.sort(Comparator.comparing((OccurrenceStep value) -> value.occurrenceId)
                .thenComparingInt(value -> value.position));
        return result;
    }
    @Override public synchronized OccurrenceStep findOccurrenceStep(String id) {
        return occurrenceSteps.get(id);
    }
    @Override public synchronized void updateOccurrenceStep(OccurrenceStep step) {
        occurrenceSteps.put(step.id, step);
    }
    @Override public synchronized void updateOccurrenceStepPositions(
            List<TodayStepPositionUpdate> updates) {
        for (TodayStepPositionUpdate update : updates) {
            OccurrenceStep step = occurrenceSteps.get(update.stepId);
            if (step != null)
                occurrenceSteps.put(step.id, step.relocate(step.occurrenceId, update.position));
        }
    }
    @Override public synchronized void assignRewardBookings(String occurrenceStepId,
                                                             String occurrenceId) {
        for (RewardBooking value : bookings.values())
            if (occurrenceStepId.equals(value.occurrenceStepId))
                rewardAssignments.put(value.id, occurrenceId);
    }

    @Override public synchronized int xp() { return xp; }
    @Override public synchronized void setXp(int value) { xp = Math.max(0, value); }
    @Override public synchronized ComboProgress combo(String ownerId) { return combos.get(ownerId); }
    @Override public synchronized void putCombo(ComboProgress combo) {
        combos.put(combo.ownerId, combo);
    }
    @Override public synchronized List<ComboProgress> combos() {
        return new ArrayList<>(combos.values());
    }

    @Override public synchronized void insertRewardBooking(RewardBooking booking) {
        if (bookings.containsKey(booking.id))
            throw new IllegalStateException("Duplicate reward booking " + booking.id);
        if (booking.reversesBookingId != null)
            for (RewardBooking value : bookings.values())
                if (booking.reversesBookingId.equals(value.reversesBookingId))
                    throw new IllegalStateException("Reward booking already reversed");
        bookings.put(booking.id, booking);
    }
    @Override public synchronized List<RewardBooking> rewardBookings(String occurrenceId) {
        List<RewardBooking> result = new ArrayList<>();
        for (RewardBooking value : bookings.values())
            if (effectiveOccurrence(value).equals(occurrenceId))
                result.add(project(value, occurrenceId));
        result.sort(bookingOrder());
        return result;
    }
    @Override public synchronized List<RewardBooking> rewardBookings(
            List<String> occurrenceIds) {
        Set<String> selected = new HashSet<>(occurrenceIds);
        List<RewardBooking> result = new ArrayList<>();
        for (RewardBooking value : bookings.values())
            if (selected.contains(effectiveOccurrence(value)))
                result.add(project(value, effectiveOccurrence(value)));
        result.sort(bookingOrder());
        return result;
    }

    @Override public synchronized List<ComboObligation> comboObligations() {
        return new ArrayList<>(comboObligations.values());
    }

    @Override public synchronized void insertComboObligations(List<ComboObligation> values) {
        for (ComboObligation value : values) comboObligations.putIfAbsent(value.id, value);
    }

    @Override public synchronized void updateComboObligation(ComboObligation value) {
        if (comboObligations.containsKey(value.id)) comboObligations.put(value.id, value);
    }

    @Override public synchronized ComboDecayEvent comboDecayEvent(String ownerId,
                                                                   LocalDate eventOn) {
        return comboDecayEvents.get(decayKey(ownerId, eventOn));
    }

    @Override public synchronized void insertComboDecayEvent(ComboDecayEvent event) {
        String key = decayKey(event.ownerId, event.eventOn);
        if (comboDecayEvents.putIfAbsent(key, event) != null)
            throw new IllegalStateException("Duplicate combo decay event " + key);
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
                new LinkedHashMap<>(comboDecayEvents), xp);
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
        final int xp;

        Snapshot(Map<TaskId, Task> tasks, Map<String, TaskStepTemplate> templates,
                 Map<String, TaskScheduleEntry> schedule,
                 Map<String, Occurrence> occurrences,
                 Map<String, OccurrenceStep> occurrenceSteps,
                 Map<String, ComboProgress> combos, Map<String, RewardBooking> bookings,
                 Map<String, String> rewardAssignments,
                 Map<String, ComboObligation> comboObligations,
                 Map<String, ComboDecayEvent> comboDecayEvents, int xp) {
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
            this.xp = xp;
        }
    }

    private static String decayKey(String ownerId, LocalDate eventOn) {
        return ownerId + '|' + eventOn;
    }
}
