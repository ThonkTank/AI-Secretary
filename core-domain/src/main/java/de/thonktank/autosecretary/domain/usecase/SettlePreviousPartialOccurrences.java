package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceKind;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.repository.DashboardReadRepository;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.ComboObligationRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Idempotently closes and harvests positive work left open across a day boundary. */
public final class SettlePreviousPartialOccurrences {
    private final DashboardReadRepository dashboard;
    private final OccurrenceExecutionRepository occurrences;
    private final RewardLedgerRepository rewards;
    private final Clock clock;
    private final FinishStepForToday finishStep;
    private final OccurrenceCompletionService completion;

    public SettlePreviousPartialOccurrences(DashboardReadRepository dashboard,
            OccurrenceExecutionRepository occurrences, RewardLedgerRepository rewards, ComboObligationRepository obligations,
            TransactionRunner transactions, Clock clock,
            ComboPolicySource policies) {
        this.dashboard = dashboard;
        this.occurrences = occurrences;
        this.rewards = rewards;
        this.clock = clock;
        finishStep = new FinishStepForToday(occurrences, rewards, obligations, transactions, clock, policies);
        completion = new OccurrenceCompletionService(occurrences, rewards, obligations, transactions, clock,
                policies);
    }

    public boolean execute() {
        boolean changed = false;
        for (Occurrence occurrence : dashboard.openOccurrences()) {
            if (occurrence.kind == OccurrenceKind.FLOW_SHEET) continue;
            if (!occurrence.scheduledOn.isBefore(clock.today())) continue;
            for (OccurrenceStep step : occurrences.occurrenceSteps(occurrence.id)) {
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
        for (RewardBooking booking : rewards.rewardBookings(occurrenceId))
            if (booking.target == RewardBooking.Target.VESSEL) total += booking.xpDelta;
        return total;
    }
}
