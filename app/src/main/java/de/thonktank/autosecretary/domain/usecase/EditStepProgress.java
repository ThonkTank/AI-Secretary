package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.util.ArrayList;
import java.util.List;

public final class EditStepProgress {
    private final TaskRepository repository;
    public EditStepProgress(TaskRepository repository) {
        this.repository = repository;
    }

    public RewardReceipt execute(String stepId, int index, int repetitions) {
        return repository.inTransaction(() -> {
            OccurrenceStep current = repository.findOccurrenceStep(stepId);
            if (current == null || (!(current.amount instanceof StepAmount.SetsReps)
                    && !(current.amount instanceof StepAmount.Repetitions)))
                return RewardReceipt.none();
            Occurrence occurrence = repository.findOccurrence(current.occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN)
                return RewardReceipt.none();
            if (index < 0 || index >= current.actualRepetitions.size())
                throw new IllegalArgumentException("Confirmed repetition index is out of range");
            if (repetitions < 0 || repetitions > 999)
                throw new IllegalArgumentException(
                        "Confirmed repetitions must be between 0 and 999");
            List<Integer> corrected = new ArrayList<>(current.actualRepetitions);
            corrected.set(index, repetitions);
            repository.updateOccurrenceStep(copy(current, corrected, current.done));
            return RewardReceipt.none();
        });
    }

    private static OccurrenceStep copy(OccurrenceStep value, List<Integer> repetitions,
                                       boolean done) {
        return new OccurrenceStep(value.id, value.occurrenceId, value.position, value.text,
                done, value.amount, value.note, repetitions, value.sourceTemplateId,
                value.comboOwnerId);
    }
}
