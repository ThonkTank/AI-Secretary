package de.thonktank.autosecretary.presentation.today;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.domain.model.RewardBreakdown;
import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;

/** Task projection containing exactly the state rendered by the Today focus card. */
public final class FocusTaskUiModel {
    public final TaskActionTarget actionTarget;
    public final String nextAction;
    public final List<FocusStepUiModel> steps;
    public final int remainingSteps;
    public final boolean ongoing;
    public final boolean overdue;
    public final boolean allowDefer;
    public final boolean harvestReady;
    public final RewardBreakdown reward;
    public final int grainLevel;
    public final XpVesselUiModel vessel;
    public final int backlogCount;
    public final boolean flowAggregate;

    private FocusTaskUiModel(Builder builder) {
        if (builder.actionTarget == null || builder.nextAction == null || builder.steps == null
                || builder.reward == null || builder.vessel == null)
            throw new IllegalArgumentException("Complete focus task content is required");
        if (!builder.reward.equals(builder.vessel.reward))
            throw new IllegalArgumentException("Focus and vessel rewards must match");
        this.actionTarget = builder.actionTarget;
        this.nextAction = builder.nextAction;
        this.steps = Collections.unmodifiableList(new ArrayList<>(builder.steps));
        this.remainingSteps = Math.max(0, builder.remainingSteps);
        this.ongoing = builder.ongoing;
        this.overdue = builder.overdue;
        this.allowDefer = builder.allowDefer;
        this.harvestReady = builder.harvestReady;
        this.reward = builder.reward;
        this.grainLevel = builder.grainLevel == null
                ? builder.reward.comboStage : Math.max(0, builder.grainLevel);
        this.vessel = builder.vessel;
        this.backlogCount = Math.max(0, builder.backlogCount);
        this.flowAggregate = builder.flowAggregate;
    }

    public String taskId() { return actionTarget.taskId; }
    public String occurrenceId() { return actionTarget.occurrenceId; }
    public String title() { return actionTarget.title; }
    public boolean terminalCondition() { return actionTarget.terminalCondition; }

    public static Builder builder(TaskActionTarget target) { return new Builder(target); }

    public static final class Builder {
        private final TaskActionTarget actionTarget;
        private String nextAction = "";
        private List<FocusStepUiModel> steps = Collections.emptyList();
        private int remainingSteps;
        private boolean ongoing;
        private boolean overdue;
        private boolean allowDefer;
        private boolean harvestReady;
        private RewardBreakdown reward;
        private XpVesselUiModel vessel;
        private Integer grainLevel;
        private int backlogCount;
        private boolean flowAggregate;

        private Builder(TaskActionTarget target) { this.actionTarget = target; }

        public Builder nextAction(String value) { nextAction = value; return this; }
        public Builder steps(List<FocusStepUiModel> value, int remaining) {
            steps = value; remainingSteps = remaining; return this;
        }
        public Builder ongoing(boolean value) { ongoing = value; return this; }
        public Builder overdue(boolean value) { overdue = value; return this; }
        public Builder allowDefer(boolean value) { allowDefer = value; return this; }
        public Builder harvestReady(boolean value) { harvestReady = value; return this; }
        public Builder reward(RewardBreakdown value, XpVesselUiModel vesselModel) {
            reward = value; vessel = vesselModel; return this;
        }
        public Builder grainLevel(int value) { grainLevel = value; return this; }
        public Builder backlogCount(int value) { backlogCount = value; return this; }
        public Builder flowAggregate(boolean value) { flowAggregate = value; return this; }
        public FocusTaskUiModel build() { return new FocusTaskUiModel(this); }
    }
}
