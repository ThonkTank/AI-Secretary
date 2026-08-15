package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

public final class CompleteOccurrence {
    private final TaskRepository repository;
    private final Clock clock;
    private final OccurrenceCompletion completion;

    public CompleteOccurrence(TaskRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
        this.completion = new OccurrenceCompletion(repository);
    }

    public void execute(String occurrenceId) {
        if (occurrenceId == null || occurrenceId.isEmpty()) return;
        repository.inTransaction(() -> {
            Occurrence occurrence = repository.findOccurrence(occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN) return;
            Task task = repository.findTask(occurrence.taskId);
            if (task != null) completion.execute(occurrence, task, clock.today());
        });
    }
}
