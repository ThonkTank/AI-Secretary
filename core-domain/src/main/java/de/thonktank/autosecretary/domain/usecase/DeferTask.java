package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.schedule.ScheduleMoveRequest;
import de.thonktank.autosecretary.domain.schedule.TaskScheduleService;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.schedule.TaskScheduleRepository;

public final class DeferTask {
    private final OccurrenceExecutionRepository repository;
    private final TaskScheduleRepository schedules;

    public DeferTask(OccurrenceExecutionRepository repository,
                     TaskScheduleRepository schedules) {
        this.repository = repository;
        this.schedules = schedules;
    }

    public void execute(String occurrenceOrTaskId) {
        Occurrence selected = repository.findOccurrence(occurrenceOrTaskId);
        if (selected != null) {
            repository.inTransaction(() -> {
                Occurrence current = repository.findOccurrence(occurrenceOrTaskId);
                if (current == null) return null;
                int last = current.sortOrder;
                for (Occurrence occurrence : repository.openOccurrences(current.slot))
                    last = Math.max(last, occurrence.sortOrder);
                if (last > current.sortOrder) repository.updateOccurrence(current.moveTo(last + 1));
                return null;
            });
            return;
        }
        TaskId id;
        try { id = TaskId.of(occurrenceOrTaskId); }
        catch (IllegalArgumentException error) { return; }
        TaskScheduleService service = new TaskScheduleService(schedules, new UuidGenerator());
        java.util.List<TaskScheduleEntry> placements = schedules.scheduleEntries(id);
        if (placements.isEmpty()) return;
        TaskScheduleEntry primary = placements.get(0);
        service.move(ScheduleMoveRequest.toEnd(primary.id, primary.slot));
    }
}
