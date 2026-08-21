package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

public final class DeferTask {
    private final TaskRepository repository;

    public DeferTask(TaskRepository repository, LoadDashboard ignored,
                     TaskOrdering ignoredOrdering, Clock clock) {
        this.repository = repository;
    }

    public void execute(String occurrenceOrTaskId) {
        Occurrence selected = repository.findOccurrence(occurrenceOrTaskId);
        if (selected != null) {
            repository.inTransaction(() -> {
                Occurrence current = repository.findOccurrence(occurrenceOrTaskId);
                if (current == null) return null;
                int last = current.sortOrder;
                for (Occurrence occurrence : repository.openOccurrences())
                    if (occurrence.slot == current.slot) last = Math.max(last, occurrence.sortOrder);
                if (last > current.sortOrder) repository.updateOccurrence(current.moveTo(last + 1));
                return null;
            });
            return;
        }
        TaskId id;
        try { id = TaskId.of(occurrenceOrTaskId); }
        catch (IllegalArgumentException error) { return; }
        TaskScheduleService schedules = new TaskScheduleService(repository, new UuidGenerator());
        TaskScheduleEntry primary;
        try { primary = schedules.load().primary(id); }
        catch (IllegalStateException missingSchedule) { return; }
        schedules.move(primary.id, primary.slot, null);
    }
}
