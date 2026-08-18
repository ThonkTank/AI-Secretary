package de.thonktank.autosecretary.domain.usecase;

import java.util.List;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

/** Explicitly reopens a completed exercise while preserving its editable set progress. */
public final class ReopenExercise {
    private final TaskRepository repository;
    private final RewardEngine rewards;

    public ReopenExercise(TaskRepository repository, Clock clock) {
        this.repository = repository;
        this.rewards = new RewardEngine(repository, clock);
    }

    public RewardReceipt execute(String stepId, List<Integer> repetitions) {
        final RewardReceipt[] result = {RewardReceipt.none()};
        repository.inTransaction(() -> {
            OccurrenceStep step = repository.findOccurrenceStep(stepId);
            if (step == null || step.amountKind != StepAmountKind.SETS_REPS || !step.done) return;
            Occurrence occurrence = repository.findOccurrence(step.occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN) return;
            OccurrenceStep edited = step.withActualRepetitions(repetitions);
            repository.updateOccurrenceStep(edited);
            result[0] = rewards.undoStep(occurrence, edited);
        });
        return result[0];
    }
}
