package com.autosecretary.views.taskTab;

import com.autosecretary.constants.Period;
import com.autosecretary.constants.Priority;
import com.autosecretary.database.task.Task;
import com.autosecretary.database.task.TaskPrefSlot;
import com.autosecretary.database.task.TaskPrefSlotFactory;
import com.autosecretary.views.taskTab.mapper.TaskEditStateMapper;
import com.autosecretary.views.taskTab.model.PrefSlotEditState;
import com.autosecretary.views.taskTab.model.TaskEditState;

import java.time.LocalDate;
import java.util.List;

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

    public boolean onRepetitionChanged(boolean repetitionEnabled, String repsText,
                                       String perPeriodText, Period periodUnit) {
        int newRepsPerDay = computeCurrentRepsPerDay(repetitionEnabled, repsText, perPeriodText, periodUnit);
        if (newRepsPerDay == lastRepsPerDay) {
            return false;
        }
        lastRepsPerDay = newRepsPerDay;

        int currentCount = editState.prefSlots.size();
        if (newRepsPerDay > currentCount) {
            for (int i = currentCount; i < newRepsPerDay; i++) {
                TaskPrefSlot defaultSlot = TaskPrefSlotFactory.createDefault(editState.id);
                PrefSlotEditState newSlot = new PrefSlotEditState();
                newSlot.taskId = defaultSlot.taskId;
                newSlot.days = defaultSlot.days;
                newSlot.start = defaultSlot.start;
                editState.prefSlots.add(newSlot);
            }
        } else if (newRepsPerDay < currentCount && newRepsPerDay > 0) {
            while (editState.prefSlots.size() > newRepsPerDay) {
                editState.prefSlots.remove(editState.prefSlots.size() - 1);
            }
        }
        return true;
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

    public void collectAllFields(FormData formData) {
        BasicInfoInput basicInfo = formData.basicInfo != null ? formData.basicInfo : new BasicInfoInput();
        ScheduleInput schedule = formData.schedule != null ? formData.schedule : new ScheduleInput();
        RepetitionInput repetition = formData.repetition != null ? formData.repetition : new RepetitionInput();
        ProgressInput progress = formData.progress != null ? formData.progress : new ProgressInput();

        editState.title = basicInfo.title;
        editState.description = basicInfo.description;
        editState.priority = basicInfo.priority;

        editState.closeOnMiss = schedule.closeOnMiss;
        editState.minDuration = schedule.minDuration;
        editState.maxDuration = schedule.maxDuration;
        editState.cooldown = schedule.cooldown;
        editState.adaptive = schedule.adaptive;

        if (repetition.enabled) {
            int newReps = repetition.reps;
            int newPerPeriod = repetition.perPeriod;
            Period newPeriodUnit = repetition.periodUnit;

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
        } else {
            editState.reps = 0;
            editState.perPeriod = 1;
            editState.periodUnit = Period.DAY;
            editState.periodCompletions = 0;
            editState.periodStart = null;
        }

        if (progress.enabled) {
            editState.unit = progress.unit;
            editState.target = progress.target;
            editState.current = progress.current;
            editState.resetPerRep = progress.resetPerRep;
            editState.minPerRep = progress.minPerRep;
            editState.maxPerRep = progress.maxPerRep;
        } else {
            editState.target = 0;
        }
    }

    public Task toTaskForSave(Task baseTask) {
        return mapper.toTask(editState, baseTask);
    }

    private static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    public static class InputDefaults {
        public static final Priority PRIORITY = Priority.MEDIUM;

        public static final int MIN_DURATION = 5;
        public static final int MAX_DURATION = 10;
        public static final int COOLDOWN = 1;

        public static final int REPETITION_REPS = 1;
        public static final int REPETITION_PER_PERIOD = 1;
        public static final Period REPETITION_PERIOD_UNIT = Period.DAY;

        public static final String UNIT = "";
        public static final int TARGET = 0;
        public static final int CURRENT = 0;
        public static final int MIN_PER_REP = 0;
        public static final int MAX_PER_REP = 0;
    }

    public static class FormData {
        public BasicInfoInput basicInfo;
        public ScheduleInput schedule;
        public RepetitionInput repetition;
        public ProgressInput progress;
    }

    public static class BasicInfoInput {
        public String title;
        public String description;
        public Priority priority = InputDefaults.PRIORITY;
    }

    public static class ScheduleInput {
        public boolean closeOnMiss;
        public int minDuration = InputDefaults.MIN_DURATION;
        public int maxDuration = InputDefaults.MAX_DURATION;
        public int cooldown = InputDefaults.COOLDOWN;
        public boolean adaptive;
    }

    public static class RepetitionInput {
        public boolean enabled;
        public int reps = InputDefaults.REPETITION_REPS;
        public int perPeriod = InputDefaults.REPETITION_PER_PERIOD;
        public Period periodUnit = InputDefaults.REPETITION_PERIOD_UNIT;
    }

    public static class ProgressInput {
        public boolean enabled;
        public String unit;
        public int target = InputDefaults.TARGET;
        public int current = InputDefaults.CURRENT;
        public boolean resetPerRep;
        public int minPerRep = InputDefaults.MIN_PER_REP;
        public int maxPerRep = InputDefaults.MAX_PER_REP;
    }
}
