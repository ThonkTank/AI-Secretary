package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.ComboObligationRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Ends one quantitative step with exactly the results recorded so far. */
public final class FinishStepForToday {
    private final OccurrenceExecutionRepository occurrences;
    private final TransactionRunner transactions;
    private final StepExecutionService execution;

    public FinishStepForToday(OccurrenceExecutionRepository occurrences,
                       RewardLedgerRepository rewards, ComboObligationRepository obligations, TransactionRunner transactions, Clock clock,
                       ComboPolicySource policies) {
        this.occurrences = occurrences;
        this.transactions = transactions;
        execution = new StepExecutionService(occurrences, rewards, obligations, transactions, clock, policies);
    }

    public RewardReceipt execute(String stepId) {
        return transactions.inTransaction(() -> {
            OccurrenceStep step = occurrences.findOccurrenceStep(stepId);
            Occurrence occurrence = step == null ? null
                    : occurrences.findOccurrence(step.occurrenceId);
            if (step == null || step.repetitionProgress == null || step.done
                    || occurrence == null || occurrence.state != OccurrenceState.OPEN)
                return RewardReceipt.none();
            return execution.completeStep(occurrence, step,
                    java.util.UUID.randomUUID().toString());
        });
    }
}
