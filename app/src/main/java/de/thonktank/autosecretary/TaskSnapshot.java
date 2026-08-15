package de.thonktank.autosecretary;

import androidx.annotation.NonNull;

/** Read model shared by the activity and home-screen widget. */
public final class TaskSnapshot {
    @NonNull public final String taskId;
    @NonNull public final String occurrenceId;
    @NonNull public final String title;
    @NonNull public final String slot;
    @NonNull public final String nextAction;
    public final int remainingSteps;
    public final boolean terminalCondition;
    TaskSnapshot(String taskId, String occurrenceId, String title, String slot, String nextAction, int remainingSteps, boolean terminalCondition) {
        this.taskId = taskId; this.occurrenceId = occurrenceId; this.title = title; this.slot = slot;
        this.nextAction = nextAction; this.remainingSteps = remainingSteps; this.terminalCondition = terminalCondition;
    }
}
