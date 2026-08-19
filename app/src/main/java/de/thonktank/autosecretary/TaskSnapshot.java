package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.TaskStepUiModel;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;

/** Rich read model shared by the activity and the home-screen widget. */
public final class TaskSnapshot {
    @NonNull public final String taskId;
    @NonNull public final String occurrenceId;
    @NonNull public final String title;
    @NonNull public final TaskSlot slot;
    @NonNull public final String softTime;
    @NonNull public final String nextAction;
    @NonNull public final Recurrence recurrence;
    @NonNull public final List<TaskStepUiModel> steps;
    public final int remainingSteps;
    public final boolean terminalCondition;
    public final boolean ongoing;
    public final boolean done;
    public final boolean overdue;
    public final long displayOrder;
    public final int comboStage;
    public final int claimableXp;
    public final int collectedXp;
    public final int awardedXp;
    public final boolean harvestReady;
    /** True only while this completed occurrence can still be reversed from today's dashboard. */
    public final boolean undoAvailable;

    public TaskSnapshot(@NonNull String taskId, @NonNull String occurrenceId, @NonNull String title,
                 @NonNull TaskSlot slot, @NonNull String softTime, @NonNull String nextAction,
                 @NonNull Recurrence recurrence, @NonNull List<TaskStepUiModel> steps,
                 int remainingSteps, boolean terminalCondition, boolean ongoing, boolean done,
                 boolean overdue, int comboStage, long displayOrder) {
        this(taskId, occurrenceId, title, slot, softTime, nextAction, recurrence, steps,
                remainingSteps, terminalCondition, ongoing, done, overdue, comboStage,
                displayOrder, 10, 0, done ? 10 : 0, false);
    }

    public TaskSnapshot(@NonNull String taskId, @NonNull String occurrenceId,
                 @NonNull String title, @NonNull TaskSlot slot, @NonNull String softTime,
                 @NonNull String nextAction, @NonNull Recurrence recurrence,
                 @NonNull List<TaskStepUiModel> steps, int remainingSteps,
                 boolean terminalCondition, boolean ongoing, boolean done, boolean overdue,
                 int comboStage, long displayOrder, int claimableXp,
                 int collectedXp, int awardedXp, boolean harvestReady) {
        this(taskId, occurrenceId, title, slot, softTime, nextAction, recurrence, steps,
                remainingSteps, terminalCondition, ongoing, done, overdue, comboStage,
                displayOrder, claimableXp, collectedXp, awardedXp, harvestReady, false);
    }

    public TaskSnapshot(@NonNull String taskId, @NonNull String occurrenceId,
                 @NonNull String title, @NonNull TaskSlot slot, @NonNull String softTime,
                 @NonNull String nextAction, @NonNull Recurrence recurrence,
                 @NonNull List<TaskStepUiModel> steps, int remainingSteps,
                 boolean terminalCondition, boolean ongoing, boolean done, boolean overdue,
                 int comboStage, long displayOrder, int claimableXp,
                 int collectedXp, int awardedXp, boolean harvestReady, boolean undoAvailable) {
        this.taskId = taskId; this.occurrenceId = occurrenceId; this.title = title; this.slot = slot;
        this.softTime = softTime; this.nextAction = nextAction; this.recurrence = recurrence;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps)); this.remainingSteps = remainingSteps; this.terminalCondition = terminalCondition;
        this.ongoing = ongoing; this.done = done; this.overdue = overdue;
        this.displayOrder = displayOrder;
        this.comboStage = Math.max(0, comboStage);
        this.claimableXp = Math.max(0, claimableXp);
        this.collectedXp = Math.max(0, collectedXp);
        this.awardedXp = Math.max(0, awardedXp);
        this.harvestReady = harvestReady;
        this.undoAvailable = undoAvailable;
    }

    public boolean routine() { return recurrence != Recurrence.ONCE; }
    public String actionLabel(android.content.Context context) {
        if (terminalCondition) return context.getString(R.string.condition_met);
        if (steps.isEmpty()) return context.getString(R.string.action_complete);
        return context.getString(remainingSteps == 0
                ? R.string.action_complete_all : R.string.action_complete_rest);
    }
}
