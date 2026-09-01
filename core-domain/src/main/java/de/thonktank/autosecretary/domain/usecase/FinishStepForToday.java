package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Ends one quantitative step with exactly the results recorded so far. */
public final class FinishStepForToday {
    private final StepRepository steps;
    private final TodayRepository today;
    private final TransactionRunner transactions;
    private final StepExecutionService execution;

    public FinishStepForToday(CatalogRepository catalog, StepRepository steps, TodayRepository today, TransactionRunner transactions, Clock clock,
                       ComboPolicySource policies) {
        this.steps = steps;
        this.today = today;
        this.transactions = transactions;
        execution = new StepExecutionService(catalog, steps, today, transactions, clock, policies);
    }

    public RewardReceipt execute(String stepId) {
        return transactions.inTransaction(() -> {
            OccurrenceStep step = steps.findOccurrenceStep(stepId);
            Occurrence occurrence = step == null ? null
                    : today.findOccurrence(step.occurrenceId);
            if (step == null || step.repetitionProgress == null || step.done
                    || occurrence == null || occurrence.state != OccurrenceState.OPEN)
                return RewardReceipt.none();
            return execution.completeStep(occurrence, step,
                    java.util.UUID.randomUUID().toString());
        });
    }
}
