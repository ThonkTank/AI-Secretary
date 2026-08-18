package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

public final class CloseOngoingTask {
    private final TaskRepository repository;
    private final Clock clock;
    private final RewardEngine rewards;

    public CloseOngoingTask(TaskRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
        this.rewards = new RewardEngine(repository, clock);
    }

    public RewardReceipt execute(TaskId taskId) {
        final RewardReceipt[] result = {RewardReceipt.none()};
        repository.inTransaction(() -> {
            Task task = repository.findTask(taskId);
            if (task == null || !task.ongoing || task.conditionText.isEmpty() || task.conditionDone) return;
            Occurrence open = repository.openOccurrence(task.id);
            if (open == null) {
                open = new Occurrence("condition:" + task.id.value + ":" + clock.today(),
                        task.id, clock.today(), task.slot, OccurrenceState.OPEN,
                        Integer.MAX_VALUE, null);
                repository.insertOccurrence(open);
            }
            result[0] = rewards.harvest(open, task);
            task = repository.findTask(task.id);
            repository.updateTask(task.closeCondition(clock.today()));
        });
        return result[0];
    }
}
