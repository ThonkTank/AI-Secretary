package de.thonktank.autosecretary;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;

public final class TaskEditorValidator {
    public static final String TITLE = "title";
    public static final String DURATION = "duration";
    public static final String WEEKDAYS = "weekdays";
    public static final String INTERVAL = "interval";
    public static final String TIMES = "times";
    public static final String BOUND = "bound";
    public static final String STEP_PREFIX = "step:";
    public static final String AMOUNT_PREFIX = "amount:";

    public enum Error { NONE, TITLE, WEEKDAYS, CONDITION }

    public Set<String> errors(EditorUiState draft, LocalDate today) {
        Set<String> errors = new LinkedHashSet<>();
        String title = draft.title == null ? "" : draft.title.trim();
        if (title.isEmpty() || title.length() > 120) errors.add(TITLE);
        if (draft.estimatedMinutes != null && draft.estimatedMinutes < 1) errors.add(DURATION);
        if (draft.recurrence == Recurrence.WEEKDAYS && draft.weekdayMask == 0)
            errors.add(WEEKDAYS);
        if (draft.recurrence == Recurrence.INTERVAL && draft.intervalDays < 1)
            errors.add(INTERVAL);
        if (draft.recurrence != Recurrence.ONCE && draft.timeOfDayMask == 0)
            errors.add(TIMES);
        if (draft.recurrence != Recurrence.ONCE) {
            if ((draft.boundKind == TaskBoundKind.UNTIL_DATE
                    || draft.boundKind == TaskBoundKind.FOR_WEEKS)
                    && (draft.boundUntilOn == null || draft.boundUntilOn.isBefore(today)))
                errors.add(BOUND);
            if (draft.boundKind == TaskBoundKind.FOR_WEEKS
                    && (draft.boundWeeks == null || draft.boundWeeks < 1)) errors.add(BOUND);
            if (draft.boundKind == TaskBoundKind.N_TIMES
                    && (draft.remainingCount == null || draft.remainingCount < 1)) errors.add(BOUND);
        } else if (draft.deadlineOn != null && draft.deadlineOn.isBefore(today)) {
            errors.add(BOUND);
        }
        for (EditorStepState step : draft.stepStates) {
            if (step.text.trim().isEmpty()) errors.add(STEP_PREFIX + step.id);
            boolean invalid = step.amountKind == StepAmountKind.SETS_REPS
                    && (notPositive(step.plannedSets) || notPositive(step.plannedReps));
            invalid |= step.amountKind == StepAmountKind.REPS && notPositive(step.plannedReps);
            invalid |= step.amountKind == StepAmountKind.DURATION
                    && notPositive(step.plannedDurationSeconds);
            if (invalid) errors.add(AMOUNT_PREFIX + step.id);
        }
        return Collections.unmodifiableSet(errors);
    }

    public Error validate(EditorUiState draft) {
        if (draft.title == null || draft.title.trim().isEmpty()) return Error.TITLE;
        if (draft.recurrence == Recurrence.WEEKDAYS && draft.weekdayMask == 0)
            return Error.WEEKDAYS;
        if (draft.ongoing && (draft.condition == null || draft.condition.trim().isEmpty()))
            return Error.CONDITION;
        return Error.NONE;
    }

    private static boolean notPositive(Integer value) { return value == null || value < 1; }
}
