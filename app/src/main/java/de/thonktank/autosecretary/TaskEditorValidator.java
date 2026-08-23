package de.thonktank.autosecretary;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;

public final class TaskEditorValidator {
    public Set<ValidationIssue> issues(EditorUiState draft, LocalDate today) {
        Set<ValidationIssue> issues = new LinkedHashSet<>();
        String title = draft.title == null ? "" : draft.title.trim();
        if (title.isEmpty() || title.length() > 120)
            issues.add(ValidationIssue.task(ValidationIssue.Field.TITLE));
        if (draft.estimatedMinutes != null && draft.estimatedMinutes < 1)
            issues.add(ValidationIssue.task(ValidationIssue.Field.DURATION));
        if (draft.recurrence == Recurrence.WEEKDAYS && draft.weekdayMask == 0)
            issues.add(ValidationIssue.task(ValidationIssue.Field.WEEKDAYS));
        if (draft.recurrence == Recurrence.INTERVAL && draft.intervalDays < 1)
            issues.add(ValidationIssue.task(ValidationIssue.Field.INTERVAL));
        if (draft.recurrence != Recurrence.ONCE && draft.timeOfDayMask == 0)
            issues.add(ValidationIssue.task(ValidationIssue.Field.TIMES));
        if (draft.recurrence != Recurrence.ONCE) {
            if ((draft.boundKind == TaskBoundKind.UNTIL_DATE
                    || draft.boundKind == TaskBoundKind.FOR_WEEKS)
                    && (draft.boundUntilOn == null || draft.boundUntilOn.isBefore(today)))
                issues.add(ValidationIssue.task(ValidationIssue.Field.BOUND));
            if (draft.boundKind == TaskBoundKind.FOR_WEEKS
                    && (draft.boundWeeks == null || draft.boundWeeks < 1))
                issues.add(ValidationIssue.task(ValidationIssue.Field.BOUND));
            if (draft.boundKind == TaskBoundKind.N_TIMES
                    && (draft.remainingCount == null || draft.remainingCount < 1))
                issues.add(ValidationIssue.task(ValidationIssue.Field.BOUND));
        } else if (draft.deadlineOn != null && draft.deadlineOn.isBefore(today)) {
            issues.add(ValidationIssue.task(ValidationIssue.Field.BOUND));
        }
        for (EditorStepState step : draft.stepStates) {
            if (step.text.trim().isEmpty())
                issues.add(ValidationIssue.step(ValidationIssue.Field.STEP_TITLE, step.id));
            if (!step.amount.isValid())
                issues.add(ValidationIssue.step(ValidationIssue.Field.STEP_AMOUNT, step.id));
            if (step.cadenceMode == StepCadenceMode.INTERVAL
                    && (step.intervalDays == null || step.intervalDays < 2))
                issues.add(ValidationIssue.step(ValidationIssue.Field.STEP_INTERVAL, step.id));
        }
        return Collections.unmodifiableSet(issues);
    }

}
