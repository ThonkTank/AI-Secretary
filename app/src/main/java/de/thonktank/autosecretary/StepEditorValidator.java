package de.thonktank.autosecretary;

import java.util.*;

/** Shared validation for the wizard page and Today quick editor. */
public final class StepEditorValidator {
    private StepEditorValidator() { }
    public static Set<ValidationIssue> issues(EditorStepState step, boolean showCadence) {
        Set<ValidationIssue> issues = new LinkedHashSet<>();
        if (step.text.trim().isEmpty())
            issues.add(ValidationIssue.step(ValidationIssue.Field.STEP_TITLE, step.id));
        if (!step.prescription.amount.isValid())
            issues.add(ValidationIssue.step(ValidationIssue.Field.STEP_AMOUNT, step.id));
        if (showCadence && step.cadenceMode == StepCadenceMode.INTERVAL
                && (step.intervalDays == null || step.intervalDays < 2))
            issues.add(ValidationIssue.step(ValidationIssue.Field.STEP_INTERVAL, step.id));
        return Collections.unmodifiableSet(issues);
    }
}
