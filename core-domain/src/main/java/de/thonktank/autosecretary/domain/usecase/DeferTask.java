package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.schedule.ScheduleMoveRequest;
import de.thonktank.autosecretary.domain.schedule.TaskScheduleService;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceKind;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.schedule.TaskScheduleRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

public final class DeferTask {
    private final OccurrenceExecutionRepository repository;
    private final TaskScheduleRepository schedules;
    private final TransactionRunner transactions;
    private final FlowRuntimeCoordinator flows;

    public DeferTask(OccurrenceExecutionRepository repository,
                     TaskScheduleRepository schedules, TransactionRunner transactions) {
        this(repository, schedules, transactions, null);
    }

    public DeferTask(OccurrenceExecutionRepository repository,
                     TaskScheduleRepository schedules, TransactionRunner transactions,
                     FlowRuntimeCoordinator flows) {
        this.repository = repository;
        this.schedules = schedules;
        this.transactions = transactions;
        this.flows = flows;
    }

    public void execute(String occurrenceOrTaskId) {
        Occurrence selected = repository.findOccurrence(occurrenceOrTaskId);
        if (selected != null) {
            if (selected.kind == OccurrenceKind.FLOW_SHEET && selected.flowRunId != null
                    && flows != null) {
                flows.defer(selected.flowRunId);
                return;
            }
            transactions.inTransaction(() -> {
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
        TaskScheduleService service = new TaskScheduleService(schedules, transactions,
                new UuidGenerator());
        java.util.List<TaskScheduleEntry> placements = schedules.scheduleEntries(id);
        if (placements.isEmpty()) return;
        TaskScheduleEntry primary = placements.get(0);
        service.move(ScheduleMoveRequest.toEnd(primary.id, primary.slot));
    }
}
