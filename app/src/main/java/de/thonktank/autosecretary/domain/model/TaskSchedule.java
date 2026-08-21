package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical aggregate for all task placements.
 *
 * It owns the task/slot uniqueness invariant, primary placement and deterministic ordering
 * changes. Task definitions deliberately contain no persisted scheduling projection.
 */
public final class TaskSchedule {
    public static final long ORDER_STEP = 1_024L;
    private static final Comparator<TaskScheduleEntry> ORDER = Comparator
            .comparingInt((TaskScheduleEntry value) -> value.slot.rank)
            .thenComparingLong(value -> value.displayOrder)
            .thenComparing(value -> value.id);

    private final List<TaskScheduleEntry> entries;

    public TaskSchedule(List<TaskScheduleEntry> entries) {
        if (entries == null) throw new IllegalArgumentException("Schedule entries are required");
        List<TaskScheduleEntry> copied = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> placements = new HashSet<>();
        for (TaskScheduleEntry entry : entries) {
            if (entry == null || !ids.add(entry.id)
                    || !placements.add(key(entry.taskId, entry.slot)))
                throw new IllegalArgumentException("Schedule entries must have unique ids and slots");
            copied.add(entry);
        }
        copied.sort(ORDER);
        this.entries = Collections.unmodifiableList(copied);
    }

    public List<TaskScheduleEntry> entries() { return entries; }

    public List<TaskScheduleEntry> placements(TaskId taskId) {
        List<TaskScheduleEntry> result = new ArrayList<>();
        for (TaskScheduleEntry entry : entries)
            if (entry.taskId.equals(taskId)) result.add(entry);
        result.sort(ORDER);
        return Collections.unmodifiableList(result);
    }

    public List<TaskSlot> slots(TaskId taskId) {
        List<TaskSlot> result = new ArrayList<>();
        for (TaskScheduleEntry entry : placements(taskId)) result.add(entry.slot);
        return Collections.unmodifiableList(result);
    }

    public TaskScheduleEntry primary(TaskId taskId) {
        List<TaskScheduleEntry> values = placements(taskId);
        if (values.isEmpty())
            throw new IllegalStateException("Task " + taskId.value + " has no schedule placement");
        return values.get(0);
    }

    public boolean contains(TaskId taskId, TaskSlot slot) {
        for (TaskScheduleEntry entry : entries)
            if (entry.taskId.equals(taskId) && entry.slot == slot) return true;
        return false;
    }

    public long nextOrder(TaskSlot slot) {
        long last = 0;
        for (TaskScheduleEntry entry : entries)
            if (entry.slot == slot) last = Math.max(last, entry.displayOrder);
        return last + ORDER_STEP;
    }

    public Mutation move(String entryId, TaskSlot targetSlot, String beforeEntryId) {
        TaskScheduleEntry moving = null;
        for (TaskScheduleEntry entry : entries) if (entry.id.equals(entryId)) moving = entry;
        if (moving == null) return Mutation.unchanged(this);
        if (moving.slot != targetSlot && contains(moving.taskId, targetSlot))
            throw new IllegalArgumentException("Task already has a placement in the target slot");

        List<TaskScheduleEntry> source = inSlot(moving.slot, moving.id);
        List<TaskScheduleEntry> target = moving.slot == targetSlot
                ? source : inSlot(targetSlot, moving.id);
        TaskScheduleEntry changed = moving.move(targetSlot, 0);
        int insertion = indexOf(target, beforeEntryId);
        target.add(insertion < 0 ? target.size() : insertion, changed);

        List<TaskScheduleEntry> writes = new ArrayList<>();
        resequence(target, writes);
        if (moving.slot != targetSlot) resequence(source, writes);
        Map<String, TaskScheduleEntry> replacements = new HashMap<>();
        for (TaskScheduleEntry entry : writes) replacements.put(entry.id, entry);
        List<TaskScheduleEntry> all = new ArrayList<>();
        for (TaskScheduleEntry entry : entries)
            all.add(replacements.getOrDefault(entry.id, entry));
        return new Mutation(new TaskSchedule(all), writes, moving.slot, targetSlot, true);
    }

    public static List<TaskSlot> desiredSlots(TaskDefinition definition) {
        if (definition.recurrence == Recurrence.ONCE)
            return Collections.singletonList(definition.fallbackSlot);
        Set<TaskSlot> selected = EnumSet.noneOf(TaskSlot.class);
        selected.addAll(TimeOfDay.slots(definition.timeOfDayMask));
        if (selected.isEmpty()) selected.add(definition.fallbackSlot);
        return Collections.unmodifiableList(new ArrayList<>(selected));
    }

    private List<TaskScheduleEntry> inSlot(TaskSlot slot, String excludedId) {
        List<TaskScheduleEntry> values = new ArrayList<>();
        for (TaskScheduleEntry entry : entries)
            if (entry.slot == slot && !entry.id.equals(excludedId)) values.add(entry);
        values.sort(Comparator.comparingLong((TaskScheduleEntry value) -> value.displayOrder)
                .thenComparing(value -> value.id));
        return values;
    }

    private static void resequence(List<TaskScheduleEntry> values,
                                   List<TaskScheduleEntry> writes) {
        for (int index = 0; index < values.size(); index++) {
            TaskScheduleEntry changed = values.get(index).withOrder((index + 1L) * ORDER_STEP);
            values.set(index, changed);
            writes.add(changed);
        }
    }

    private static int indexOf(List<TaskScheduleEntry> values, String id) {
        if (id == null) return -1;
        for (int index = 0; index < values.size(); index++)
            if (values.get(index).id.equals(id)) return index;
        return -1;
    }

    private static String key(TaskId taskId, TaskSlot slot) {
        return taskId.value + '|' + slot.name();
    }

    public static final class Mutation {
        public final TaskSchedule schedule;
        public final List<TaskScheduleEntry> writes;
        public final TaskSlot sourceSlot;
        public final TaskSlot targetSlot;
        public final boolean changed;

        private Mutation(TaskSchedule schedule, List<TaskScheduleEntry> writes,
                         TaskSlot sourceSlot, TaskSlot targetSlot, boolean changed) {
            this.schedule = schedule;
            this.writes = Collections.unmodifiableList(new ArrayList<>(writes));
            this.sourceSlot = sourceSlot;
            this.targetSlot = targetSlot;
            this.changed = changed;
        }

        private static Mutation unchanged(TaskSchedule schedule) {
            return new Mutation(schedule, Collections.emptyList(), null, null, false);
        }
    }
}
