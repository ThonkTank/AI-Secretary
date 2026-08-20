package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.domain.repository.TaskDefinitionRepository;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Keeps normalized schedule placements aligned with editable task definitions. */
final class TaskScheduleService {
    private static final long ORDER_STEP = 1_024L;
    private final TaskDefinitionRepository repository;
    private final IdGenerator ids;

    TaskScheduleService(TaskDefinitionRepository repository, IdGenerator ids) {
        this.repository = repository;
        this.ids = ids;
    }

    void sync(Task task, TaskDefinition definition) {
        Set<TaskSlot> desired = slots(definition);
        Map<TaskSlot, TaskScheduleEntry> existing = new LinkedHashMap<>();
        for (TaskScheduleEntry entry : repository.scheduleEntries(task.id))
            existing.put(entry.slot, entry);
        List<TaskScheduleEntry> updated = new ArrayList<>();
        for (TaskSlot slot : desired) {
            TaskScheduleEntry current = existing.remove(slot);
            updated.add(current == null
                    ? new TaskScheduleEntry(ids.nextId(), task.id, slot, nextOrder(slot))
                    : current);
        }
        for (TaskScheduleEntry removed : existing.values())
            repository.deleteScheduleEntry(removed.id);
        repository.putScheduleEntries(updated);
    }

    void create(Task task) {
        List<TaskScheduleEntry> entries = new ArrayList<>();
        List<TaskSlot> slots = task.recurrence == Recurrence.ONCE
                ? java.util.Collections.singletonList(task.slot)
                : TimeOfDay.slots(task.timeOfDayMask);
        if (slots.isEmpty()) slots = java.util.Collections.singletonList(task.slot);
        for (TaskSlot slot : slots)
            entries.add(new TaskScheduleEntry(ids.nextId(), task.id, slot, nextOrder(slot)));
        repository.putScheduleEntries(entries);
    }

    private long nextOrder(TaskSlot slot) {
        long last = 0;
        for (TaskScheduleEntry entry : repository.scheduleEntries())
            if (entry.slot == slot) last = Math.max(last, entry.displayOrder);
        return last + ORDER_STEP;
    }

    private static Set<TaskSlot> slots(TaskDefinition definition) {
        if (definition.recurrence == Recurrence.ONCE)
            return EnumSet.of(definition.fallbackSlot);
        Set<TaskSlot> result = EnumSet.noneOf(TaskSlot.class);
        result.addAll(TimeOfDay.slots(definition.timeOfDayMask));
        if (result.isEmpty()) result.add(definition.fallbackSlot);
        return result;
    }
}
