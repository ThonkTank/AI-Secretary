package de.thonktank.autosecretary;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Complete immutable render and control state for the management tab. */
public final class AllTasksUiState {
    public enum Mode { LIST, SORT }
    public enum Status { ACTIVE, ARCHIVED, ALL }

    public final TaskCatalog catalog;
    public final AllTasksFilter filter;
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

    private AllTasksUiState(TaskCatalog catalog, AllTasksFilter filter) {
        this.catalog = catalog == null ? new TaskCatalog(Collections.emptyList()) : catalog;
        this.filter = filter == null ? AllTasksFilter.defaults() : filter;
        this.query = this.filter.query;
        this.status = this.filter.status;
        this.slots = this.filter.slots;
        this.recurrences = this.filter.recurrences;
        this.weekday = this.filter.weekday;
        this.mode = this.filter.mode;
        this.expandedTaskIds = this.filter.expandedTaskIds;
        this.tasks = Collections.unmodifiableList(projectTasks());
        this.schedule = Collections.unmodifiableList(projectSchedule());
    }

    public static AllTasksUiState empty() {
        return new AllTasksUiState(null, AllTasksFilter.defaults());
    }

    public static AllTasksUiState from(TaskCatalog catalog, AllTasksFilter filter) {
        return new AllTasksUiState(catalog, filter);
    }

    public AllTasksUiState withCatalog(TaskCatalog value) {
        return new AllTasksUiState(value, filter);
    }
    public AllTasksUiState withQuery(String value) {
        return new AllTasksUiState(catalog, filter.withQuery(value));
    }
    public AllTasksUiState withStatus(Status value) {
        return new AllTasksUiState(catalog, filter.withStatus(value));
    }
    public AllTasksUiState withSlots(Set<TaskSlot> value) {
        return new AllTasksUiState(catalog, filter.withSlots(value));
    }
    public AllTasksUiState withRecurrences(Set<Recurrence> value) {
        return new AllTasksUiState(catalog, filter.withRecurrences(value));
    }
    public AllTasksUiState withWeekday(int value) {
        return new AllTasksUiState(catalog, filter.withWeekday(value));
    }
    public AllTasksUiState withMode(Mode value) {
        return new AllTasksUiState(catalog, filter.withMode(value));
    }
    public AllTasksUiState toggleExpanded(String taskId) {
        return new AllTasksUiState(catalog, filter.toggleExpanded(taskId));
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
