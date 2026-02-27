package com.autosecretary.features.task.ui.edit;

import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPrefSlotFactory;
import com.autosecretary.features.task.ui.edit.internal.mapper.TaskEditStateMapper;
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

    /** Triggers prefSlot count recalculation when repetition fields change. */
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
        editState.goalIcon = safeInput.goalIcon != null ? safeInput.goalIcon : TaskEditDefaults.GOAL_ICON;
        editState.goalColorHex = safeInput.goalColorHex != null ? safeInput.goalColorHex : TaskEditDefaults.GOAL_COLOR_HEX;
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

    /** Raw form field values collected from the dialog UI. */
    public static class FormInput {
        public String title;
        public String description;
        public Priority priority = TaskEditDefaults.PRIORITY;
        public String goalIcon = TaskEditDefaults.GOAL_ICON;
        public String goalColorHex = TaskEditDefaults.GOAL_COLOR_HEX;
        public TaskCore.SchedulingType schedulingType = TaskEditDefaults.SCHEDULING_TYPE;
        public LocalDate fixedDate;
        public LocalTime fixedStart;
        public LocalTime fixedEnd;
        public Integer fixedDuration;
        public Integer budgetRequiredCents;
        public String budgetAccountId;
        public String budgetCategoryId;

        public boolean closeOnMiss;
        public int minDuration = TaskEditDefaults.MIN_DURATION;
        public int maxDuration = TaskEditDefaults.MAX_DURATION;
        public int cooldown = TaskEditDefaults.COOLDOWN;
        public boolean adaptive;

        public boolean repetitionEnabled;
        public int reps = TaskEditDefaults.REPETITION_REPS;
        public int perPeriod = TaskEditDefaults.REPETITION_PER_PERIOD;
        public Period periodUnit = TaskEditDefaults.REPETITION_PERIOD_UNIT;
        public boolean completeFirst;

        public boolean progressEnabled;
        public String unit;
        public int target = TaskEditDefaults.TARGET;
        public int current = TaskEditDefaults.CURRENT;
        public boolean resetPerRep;
        public int minPerRep = TaskEditDefaults.MIN_PER_REP;
        public int maxPerRep = TaskEditDefaults.MAX_PER_REP;
    }
}
