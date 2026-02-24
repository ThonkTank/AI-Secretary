package com.autosecretary.views.taskTab;

import com.autosecretary.constants.Period;
import com.autosecretary.constants.Priority;
import com.autosecretary.database.task.Task;
import com.autosecretary.database.task.TaskPrefSlot;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class TaskEditPresenter {

    private final Task task;
    private final List<TaskPrefSlot> editablePrefSlots;
    private LocalDate editableDeadline;
    private int lastRepsPerDay = -1;

    public TaskEditPresenter(Task task) {
        this.task = task;
        this.editablePrefSlots = deepCopyPrefSlots(task.prefSlots);
        this.editableDeadline = task.core.deadline;
    }

    public List<TaskPrefSlot> getEditablePrefSlots() {
        return editablePrefSlots;
    }

    public LocalDate getEditableDeadline() {
        return editableDeadline;
    }

    public void setEditableDeadline(LocalDate editableDeadline) {
        this.editableDeadline = editableDeadline;
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

        int currentCount = editablePrefSlots.size();
        if (newRepsPerDay > currentCount) {
            for (int i = currentCount; i < newRepsPerDay; i++) {
                TaskPrefSlot newSlot = new TaskPrefSlot();
                newSlot.taskId = task.core.id;
                newSlot.days = EnumSet.allOf(DayOfWeek.class);
                newSlot.start = LocalTime.of(6, 0);
                editablePrefSlots.add(newSlot);
            }
        } else if (newRepsPerDay < currentCount && newRepsPerDay > 0) {
            while (editablePrefSlots.size() > newRepsPerDay) {
                editablePrefSlots.remove(editablePrefSlots.size() - 1);
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
        task.core.title = formData.title;
        task.core.description = formData.description;
        task.core.priority = formData.priority;

        task.core.deadline = editableDeadline;
        task.core.closeOnMiss = formData.closeOnMiss;
        task.core.minDuration = parseIntSafe(formData.minDuration, 5);
        task.core.maxDuration = parseIntSafe(formData.maxDuration, 10);
        task.core.cooldown = parseIntSafe(formData.cooldown, 1);
        task.core.adaptive = formData.adaptive;

        if (formData.repetitionEnabled) {
            int newReps = parseIntSafe(formData.reps, 1);
            int newPerPeriod = parseIntSafe(formData.perPeriod, 1);
            Period newPeriodUnit = formData.periodUnit;

            boolean periodChanged =
                newReps != task.core.repetition.reps ||
                newPerPeriod != task.core.repetition.perPeriod ||
                newPeriodUnit != task.core.repetition.periodUnit;

            task.core.repetition.reps = newReps;
            task.core.repetition.perPeriod = newPerPeriod;
            task.core.repetition.periodUnit = newPeriodUnit;

            if (periodChanged || task.core.repetition.periodStart == null) {
                task.core.repetition.periodStart = LocalDate.now();
                task.core.repetition.periodCompletions = 0;
            }
        } else {
            task.core.repetition.reps = 0;
            task.core.repetition.perPeriod = 1;
            task.core.repetition.periodUnit = Period.DAY;
            task.core.repetition.periodCompletions = 0;
            task.core.repetition.periodStart = null;
        }

        if (formData.progressEnabled) {
            task.core.progress.unit = formData.unit;
            task.core.progress.target = parseIntSafe(formData.target, 0);
            task.core.progress.current = parseIntSafe(formData.current, 0);
            task.core.progress.resetPerRep = formData.resetPerRep;
            task.core.progress.minPerRep = parseIntSafe(formData.minPerRep, 0);
            task.core.progress.maxPerRep = parseIntSafe(formData.maxPerRep, 0);
        } else {
            task.core.progress.target = 0;
        }

        task.prefSlots = new ArrayList<>(editablePrefSlots);
    }

    private static List<TaskPrefSlot> deepCopyPrefSlots(List<TaskPrefSlot> prefSlots) {
        List<TaskPrefSlot> editable = new ArrayList<>();
        if (prefSlots == null) {
            return editable;
        }

        for (TaskPrefSlot ps : prefSlots) {
            TaskPrefSlot copy = new TaskPrefSlot();
            copy.id = ps.id;
            copy.taskId = ps.taskId;
            copy.days = ps.days != null ? EnumSet.copyOf(ps.days) : EnumSet.noneOf(DayOfWeek.class);
            copy.start = ps.start;
            editable.add(copy);
        }
        return editable;
    }

    private static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
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
