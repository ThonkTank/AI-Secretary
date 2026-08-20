package de.thonktank.autosecretary;

import android.os.Bundle;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskCatalog;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Complete immutable render and control state for the management tab. */
public final class AllTasksUiState {
    public enum Mode { LIST, SORT }
    public enum Status { ACTIVE, ARCHIVED, ALL }

    public final TaskCatalog catalog;
    public final String query;
    public final Status status;
    public final Set<TaskSlot> slots;
    public final Set<Recurrence> recurrences;
    /** ISO weekday 1..7, or zero for no weekday filter. */
    public final int weekday;
    public final Mode mode;
    public final Set<String> expandedTaskIds;
    public final List<TaskItem> tasks;
    public final List<ScheduleItem> schedule;

    private AllTasksUiState(TaskCatalog catalog, String query, Status status,
                            Set<TaskSlot> slots, Set<Recurrence> recurrences, int weekday,
                            Mode mode, Set<String> expandedTaskIds) {
        this.catalog = catalog == null ? new TaskCatalog(Collections.emptyList()) : catalog;
        this.query = query == null ? "" : query;
        this.status = status == null ? Status.ACTIVE : status;
        this.slots = Collections.unmodifiableSet(copySlots(slots));
        this.recurrences = Collections.unmodifiableSet(copyRecurrences(recurrences));
        this.weekday = weekday < 0 || weekday > 7 ? 0 : weekday;
        this.mode = mode == null ? Mode.LIST : mode;
        this.expandedTaskIds = Collections.unmodifiableSet(new LinkedHashSet<>(
                expandedTaskIds == null ? Collections.emptySet() : expandedTaskIds));
        this.tasks = Collections.unmodifiableList(projectTasks());
        this.schedule = Collections.unmodifiableList(projectSchedule());
    }

    public static AllTasksUiState empty() {
        return new AllTasksUiState(null, "", Status.ACTIVE, Collections.emptySet(),
                Collections.emptySet(), 0, Mode.LIST, Collections.emptySet());
    }

    public AllTasksUiState withCatalog(TaskCatalog value) {
        return copy(value, query, status, slots, recurrences, weekday, mode, expandedTaskIds);
    }
    public AllTasksUiState withQuery(String value) {
        return copy(catalog, value, status, slots, recurrences, weekday, mode, expandedTaskIds);
    }
    public AllTasksUiState withStatus(Status value) {
        return copy(catalog, query, value, slots, recurrences, weekday, mode, expandedTaskIds);
    }
    public AllTasksUiState withSlots(Set<TaskSlot> value) {
        return copy(catalog, query, status, value, recurrences, weekday, mode, expandedTaskIds);
    }
    public AllTasksUiState withRecurrences(Set<Recurrence> value) {
        return copy(catalog, query, status, slots, value, weekday, mode, expandedTaskIds);
    }
    public AllTasksUiState withWeekday(int value) {
        return copy(catalog, query, status, slots, recurrences, value, mode, expandedTaskIds);
    }
    public AllTasksUiState withMode(Mode value) {
        return copy(catalog, query, status, slots, recurrences, weekday, value, expandedTaskIds);
    }
    public AllTasksUiState toggleExpanded(String taskId) {
        Set<String> values = new LinkedHashSet<>(expandedTaskIds);
        if (!values.add(taskId)) values.remove(taskId);
        return copy(catalog, query, status, slots, recurrences, weekday, mode, values);
    }

    public Bundle controlsBundle() {
        Bundle value = new Bundle();
        value.putString("query", query); value.putString("status", status.name());
        value.putString("mode", mode.name()); value.putInt("weekday", weekday);
        ArrayList<String> slotValues = new ArrayList<>();
        for (TaskSlot slot : slots) slotValues.add(slot.name());
        value.putStringArrayList("slots", slotValues);
        ArrayList<String> rhythms = new ArrayList<>();
        for (Recurrence recurrence : recurrences) rhythms.add(recurrence.name());
        value.putStringArrayList("recurrences", rhythms);
        value.putStringArrayList("expanded", new ArrayList<>(expandedTaskIds));
        return value;
    }

    public static AllTasksUiState controlsFrom(Bundle value) {
        if (value == null) return empty();
        Set<TaskSlot> slots = EnumSet.noneOf(TaskSlot.class);
        ArrayList<String> slotValues = value.getStringArrayList("slots");
        if (slotValues != null) for (String stored : slotValues)
            try { slots.add(TaskSlot.valueOf(stored)); } catch (IllegalArgumentException ignored) { }
        Set<Recurrence> recurrences = EnumSet.noneOf(Recurrence.class);
        ArrayList<String> rhythms = value.getStringArrayList("recurrences");
        if (rhythms != null) for (String stored : rhythms)
            try { recurrences.add(Recurrence.valueOf(stored)); }
            catch (IllegalArgumentException ignored) { }
        ArrayList<String> expanded = value.getStringArrayList("expanded");
        return new AllTasksUiState(null, value.getString("query", ""),
                enumValue(Status.class, value.getString("status"), Status.ACTIVE),
                slots, recurrences, value.getInt("weekday"),
                enumValue(Mode.class, value.getString("mode"), Mode.LIST),
                expanded == null ? Collections.emptySet() : new LinkedHashSet<>(expanded));
    }

    private List<TaskItem> projectTasks() {
        List<TaskItem> result = new ArrayList<>();
        for (TaskCatalog.Item item : catalog.items) {
            boolean archived = item.task.archived || item.task.conditionDone;
            if ((status == Status.ACTIVE && archived)
                    || (status == Status.ARCHIVED && !archived))
                continue;
            if (!recurrences.isEmpty() && !recurrences.contains(item.task.recurrence)) continue;
            if (!slots.isEmpty() && !hasSlot(item.schedule, slots)) continue;
            if (!matches(item, query)) continue;
            result.add(new TaskItem(item, archived, expandedTaskIds.contains(item.task.id.value)));
        }
        return result;
    }

    private List<ScheduleItem> projectSchedule() {
        List<ScheduleItem> result = new ArrayList<>();
        for (TaskCatalog.Item item : catalog.items) {
            Task task = item.task;
            if (task.archived || task.conditionDone || status == Status.ARCHIVED) continue;
            if (!recurrences.isEmpty() && !recurrences.contains(task.recurrence)) continue;
            if (!matches(item, query) || !eligibleOn(task, weekday)) continue;
            for (TaskScheduleEntry entry : item.schedule) {
                if (!slots.isEmpty() && !slots.contains(entry.slot)) continue;
                result.add(new ScheduleItem(entry.id, task.id.value, task.title,
                        entry.slot, entry.displayOrder, task.recurrence));
            }
        }
        result.sort(Comparator.comparingInt((ScheduleItem value) -> value.slot.rank)
                .thenComparingLong(value -> value.displayOrder).thenComparing(value -> value.id));
        return result;
    }

    private static boolean matches(TaskCatalog.Item item, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.GERMAN);
        if (needle.isEmpty()) return true;
        if (contains(item.task.title, needle) || contains(item.task.note, needle)) return true;
        for (TaskStepTemplate step : item.steps)
            if (contains(step.text, needle) || contains(step.note, needle)) return true;
        return false;
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.GERMAN).contains(needle);
    }

    private static boolean hasSlot(List<TaskScheduleEntry> values, Set<TaskSlot> selected) {
        for (TaskScheduleEntry value : values) if (selected.contains(value.slot)) return true;
        return false;
    }

    private static boolean eligibleOn(Task task, int weekday) {
        if (weekday == 0 || task.recurrence == Recurrence.DAILY
                || task.recurrence == Recurrence.INTERVAL) return true;
        if (task.recurrence == Recurrence.WEEKDAYS)
            return (task.weekdayMask & 1 << (weekday - 1)) != 0;
        return task.nextDueOn != null
                && task.nextDueOn.getDayOfWeek() == DayOfWeek.of(weekday);
    }

    private AllTasksUiState copy(TaskCatalog catalog, String query, Status status,
                                 Set<TaskSlot> slots, Set<Recurrence> recurrences, int weekday,
                                 Mode mode, Set<String> expanded) {
        return new AllTasksUiState(catalog, query, status, slots, recurrences,
                weekday, mode, expanded);
    }

    private static EnumSet<TaskSlot> copySlots(Set<TaskSlot> values) {
        return values == null || values.isEmpty() ? EnumSet.noneOf(TaskSlot.class)
                : EnumSet.copyOf(values);
    }
    private static EnumSet<Recurrence> copyRecurrences(Set<Recurrence> values) {
        return values == null || values.isEmpty() ? EnumSet.noneOf(Recurrence.class)
                : EnumSet.copyOf(values);
    }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        if (value == null) return fallback;
        try { return Enum.valueOf(type, value); } catch (IllegalArgumentException error) { return fallback; }
    }

    public static final class TaskItem {
        public final Task task;
        public final List<TaskStepTemplate> steps;
        public final List<TaskScheduleEntry> schedule;
        public final boolean archived;
        public final boolean expanded;
        TaskItem(TaskCatalog.Item item, boolean archived, boolean expanded) {
            task = item.task; steps = item.steps; schedule = item.schedule;
            this.archived = archived; this.expanded = expanded;
        }
    }

    public static final class ScheduleItem {
        public final String id;
        public final String taskId;
        public final String title;
        public final TaskSlot slot;
        public final long displayOrder;
        public final Recurrence recurrence;
        ScheduleItem(String id, String taskId, String title, TaskSlot slot,
                     long displayOrder, Recurrence recurrence) {
            this.id = id; this.taskId = taskId; this.title = title; this.slot = slot;
            this.displayOrder = displayOrder; this.recurrence = recurrence;
        }
    }
}
