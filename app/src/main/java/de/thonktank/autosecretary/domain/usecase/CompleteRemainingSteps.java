package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.util.ArrayList;
import de.thonktank.autosecretary.domain.model.RewardBooking;

public final class CompleteRemainingSteps {
    private final TaskRepository repository;
    private final RewardEngine rewards;
    public CompleteRemainingSteps(TaskRepository repository, Clock clock) {
        this.repository = repository; rewards = new RewardEngine(repository, clock);
    }
    public RewardReceipt execute(String occurrenceId) {
        final String transactionId = RewardEngine.newTransactionId();
        final ArrayList<RewardBooking> bookings = new ArrayList<>();
        repository.inTransaction(() -> {
            Occurrence occurrence = repository.findOccurrence(occurrenceId);
            if (occurrence == null) return;
            for (OccurrenceStep step : repository.occurrenceSteps(occurrenceId)) {
                if (step.done) continue;
                RewardReceipt receipt = rewards.completeStep(occurrence, step, transactionId);
                bookings.addAll(receipt.bookings);
            }
        });
        return RewardReceipt.of(transactionId, bookings, RewardReceipt.Target.VESSEL);
    }
}
