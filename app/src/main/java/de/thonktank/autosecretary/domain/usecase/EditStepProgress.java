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

    public RewardReceipt execute(String stepId, List<Integer> repetitions) {
        return repository.inTransaction(() -> {
            OccurrenceStep current = repository.findOccurrenceStep(stepId);
            if (current == null || !(current.amount instanceof StepAmount.SetsReps))
                return RewardReceipt.none();
            Occurrence occurrence = repository.findOccurrence(current.occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN)
                return RewardReceipt.none();
            List<Integer> checked = new ArrayList<>();
            for (Integer value : repetitions) {
                if (value == null || value <= 0)
                    throw new IllegalArgumentException("Confirmed repetitions must be positive");
                checked.add(value);
            }
            if (checked.size() > ((StepAmount.SetsReps) current.amount).sets)
                throw new IllegalArgumentException("Confirmed set count exceeds planned sets");
            repository.updateOccurrenceStep(copy(current, checked, current.done));
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
