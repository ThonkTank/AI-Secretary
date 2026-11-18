package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

import java.util.Collections;

/** Appends a reusable step and its snapshot to the selected execution atomically. */
public final class AddTodayStep {
    private final CatalogRepository catalog;
    private final StepRepository steps;
    private final TodayRepository today;
    private final TransactionRunner transactions;
    private final IdGenerator ids;
    private final StepSnapshotFactory snapshots;

    public AddTodayStep(CatalogRepository catalog, StepRepository steps, TodayRepository today,
                        TransactionRunner transactions, IdGenerator ids) {
        this.catalog = catalog;
        this.steps = steps;
        this.today = today;
        this.transactions = transactions;
        this.ids = ids;
        this.snapshots = new StepSnapshotFactory(ids);
    }

    public boolean execute(String occurrenceId, String title) {
        return transactions.inTransaction(() -> {
            Occurrence occurrence = today.findOccurrence(occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN
                    || occurrence.kind == OccurrenceKind.FLOW_STEP) return false;
            Task task = catalog.findTask(occurrence.taskId);
            if (task == null || task.archived || task.conditionDone || task.kind == TaskKind.FLOW)
                return false;
            if (title == null || title.trim().isEmpty()) return false;
            int templatePosition = steps.templates(task.id).stream()
                    .mapToInt(step -> step.position).max().orElse(-1) + 1;
            int executionPosition = steps.occurrenceSteps(occurrence.id).stream()
                    .mapToInt(step -> step.position).max().orElse(-1) + 1;
            TaskStepTemplate template = new TaskStepTemplate(ids.nextId(), task.id,
                    templatePosition, title.trim(), 0, 0, StepPrescription.forAmount(StepAmount.none()),
                    "", StepActivationKind.SCHEDULED);
            steps.insertTemplates(Collections.singletonList(template));
            steps.insertOccurrenceSteps(Collections.singletonList(
                    snapshots.fromTemplate(template, occurrence.id, executionPosition)));
            return true;
        });
    }
}
