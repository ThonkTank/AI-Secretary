package com.autosecretary.features.task.ui.edit;

import com.autosecretary.shared.Period;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.data.TaskPrefSlotFactory;
import com.autosecretary.features.task.ui.edit.internal.TaskEditStateMapper;
import com.autosecretary.features.task.ui.edit.state.PrefSlotEditState;
import com.autosecretary.features.task.ui.edit.state.TaskEditDefaults;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Form logic coordinator for {@link com.autosecretary.features.task.ui.edit.TaskEditDialog TaskEditDialog}.
 * Handles repetition-to-prefSlot reactivity ({@link #onRepetitionChanged} recalculates repsPerDay
 * and adjusts the prefSlot count), form input application ({@link #applyForm}), and Task
 * reconstitution for persistence ({@link #toTaskForSave}).
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

    /** Updates the deadline in the edit state. Mutated separately from the main FormInput flow. */
    public void setEditableDeadline(LocalDate editableDeadline) {
        editState.deadline = editableDeadline;
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
        } else if (newRepsPerDay < currentSlotCount && newRepsPerDay > 0) {
            editState.prefSlots.subList(newRepsPerDay, currentSlotCount).clear();
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

        int reps = parseIntSafe(repsText, TaskEditDefaults.REPETITION_REPS);
        int perPeriod = parseIntSafe(perPeriodText, TaskEditDefaults.REPETITION_PER_PERIOD);
        Period safePeriodUnit = periodUnit != null ? periodUnit : TaskEditDefaults.REPETITION_PERIOD_UNIT;
        int periodInDays = Math.max(1, safePeriodUnit.dayCount * perPeriod);
        // Ceiling ensures we create enough pref-slot groups to cover the heaviest day.
        // E.g. 3 reps / 2 days → 1.5 → ceil → 2 slot groups (one day needs 2 reps).
        // Floor would produce 1 group — not enough to schedule all reps.
        return (int) Math.ceil((double) reps / (double) periodInDays);
    }

    /** Merges all form field values from a {@link FormInput} into the edit state. Called on save. */
    public void applyForm(FormInput input) {
        FormInput safeInput = input != null ? input : new FormInput();
        editState.title = safeInput.title;
        editState.description = safeInput.description;
        editState.priority = Objects.requireNonNullElse(safeInput.priority, TaskEditDefaults.PRIORITY);
        editState.goalIcon = Objects.requireNonNullElse(safeInput.goalIcon, TaskEditDefaults.GOAL_ICON);
        editState.goalColorHex = Objects.requireNonNullElse(safeInput.goalColorHex, TaskEditDefaults.GOAL_COLOR_HEX);
        editState.schedulingType = Objects.requireNonNullElse(safeInput.schedulingType, TaskEditDefaults.SCHEDULING_TYPE);
        editState.fixedDate = safeInput.fixedDate;
        editState.fixedStart = safeInput.fixedStart;
        editState.fixedEnd = safeInput.fixedEnd;
        editState.fixedDuration = safeInput.fixedDuration;
        editState.budgetRequiredCents = safeInput.budgetRequiredCents;
        editState.budgetAccountId = safeInput.budgetAccountId;
        editState.budgetCategoryId = safeInput.budgetCategoryId;

        editState.closeOnMiss = safeInput.closeOnMiss;
        editState.minDuration = safeInput.minDuration;
        editState.maxDuration = safeInput.maxDuration;
        editState.cooldown = safeInput.cooldown;
        editState.adaptive = safeInput.adaptive;

        if (safeInput.repetitionEnabled) updateRepetition(safeInput); else resetRepetition();
        if (safeInput.progressEnabled)   updateProgress(safeInput);   else resetProgress();
    }

    private void updateRepetition(FormInput input) {
        boolean periodChanged =
            input.reps != editState.reps ||
            input.perPeriod != editState.perPeriod ||
            input.periodUnit != editState.periodUnit;

        editState.reps = input.reps;
        editState.perPeriod = input.perPeriod;
        editState.periodUnit = input.periodUnit;
        editState.completeFirst = input.completeFirst;

        if (periodChanged || editState.periodStart == null) {
            // Repetition schedule changed — old period counters are meaningless under the
            // new schedule, so reset them to start a fresh period from today.
            editState.periodStart = LocalDate.now();
            editState.periodCompletions = 0;
            editState.carryoverDebt = 0;
        }
    }

    private void resetRepetition() {
        editState.reps = 0;
        editState.perPeriod = 1;
        editState.periodUnit = Period.DAY;
        editState.periodCompletions = 0;
        editState.periodStart = null;
        editState.completeFirst = false;
        editState.carryoverDebt = 0;
    }

    private void updateProgress(FormInput input) {
        editState.unit = input.unit;
        editState.target = input.target;
        editState.current = input.current;
        editState.resetPerRep = input.resetPerRep;
        editState.minPerRep = input.minPerRep;
        editState.maxPerRep = input.maxPerRep;
    }

    private void resetProgress() {
        editState.unit = "";  // Empty string means no progress tracking (matches TaskEditDefaults.UNIT)
        editState.target = 0;
        editState.current = 0;
        editState.resetPerRep = false;
        editState.minPerRep = 0;
        editState.maxPerRep = 0;
    }

    /** Maps the current edit state back onto a base Task for DB persistence. */
    public Task toTaskForSave(Task baseTask) {
        return TaskEditStateMapper.toTask(editState, baseTask);
    }

    /** Parses a trimmed string to int, returning {@code fallback} if null, empty, or non-numeric. */
    public static int parseIntSafe(String s, int fallback) {
        if (s == null) return fallback;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

}
