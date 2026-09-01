package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.today.TodayStepMoveResult;
import de.thonktank.autosecretary.domain.today.TodayStepOrder;
import de.thonktank.autosecretary.domain.today.TodayOccurrenceSnapshot;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Persists an execution-only step order without changing reusable templates. */
public final class MoveTodayStep {
    private final StepRepository steps;
    private final TodayRepository today;
    private final TransactionRunner transactions;

    public MoveTodayStep(StepRepository steps, TodayRepository today,
                         TransactionRunner transactions) {
        this.steps = steps;
        this.today = today;
        this.transactions = transactions;
    }

    public TodayStepMoveResult execute(String stepId, String beforeStepId) {
        if (stepId == null || stepId.isEmpty())
            throw new IllegalArgumentException("Step identity is required");
        return transactions.inTransaction(() -> {
            OccurrenceStep moving = steps.findOccurrenceStep(stepId);
            if (moving == null)
                return TodayStepOrder.move(new TodayOccurrenceSnapshot(null,
                        java.util.Collections.emptyList(), null), stepId, beforeStepId);
            Occurrence occurrence = today.findOccurrence(moving.occurrenceId);
            OccurrenceStep before = beforeStepId == null ? null
                    : steps.findOccurrenceStep(beforeStepId);
            TodayStepMoveResult result = TodayStepOrder.move(new TodayOccurrenceSnapshot(
                    occurrence, steps.occurrenceSteps(moving.occurrenceId), before),
                    stepId, beforeStepId);
            if (result.moved()) steps.updateOccurrenceStepPositions(result.positionUpdates);
            return result;
        });
    }
}
