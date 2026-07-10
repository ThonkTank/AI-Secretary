package com.autosecretary.features.task.ui.edit;

import com.autosecretary.shared.Period;
import com.autosecretary.shared.ui.DialogValidation;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.data.TaskPrefSlotFactory;
import com.autosecretary.features.task.ui.edit.internal.TaskEditStateMapper;
import com.autosecretary.features.task.ui.edit.state.PrefSlotEditState;
import com.autosecretary.features.task.ui.edit.state.TaskEditDefaults;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Form logic coordinator for {@link com.autosecretary.features.task.ui.edit.TaskEditDialog TaskEditDialog}.
 * Handles repetition-to-prefSlot reactivity ({@link #onRepetitionChanged} recalculates repsPerDay
 * and adjusts the prefSlot count), and Task reconstitution for persistence ({@link #toTaskForSave}).
 */
public class TaskEditPresenter {

    private final TaskEditState editState;
    // -1 sentinel: uninitialized. Set by initializeRepetitionState() during form setup;
    // onRepetitionChanged() must not be called before initializeRepetitionState() runs.
    private int lastRepsPerDay = -1;

    public TaskEditPresenter(TaskEditState editState) {
        this.editState = editState;
    }

    /** Returns the mutable list of preferred-slot edit states; used by the pref-slot UI controllers. */
    public List<PrefSlotEditState> getEditablePrefSlots() {
        return editState.prefSlots;
    }

    /** Returns the current deadline from the edit state, or null if unset. */
    public LocalDate getEditableDeadline() {
        return editState.deadline;
    }

    /** Updates the deadline in the edit state. Mutated separately from the main form-read flow. */
    public void setEditableDeadline(LocalDate editableDeadline) {
        editState.deadline = editableDeadline;
    }

    public LocalDate getEditableStartDate() {
        return editState.startDate;
    }

    public void setEditableStartDate(LocalDate editableStartDate) {
        editState.startDate = editableStartDate;
    }

    public LocalDate getEditableFixedDate() {
        return editState.fixedDate;
    }

    public void setEditableFixedDate(LocalDate editableFixedDate) {
        editState.fixedDate = editableFixedDate;
    }

    public LocalTime getEditableFixedStart() {
        return editState.fixedStart;
    }

    public void setEditableFixedStart(LocalTime editableFixedStart) {
        editState.fixedStart = editableFixedStart;
    }

    public TaskCore.SchedulingType getEditableSchedulingType() {
        return editState.schedulingType;
    }

    public void setEditableSchedulingType(TaskCore.SchedulingType schedulingType) {
        editState.schedulingType = schedulingType;
    }

    /**
     * Seeds the baseline repsPerDay after the form has been populated.
     * Must be called exactly once before any {@link #onRepetitionChanged} call, so the
     * first repetition-field event can correctly detect whether anything actually changed.
     */
    public void initializeRepetitionState(boolean repetitionEnabled, String repsText,
                                          String perPeriodText, Period periodUnit) {
        lastRepsPerDay = computeCurrentRepsPerDay(repetitionEnabled, repsText, perPeriodText, periodUnit);
    }

    /**
     * Recalculates repsPerDay from the current repetition field values and adjusts the
     * {@code prefSlots} list in the edit state to match (adding defaults or trimming excess).
     *
     * @return {@code true} if the effective repsPerDay changed (caller should rebuild the
     *         pref-slot UI); {@code false} if it is unchanged and no rebuild is needed.
     */
    public boolean onRepetitionChanged(boolean repetitionEnabled, String repsText,
                                       String perPeriodText, Period periodUnit) {
        int newRepsPerDay = computeCurrentRepsPerDay(repetitionEnabled, repsText, perPeriodText, periodUnit);
        if (newRepsPerDay == lastRepsPerDay) {
            return false;
        }
        lastRepsPerDay = newRepsPerDay;

        int currentSlotCount = editState.prefSlots.size();
        if (newRepsPerDay > currentSlotCount) {
            for (int i = currentSlotCount; i < newRepsPerDay; i++) {
                editState.prefSlots.add(createDefaultPrefSlotState(editState.id));
            }
        }
        return true;
    }

    private PrefSlotEditState createDefaultPrefSlotState(String taskId) {
        TaskPrefSlot defaultSlot = TaskPrefSlotFactory.createDefault(taskId);
        PrefSlotEditState newSlot = new PrefSlotEditState();
        newSlot.days = defaultSlot.days;
        newSlot.start = defaultSlot.start;
        return newSlot;
    }

    /**
     * Computes how many preferred-slot groups are needed per day based on repetition settings.
     * Returns {@code ceil(reps / periodInDays)}, or 1 if repetition is disabled.
     */
    public int computeCurrentRepsPerDay(boolean repetitionEnabled, String repsText,
                                        String perPeriodText, Period periodUnit) {
        if (!repetitionEnabled) {
            return 1;
        }

        int reps = DialogValidation.parseIntOrDefault(repsText, TaskEditDefaults.REPETITION_REPS);
        int perPeriod = DialogValidation.parseIntOrDefault(perPeriodText, TaskEditDefaults.REPETITION_PER_PERIOD);
        Period safePeriodUnit = periodUnit != null ? periodUnit : TaskEditDefaults.REPETITION_PERIOD_UNIT;
        int periodInDays = Math.max(1, safePeriodUnit.dayCount * perPeriod);
        // Ceiling ensures we create enough pref-slot groups to cover the heaviest day.
        // E.g. 3 reps / 2 days → 1.5 → ceil → 2 slot groups (one day needs 2 reps).
        // Floor would produce 1 group — not enough to schedule all reps.
        return (int) Math.ceil((double) reps / (double) periodInDays);
    }

    /** Maps the current edit state back onto a base Task for DB persistence. */
    public Task toTaskForSave(Task baseTask) {
        return TaskEditStateMapper.toTask(editState, baseTask);
    }

}
