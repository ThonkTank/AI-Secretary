package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.schedule.ScheduleMoveRequest;
import de.thonktank.autosecretary.domain.schedule.TaskScheduleService;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceKind;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

public final class DeferTask {
    private final TodayRepository today;
    private final CatalogRepository schedules;
    private final TransactionRunner transactions;

    public DeferTask(CatalogRepository schedules, TodayRepository today,
                     TransactionRunner transactions) {
        this.today = today;
        this.schedules = schedules;
        this.transactions = transactions;
    }

    public void execute(String occurrenceOrTaskId) {
        Occurrence selected = today.findOccurrence(occurrenceOrTaskId);
        if (selected != null) {
            if (selected.kind == OccurrenceKind.FLOW_SHEET) {
                deferFlowSheet(selected.id);
                return;
            }
            transactions.inTransaction(() -> {
                Occurrence current = today.findOccurrence(occurrenceOrTaskId);
                if (current == null) return null;
                int last = current.sortOrder;
                for (Occurrence occurrence : today.openOccurrences(current.slot))
                    last = Math.max(last, occurrence.sortOrder);
                if (last > current.sortOrder) today.updateOccurrence(current.moveTo(last + 1));
                return null;
            });
            return;
        }
        TaskId id;
        try { id = TaskId.of(occurrenceOrTaskId); }
        catch (IllegalArgumentException error) { return; }
        TaskScheduleService service = new TaskScheduleService(schedules, today, transactions,
                new UuidGenerator());
        java.util.List<TaskScheduleEntry> placements = schedules.scheduleEntries(id);
        if (placements.isEmpty()) return;
        TaskScheduleEntry primary = placements.get(0);
        service.move(ScheduleMoveRequest.toEnd(primary.id, primary.slot));
    }

    private void deferFlowSheet(String occurrenceId) {
        transactions.inTransaction(() -> {
            Occurrence selected = today.findOccurrence(occurrenceId);
            if (selected == null || selected.kind != OccurrenceKind.FLOW_SHEET) return null;
            java.util.List<Occurrence> open = today.openOccurrences(selected.slot);
            int last = selected.sortOrder;
            for (Occurrence occurrence : open) last = Math.max(last, occurrence.sortOrder);
            int next = last + 1;
            for (Occurrence occurrence : open)
                if (occurrence.kind == OccurrenceKind.FLOW_SHEET
                        && occurrence.taskId.equals(selected.taskId))
                    today.updateOccurrence(occurrence.moveTo(next++));
            return null;
        });
    }
}
