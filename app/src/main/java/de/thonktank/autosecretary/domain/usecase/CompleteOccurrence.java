package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.util.ArrayList;
import de.thonktank.autosecretary.domain.model.RewardBooking;

public final class CompleteOccurrence {
    private final TaskRepository repository;
    private final RewardEngine rewards;

    public CompleteOccurrence(TaskRepository repository, Clock clock) {
        this.repository = repository;
        this.rewards = new RewardEngine(repository, clock);
    }

    public RewardReceipt execute(String occurrenceId) {
        if (occurrenceId == null || occurrenceId.isEmpty()) return RewardReceipt.none();
        final RewardReceipt[] result = {RewardReceipt.none()};
        final String transactionId = RewardEngine.newTransactionId();
        repository.inTransaction(() -> {
            Occurrence occurrence = repository.findOccurrence(occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN) return;
            Task task = repository.findTask(occurrence.taskId);
            if (task == null) return;
            ArrayList<RewardBooking> bookings = new ArrayList<>();
            for (de.thonktank.autosecretary.domain.model.OccurrenceStep step
                    : repository.occurrenceSteps(occurrenceId))
                if (!step.done) bookings.addAll(rewards.completeStep(occurrence, step,
                        transactionId).bookings);
            bookings.addAll(rewards.harvest(repository.findOccurrence(occurrenceId), task,
                    transactionId).bookings);
            result[0] = RewardReceipt.of(transactionId, bookings, RewardReceipt.Target.HEAD);
        });
        return result[0];
    }
}
