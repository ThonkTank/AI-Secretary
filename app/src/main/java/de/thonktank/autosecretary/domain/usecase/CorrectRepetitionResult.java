package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

/** Corrects one persisted result without changing completion rewards. */
public final class CorrectRepetitionResult {
    private final TaskRepository repository;

    public CorrectRepetitionResult(TaskRepository repository) {
        this.repository = repository;
    }

    public RewardReceipt execute(String stepId, int index, int repetitions) {
        return repository.inTransaction(() -> {
            OccurrenceStep current = repository.findOccurrenceStep(stepId);
            if (current == null || current.repetitionProgress == null)
                return RewardReceipt.none();
            Occurrence occurrence = repository.findOccurrence(current.occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN)
                return RewardReceipt.none();
            repository.updateOccurrenceStep(
                    current.correctRepetitionResult(index, repetitions));
            return RewardReceipt.none();
        });
    }
}
