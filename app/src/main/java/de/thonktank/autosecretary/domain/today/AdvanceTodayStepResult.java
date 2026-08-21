package de.thonktank.autosecretary.domain.today;

import de.thonktank.autosecretary.domain.model.RewardReceipt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Complete outcome of advancing one visible Today step with its planned value. */
public final class AdvanceTodayStepResult {
    public enum Status {
        PROGRESS_RECORDED,
        STEP_COMPLETED,
        STEP_ALREADY_DONE,
        OCCURRENCE_CLOSED,
        INVALID_STEP,
        NO_PLANNED_VALUE
    }

    public final Status status;
    public final Integer recordedPlanValue;
    public final List<String> openStepIds;
    public final RewardReceipt rewardReceipt;
    public final int xp;

    public AdvanceTodayStepResult(Status status, Integer recordedPlanValue,
                                  List<String> openStepIds, RewardReceipt rewardReceipt) {
        this.status = status;
        this.recordedPlanValue = recordedPlanValue;
        this.openStepIds = Collections.unmodifiableList(new ArrayList<>(openStepIds));
        this.rewardReceipt = rewardReceipt == null ? RewardReceipt.none() : rewardReceipt;
        this.xp = this.rewardReceipt.xp;
    }

    public boolean completed() { return status == Status.STEP_COMPLETED; }
}
