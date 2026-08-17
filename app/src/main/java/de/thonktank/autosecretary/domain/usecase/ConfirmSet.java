package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

public final class ConfirmSet {
    private final TaskRepository repository;

    public ConfirmSet(TaskRepository repository) { this.repository = repository; }

    public void execute(String stepId, int repetitions) {
        repository.inTransaction(() -> {
            OccurrenceStep step = repository.findOccurrenceStep(stepId);
            if (step == null) return;
            Occurrence occurrence = repository.findOccurrence(step.occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN) return;
            repository.updateOccurrenceStep(step.confirmSet(repetitions));
        });
    }
}
