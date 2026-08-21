package de.thonktank.autosecretary.domain.today;

import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardReceipt;

/** Typed result for a step write that may legitimately have no reward booking. */
public final class StepExecutionResult {
    public enum Status {
        RECORDED,
        CORRECTED,
        COMPLETED,
        INVALID_STEP,
        OCCURRENCE_CLOSED,
        UNSUPPORTED
    }

    public final Status status;
    public final OccurrenceStep step;
    public final RewardReceipt rewardReceipt;
    public final int xp;

    public StepExecutionResult(Status status, OccurrenceStep step,
                               RewardReceipt rewardReceipt) {
        this.status = status;
        this.step = step;
        this.rewardReceipt = rewardReceipt == null ? RewardReceipt.none() : rewardReceipt;
        this.xp = this.rewardReceipt.xp;
    }

    public boolean changed() {
        return status == Status.RECORDED || status == Status.CORRECTED
                || status == Status.COMPLETED;
    }
}
