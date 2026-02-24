package com.autosecretary.views.taskTab;

import com.autosecretary.constants.Period;
import com.autosecretary.constants.Priority;
import com.autosecretary.database.task.Task;
import com.autosecretary.views.taskTab.mapper.TaskEditStateMapper;
import com.autosecretary.views.taskTab.model.PrefSlotEditState;
import com.autosecretary.views.taskTab.model.TaskEditState;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
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
                PrefSlotEditState newSlot = new PrefSlotEditState();
                newSlot.taskId = editState.id;
                newSlot.days = EnumSet.allOf(DayOfWeek.class);
                newSlot.start = LocalTime.of(6, 0);
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

        int reps = parseIntSafe(repsText, 1);
        int perPeriod = parseIntSafe(perPeriodText, 1);
        Period safePeriodUnit = periodUnit != null ? periodUnit : Period.DAY;
        int periodInDays = safePeriodUnit.value * perPeriod;
        if (periodInDays <= 0) {
            periodInDays = 1;
        }
        return (int) Math.ceil((double) reps / (double) periodInDays);
    }

    public void collectAllFields(FormData formData) {
        editState.title = formData.title;
        editState.description = formData.description;
        editState.priority = formData.priority != null ? formData.priority : Priority.MEDIUM;

        editState.closeOnMiss = formData.closeOnMiss;
        editState.minDuration = parseIntSafe(formData.minDuration, 5);
        editState.maxDuration = parseIntSafe(formData.maxDuration, 10);
        editState.cooldown = parseIntSafe(formData.cooldown, 1);
        editState.adaptive = formData.adaptive;

        if (formData.repetitionEnabled) {
            int newReps = parseIntSafe(formData.reps, 1);
            int newPerPeriod = parseIntSafe(formData.perPeriod, 1);
            Period newPeriodUnit = formData.periodUnit != null ? formData.periodUnit : Period.DAY;

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

        if (formData.progressEnabled) {
            editState.unit = formData.unit;
            editState.target = parseIntSafe(formData.target, 0);
            editState.current = parseIntSafe(formData.current, 0);
            editState.resetPerRep = formData.resetPerRep;
            editState.minPerRep = parseIntSafe(formData.minPerRep, 0);
            editState.maxPerRep = parseIntSafe(formData.maxPerRep, 0);
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

    public static class FormData {
        public String title;
        public String description;
        public Priority priority;

        public boolean closeOnMiss;
        public String minDuration;
        public String maxDuration;
        public String cooldown;
        public boolean adaptive;

        public boolean repetitionEnabled;
        public String reps;
        public String perPeriod;
        public Period periodUnit;

        public boolean progressEnabled;
        public String unit;
        public String target;
        public String current;
        public boolean resetPerRep;
        public String minPerRep;
        public String maxPerRep;
    }
}
