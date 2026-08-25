package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;

/** Ends one quantitative step with exactly the results recorded so far. */
public final class FinishStepForToday {
    private final OccurrenceExecutionRepository occurrences;
    private final StepExecutionService execution;

    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository>
    FinishStepForToday(T repository, Clock clock, ComboPolicySource policies) {
        occurrences = repository;
        execution = new StepExecutionService(repository, clock, policies);
    }

    public RewardReceipt execute(String stepId) {
        return occurrences.inTransaction(() -> {
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
