package com.autosecretary.features.task.ui.edit;

import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.data.TaskCore;
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
 * and adjusts the prefSlot count), form input application ({@link #applyForm}), and Task
 * reconstitution for persistence ({@link #toTaskForSave}).
 */
public class TaskEditPresenter {

    private final TaskEditState editState;
    private final TaskEditStateMapper mapper;
    // -1 sentinel: uninitialized. Set by initializeRepetitionState() during form setup;
    // onRepetitionChanged() must not be called before initializeRepetitionState() runs.
    private int lastRepsPerDay = -1;

    public TaskEditPresenter(TaskEditState editState, TaskEditStateMapper mapper) {
        this.editState = editState;
        this.mapper = mapper;
    }

    public List<PrefSlotEditState> getEditablePrefSlots() {
        return editState.prefSlots;
    }

    public LocalDate getEditableDeadline() {
        return editState.deadline;
    }

    public void setEditableDeadline(LocalDate editableDeadline) {
        editState.deadline = editableDeadline;
    }

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

        int targetSlotCount = newRepsPerDay;
        int currentSlotCount = editState.prefSlots.size();
        if (targetSlotCount > currentSlotCount) {
            for (int i = currentSlotCount; i < targetSlotCount; i++) {
                editState.prefSlots.add(createDefaultPrefSlotState(editState.id));
            }
        } else if (targetSlotCount < currentSlotCount && targetSlotCount > 0) {
            editState.prefSlots.subList(targetSlotCount, currentSlotCount).clear();
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

    public int computeCurrentRepsPerDay(boolean repetitionEnabled, String repsText,
                                        String perPeriodText, Period periodUnit) {
        if (!repetitionEnabled) {
            return 1;
        }

        int reps = parseIntSafe(repsText, TaskEditDefaults.REPETITION_REPS);
        int perPeriod = parseIntSafe(perPeriodText, TaskEditDefaults.REPETITION_PER_PERIOD);
        Period safePeriodUnit = periodUnit != null ? periodUnit : TaskEditDefaults.REPETITION_PERIOD_UNIT;
        int periodInDays = safePeriodUnit.dayCount * perPeriod;
        if (periodInDays <= 0) {
            periodInDays = 1;
        }
        return (int) Math.ceil((double) reps / (double) periodInDays);
    }

    public void applyForm(FormInput input) {
        FormInput safeInput = input != null ? input : new FormInput();
        editState.title = safeInput.title;
        editState.description = safeInput.description;
        editState.priority = coalesce(safeInput.priority, TaskEditDefaults.PRIORITY);
        editState.goalIcon = coalesce(safeInput.goalIcon, TaskEditDefaults.GOAL_ICON);
        editState.goalColorHex = coalesce(safeInput.goalColorHex, TaskEditDefaults.GOAL_COLOR_HEX);
        editState.schedulingType = coalesce(safeInput.schedulingType, TaskEditDefaults.SCHEDULING_TYPE);
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

        updateOrResetRepetition(safeInput);
        updateOrResetProgress(safeInput);
    }

    private void updateOrResetRepetition(FormInput input) {
        if (input.repetitionEnabled) {
            updateRepetition(input);
            return;
        }
        resetRepetition();
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

    private void updateOrResetProgress(FormInput input) {
        if (input.progressEnabled) {
            updateProgress(input);
            return;
        }
        resetProgress();
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
        editState.unit = null;
        editState.target = 0;
        editState.current = 0;
        editState.resetPerRep = false;
        editState.minPerRep = 0;
        editState.maxPerRep = 0;
    }

    /** Maps the current edit state back onto a base Task for DB persistence. */
    public Task toTaskForSave(Task baseTask) {
        return mapper.toTask(editState, baseTask);
    }

    /** Parses a trimmed string to int, returning {@code fallback} on any failure. */
    public static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Returns {@code value} if non-null, otherwise {@code fallback}. */
    public static <T> T coalesce(T value, T fallback) {
        return value != null ? value : fallback;
    }

}
