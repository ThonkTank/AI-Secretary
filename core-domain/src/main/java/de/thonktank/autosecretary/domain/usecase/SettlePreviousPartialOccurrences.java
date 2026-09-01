package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceKind;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Idempotently closes and harvests positive work left open across a day boundary. */
public final class SettlePreviousPartialOccurrences {
    private final StepRepository steps;
    private final TodayRepository today;
    private final Clock clock;
    private final FinishStepForToday finishStep;
    private final OccurrenceCompletionService completion;

    public SettlePreviousPartialOccurrences(CatalogRepository catalog, StepRepository steps,
            TodayRepository today,
            TransactionRunner transactions, Clock clock,
            ComboPolicySource policies) {
        this.steps = steps;
        this.today = today;
        this.clock = clock;
        finishStep = new FinishStepForToday(catalog, steps, today, transactions, clock, policies);
        completion = new OccurrenceCompletionService(catalog, steps, today, transactions, clock,
                policies);
    }

    public boolean execute() {
        boolean changed = false;
        for (Occurrence occurrence : today.openOccurrences()) {
            if (occurrence.kind == OccurrenceKind.FLOW_SHEET) continue;
            if (!occurrence.scheduledOn.isBefore(clock.today())) continue;
            for (OccurrenceStep step : steps.occurrenceSteps(occurrence.id)) {
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
        if (step.repetitionProgress == null || step.repetitionProgress.results.isEmpty())
            return false;
        int total = 0;
        for (de.thonktank.autosecretary.domain.model.SetResult value
                : step.repetitionProgress.results) total += value.repetitions;
        return total > 0;
    }

    private int vesselXp(String occurrenceId) {
        int total = 0;
        for (RewardBooking booking : today.rewardBookings(occurrenceId))
            if (booking.target == RewardBooking.Target.VESSEL) total += booking.xpDelta;
        return total;
    }
}
