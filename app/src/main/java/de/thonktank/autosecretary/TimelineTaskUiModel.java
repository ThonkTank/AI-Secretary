package de.thonktank.autosecretary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.domain.model.TaskSlot;

/** Task projection containing exactly the data rendered by a Today timeline leaf. */
public final class TimelineTaskUiModel {
    public final String taskId;
    public final String occurrenceId;
    public final String title;
    public final TaskSlot slot;
    public final String softTime;
    public final List<TimelineStepUiModel> steps;
    public final boolean terminalCondition;
    public final boolean done;
    public final boolean overdue;
    public final long displayOrder;
    public final int comboStage;
    public final int claimableXp;
    public final int awardedXp;
    public final boolean undoAvailable;

    private TimelineTaskUiModel(String taskId, String occurrenceId, String title, TaskSlot slot,
                                String softTime, List<TimelineStepUiModel> steps,
                                boolean terminalCondition, boolean done, boolean overdue,
                                long displayOrder, int comboStage, int claimableXp,
                                int awardedXp, boolean undoAvailable) {
        if (taskId == null || taskId.isEmpty() || occurrenceId == null || title == null
                || title.trim().isEmpty() || slot == null || softTime == null || steps == null)
            throw new IllegalArgumentException("Timeline task content is required");
        this.taskId = taskId;
        this.occurrenceId = occurrenceId;
        this.title = title;
        this.slot = slot;
        this.softTime = softTime;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.terminalCondition = terminalCondition;
        this.done = done;
        this.overdue = overdue;
        this.displayOrder = displayOrder;
        this.comboStage = Math.max(0, comboStage);
        this.claimableXp = Math.max(0, claimableXp);
        this.awardedXp = Math.max(0, awardedXp);
        this.undoAvailable = undoAvailable;
    }

    public static TimelineTaskUiModel of(String taskId, String occurrenceId, String title,
                                         TaskSlot slot, String softTime,
                                         List<TimelineStepUiModel> steps,
                                         boolean terminalCondition, boolean done,
                                         boolean overdue, long displayOrder, int comboStage,
                                         int claimableXp, int awardedXp,
                                         boolean undoAvailable) {
        return new TimelineTaskUiModel(taskId, occurrenceId, title, slot, softTime, steps,
                terminalCondition, done, overdue, displayOrder, comboStage, claimableXp,
                awardedXp, undoAvailable);
    }
}
