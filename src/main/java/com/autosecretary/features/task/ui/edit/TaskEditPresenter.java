package com.autosecretary.features.task.ui.edit;

import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPrefSlotFactory;
import com.autosecretary.features.task.ui.edit.internal.mapper.TaskEditStateMapper;
import com.autosecretary.features.task.ui.edit.state.PrefSlotEditState;
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
        newSlot.taskId = defaultSlot.taskId;
        newSlot.days = defaultSlot.days;
        newSlot.start = defaultSlot.start;
        return newSlot;
    }

    public int computeCurrentRepsPerDay(boolean repetitionEnabled, String repsText,
                                        String perPeriodText, Period periodUnit) {
        if (!repetitionEnabled) {
            return 1;
        }

        int reps = parseIntSafe(repsText, InputDefaults.REPETITION_REPS);
        int perPeriod = parseIntSafe(perPeriodText, InputDefaults.REPETITION_PER_PERIOD);
        Period safePeriodUnit = periodUnit != null ? periodUnit : InputDefaults.REPETITION_PERIOD_UNIT;
        int periodInDays = safePeriodUnit.value * perPeriod;
        if (periodInDays <= 0) {
            periodInDays = 1;
        }
        return (int) Math.ceil((double) reps / (double) periodInDays);
    }

    public void applyForm(FormInput input) {
        FormInput safeInput = input != null ? input : new FormInput();
        editState.title = safeInput.title;
        editState.description = safeInput.description;
        editState.priority = coalesce(safeInput.priority, InputDefaults.PRIORITY);
        editState.goalIcon = safeInput.goalIcon != null ? safeInput.goalIcon : InputDefaults.GOAL_ICON;
        editState.goalColorHex = safeInput.goalColorHex != null ? safeInput.goalColorHex : InputDefaults.GOAL_COLOR_HEX;
        editState.schedulingType = coalesce(safeInput.schedulingType, InputDefaults.SCHEDULING_TYPE);
        editState.fixedDate = safeInput.fixedDate;
        editState.fixedStart = safeInput.fixedStart;
        editState.fixedEnd = safeInput.fixedEnd;
        editState.fixedDuration = safeInput.fixedDuration;

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
        int newReps = input.reps;
        int newPerPeriod = input.perPeriod;
        Period newPeriodUnit = input.periodUnit;

        boolean periodChanged =
            newReps != editState.reps ||
            newPerPeriod != editState.perPeriod ||
            newPeriodUnit != editState.periodUnit;

        editState.reps = newReps;
        editState.perPeriod = newPerPeriod;
        editState.periodUnit = newPeriodUnit;

        if (periodChanged || editState.periodStart == null) {
            editState.periodStart = LocalDate.now();
            editState.periodCompletions = 0;
        }
    }

    private void resetRepetition() {
        editState.reps = 0;
        editState.perPeriod = 1;
        editState.periodUnit = Period.DAY;
        editState.periodCompletions = 0;
        editState.periodStart = null;
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
        editState.target = 0;
    }

    /** Maps the current edit state back onto a base Task for DB persistence. */
    public Task toTaskForSave(Task baseTask) {
        return mapper.toTask(editState, baseTask);
    }

    public static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    public static <T> T coalesce(T value, T fallback) {
        return value != null ? value : fallback;
    }

    /** Fallback values for empty or invalid form fields. */
    public static class InputDefaults {
        public static final Priority PRIORITY = Priority.MEDIUM;

        public static final TaskCore.SchedulingType SCHEDULING_TYPE = TaskCore.SchedulingType.TASK;

        public static final int MIN_DURATION = 5;
        public static final int MAX_DURATION = 10;
        public static final int COOLDOWN = 1;

        public static final int REPETITION_REPS = 1;
        public static final int REPETITION_PER_PERIOD = 1;
        public static final Period REPETITION_PERIOD_UNIT = Period.DAY;

        public static final String GOAL_ICON = TaskCore.DEFAULT_GOAL_ICON;
        public static final String GOAL_COLOR_HEX = TaskCore.DEFAULT_GOAL_COLOR_HEX;

        public static final String UNIT = "";
        public static final int TARGET = 0;
        public static final int CURRENT = 0;
        public static final int MIN_PER_REP = 0;
        public static final int MAX_PER_REP = 0;
    }

    /** Raw form field values collected from the dialog UI. */
    public static class FormInput {
        public String title;
        public String description;
        public Priority priority = InputDefaults.PRIORITY;
        public String goalIcon = InputDefaults.GOAL_ICON;
        public String goalColorHex = InputDefaults.GOAL_COLOR_HEX;
        public TaskCore.SchedulingType schedulingType = InputDefaults.SCHEDULING_TYPE;
        public LocalDate fixedDate;
        public LocalTime fixedStart;
        public LocalTime fixedEnd;
        public Integer fixedDuration;

        public boolean closeOnMiss;
        public int minDuration = InputDefaults.MIN_DURATION;
        public int maxDuration = InputDefaults.MAX_DURATION;
        public int cooldown = InputDefaults.COOLDOWN;
        public boolean adaptive;

        public boolean repetitionEnabled;
        public int reps = InputDefaults.REPETITION_REPS;
        public int perPeriod = InputDefaults.REPETITION_PER_PERIOD;
        public Period periodUnit = InputDefaults.REPETITION_PERIOD_UNIT;

        public boolean progressEnabled;
        public String unit;
        public int target = InputDefaults.TARGET;
        public int current = InputDefaults.CURRENT;
        public boolean resetPerRep;
        public int minPerRep = InputDefaults.MIN_PER_REP;
        public int maxPerRep = InputDefaults.MAX_PER_REP;
    }
}
