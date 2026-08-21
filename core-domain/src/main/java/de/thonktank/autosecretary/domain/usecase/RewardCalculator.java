package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceKind;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.RewardPolicy;
import de.thonktank.autosecretary.domain.model.Task;

import java.time.LocalDate;

/** Pure reward policy adapter. It reads inputs and returns requested deltas without persistence. */
public final class RewardCalculator {
    public StepReward step(ComboProgress settledCombo, boolean onTime) {
        return new StepReward(RewardPolicy.stepXp(settledCombo), onTime ? 1 : 0);
    }

    public HarvestReward harvest(Task task, Occurrence occurrence, boolean routine,
                                 int collectedStepXp, ComboProgress settledCombo,
                                 LocalDate today) {
        if (routine) return new HarvestReward(
                RewardPolicy.routineXp(collectedStepXp, settledCombo), 3,
                RewardBooking.Kind.ROUTINE_HARVEST);
        long late = RewardPolicy.lateDays(task, occurrence, today);
        RewardBooking.Kind kind = occurrence.kind == OccurrenceKind.CONDITION
                ? RewardBooking.Kind.CONDITION_COMPLETION
                : RewardBooking.Kind.SINGLE_COMPLETION;
        return new HarvestReward(RewardPolicy.singleTaskXp(late, settledCombo),
                late == 0 ? 3 : -2, kind);
    }

    public static final class StepReward {
        public final int xp;
        public final int requestedComboDelta;
        StepReward(int xp, int requestedComboDelta) {
            this.xp = xp; this.requestedComboDelta = requestedComboDelta;
        }
    }

    public static final class HarvestReward {
        public final int xp;
        public final int requestedComboDelta;
        public final RewardBooking.Kind kind;
        HarvestReward(int xp, int requestedComboDelta, RewardBooking.Kind kind) {
            this.xp = xp; this.requestedComboDelta = requestedComboDelta; this.kind = kind;
        }
    }
}
