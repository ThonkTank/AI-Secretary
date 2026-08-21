package de.thonktank.autosecretary.presentation.today;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.RewardBreakdown;

/** Task projection containing exactly the data rendered by a Today timeline leaf. */
public final class TimelineTaskUiModel {
    public final TaskActionTarget actionTarget;
    public final String taskId;
    public final String occurrenceId;
    public final String title;
    public final TaskSlot slot;
    public final String softTime;
    public final List<TimelineStepUiModel> steps;
    public final boolean terminalCondition;
    public final boolean overdue;
    public final long displayOrder;
    public final int comboStage;
    public final RewardBreakdown reward;

    private TimelineTaskUiModel(TaskActionTarget actionTarget,
                                String taskId, String occurrenceId, String title, TaskSlot slot,
                                String softTime, List<TimelineStepUiModel> steps,
                                boolean terminalCondition, boolean overdue,
                                long displayOrder, RewardBreakdown reward) {
        if (actionTarget == null || taskId == null || taskId.isEmpty()
                || occurrenceId == null || title == null
                || title.trim().isEmpty() || slot == null || softTime == null || steps == null)
            throw new IllegalArgumentException("Timeline task content is required");
        this.actionTarget = actionTarget;
        this.taskId = taskId;
        this.occurrenceId = occurrenceId;
        this.title = title;
        this.slot = slot;
        this.softTime = softTime;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.terminalCondition = terminalCondition;
        this.overdue = overdue;
        this.displayOrder = displayOrder;
        if (reward == null) throw new IllegalArgumentException("Timeline reward is required");
        this.comboStage = reward.comboStage;
        this.reward = reward;
    }

    public static TimelineTaskUiModel of(TaskActionTarget actionTarget,
                                         String taskId, String occurrenceId, String title,
                                         TaskSlot slot, String softTime,
                                         List<TimelineStepUiModel> steps,
                                         boolean terminalCondition, boolean overdue,
                                         long displayOrder, RewardBreakdown reward) {
        return new TimelineTaskUiModel(actionTarget, taskId, occurrenceId, title, slot, softTime, steps,
                terminalCondition, overdue, displayOrder, reward);
    }

}
