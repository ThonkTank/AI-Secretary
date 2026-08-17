package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.repository.TaskRepository;
import de.thonktank.autosecretary.domain.model.StepAmountKind;

public final class ToggleStep {
    private final TaskRepository repository;

    public ToggleStep(TaskRepository repository) {
        this.repository = repository;
    }

    public void execute(String stepId) {
        repository.inTransaction(() -> {
            OccurrenceStep step = repository.findOccurrenceStep(stepId);
            if (step == null) return;
            Occurrence occurrence = repository.findOccurrence(step.occurrenceId);
            if (occurrence != null && occurrence.state == OccurrenceState.OPEN
                    && step.amountKind != StepAmountKind.SETS_REPS)
                repository.updateOccurrenceStep(step.toggle());
        });
    }
}
