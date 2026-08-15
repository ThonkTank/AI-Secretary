package de.thonktank.autosecretary;

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
    @NonNull public final List<TaskStepSnapshot> steps;
    public final int remainingSteps;
    public final boolean terminalCondition;
    public final boolean ongoing;
    public final boolean done;
    public final boolean overdue;
    public final int ringWeeks;
    public final long displayOrder;

    public TaskSnapshot(@NonNull String taskId, @NonNull String occurrenceId, @NonNull String title,
                 @NonNull TaskSlot slot, @NonNull String softTime, @NonNull String nextAction,
                 @NonNull Recurrence recurrence, @NonNull List<TaskStepSnapshot> steps,
                 int remainingSteps, boolean terminalCondition, boolean ongoing, boolean done,
                 boolean overdue, int ringWeeks, long displayOrder) {
        this.taskId = taskId; this.occurrenceId = occurrenceId; this.title = title; this.slot = slot;
        this.softTime = softTime; this.nextAction = nextAction; this.recurrence = recurrence;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps)); this.remainingSteps = remainingSteps; this.terminalCondition = terminalCondition;
        this.ongoing = ongoing; this.done = done; this.overdue = overdue; this.ringWeeks = ringWeeks;
        this.displayOrder = displayOrder;
    }

    public boolean routine() { return recurrence != Recurrence.ONCE; }
    public String actionLabel(android.content.Context context) {
        if (terminalCondition) return context.getString(R.string.condition_met);
        if (steps.isEmpty()) return context.getString(R.string.action_complete);
        return context.getString(remainingSteps == 0
                ? R.string.action_complete_all : R.string.action_complete_rest);
    }
}
