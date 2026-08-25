package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.ApplicationTaskRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;

/** Idempotently closes and harvests positive work left open across a day boundary. */
public final class SettlePreviousPartialOccurrences {
    private final ApplicationTaskRepository repository;
    private final Clock clock;
    private final FinishStepForToday finishStep;
    private final OccurrenceCompletionService completion;

    public SettlePreviousPartialOccurrences(ApplicationTaskRepository repository, Clock clock,
                                            ComboPolicySource policies) {
        this.repository = repository;
        this.clock = clock;
        finishStep = new FinishStepForToday(repository, clock, policies);
        completion = new OccurrenceCompletionService(repository, clock, policies);
    }

    public boolean execute() {
        boolean changed = false;
        for (Occurrence occurrence : repository.openOccurrences()) {
            if (!occurrence.scheduledOn.isBefore(clock.today())) continue;
            for (OccurrenceStep step : repository.occurrenceSteps(occurrence.id)) {
                if (!step.done && positivePartial(step)) {
                    RewardReceipt receipt = finishStep.execute(step.id);
                    changed |= !receipt.bookings.isEmpty();
                }
            }
            if (vesselXp(occurrence.id) > 0) {
                RewardReceipt receipt = completion.harvestOccurrence(occurrence.id);
                changed |= !receipt.bookings.isEmpty();
            }
        }
        return changed;
    }

    private static boolean positivePartial(OccurrenceStep step) {
        if (step.repetitionProgress == null
                || step.repetitionProgress.actualRepetitions.isEmpty()) return false;
        int total = 0;
        for (Integer value : step.repetitionProgress.actualRepetitions) total += value;
        return total > 0;
    }

    private int vesselXp(String occurrenceId) {
        int total = 0;
        for (RewardBooking booking : repository.rewardBookings(occurrenceId))
            if (booking.target == RewardBooking.Target.VESSEL) total += booking.xpDelta;
        return total;
    }
}
