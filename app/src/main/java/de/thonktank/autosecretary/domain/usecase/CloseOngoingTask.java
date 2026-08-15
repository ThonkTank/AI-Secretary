package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

public final class CloseOngoingTask {
    private final TaskRepository repository;
    private final Clock clock;
    private final OccurrenceCompletion completion;

    public CloseOngoingTask(TaskRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
        this.completion = new OccurrenceCompletion(repository);
    }

    public void execute(TaskId taskId) {
        repository.inTransaction(() -> {
            Task task = repository.findTask(taskId);
            if (task == null || !task.ongoing || task.conditionText.isEmpty() || task.conditionDone) return;
            Occurrence open = repository.openOccurrence(task.id);
            if (open != null) {
                completion.execute(open, task, clock.today());
                task = repository.findTask(task.id);
            } else {
                repository.setXp(repository.xp() + OccurrenceCompletion.XP_PER_COMPLETION);
            }
            repository.updateTask(task.closeCondition(clock.today()));
        });
    }
}
