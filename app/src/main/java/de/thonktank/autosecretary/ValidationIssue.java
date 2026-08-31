package de.thonktank.autosecretary;

import android.os.Bundle;

import java.util.Objects;

/** Typed editor validation result, optionally scoped to one step draft. */
public final class ValidationIssue {
    public enum Field {
        TITLE(EditorUiState.Page.TITLE),
        DURATION(EditorUiState.Page.SCHEDULE),
        WEEKDAYS(EditorUiState.Page.SCHEDULE),
        INTERVAL(EditorUiState.Page.SCHEDULE),
        TIMES(EditorUiState.Page.SCHEDULE),
        BOUND(EditorUiState.Page.TITLE),
        STEP_TITLE(EditorUiState.Page.STEPS),
        STEP_AMOUNT(EditorUiState.Page.STEPS),
        TRAINING_LOAD(EditorUiState.Page.STEPS),
        STEP_INTERVAL(EditorUiState.Page.STEPS);

        public final EditorUiState.Page page;

        Field(EditorUiState.Page page) { this.page = page; }
    }

    public final Field field;
    public final String stepId;

    private ValidationIssue(Field field, String stepId) {
        this.field = Objects.requireNonNull(field, "field");
        this.stepId = stepId;
        if (isStepField(field) != (stepId != null && !stepId.isEmpty()))
            throw new IllegalArgumentException("Step validation issues require exactly one step id");
    }

    public static ValidationIssue task(Field field) { return new ValidationIssue(field, null); }

    public static ValidationIssue step(Field field, String stepId) {
        return new ValidationIssue(field, stepId);
    }

    public boolean belongsTo(EditorUiState.Page page) { return field.page == page; }

    public boolean belongsToStep(String id) { return Objects.equals(stepId, id); }

    Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString("field", field.name());
        bundle.putString("step_id", stepId);
        return bundle;
    }

    static ValidationIssue fromBundle(Bundle bundle) {
        if (bundle == null) return null;
        try {
            Field field = Field.valueOf(bundle.getString("field", ""));
            String stepId = bundle.getString("step_id");
            return isStepField(field) ? step(field, stepId) : task(field);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static boolean isStepField(Field field) {
        return field == Field.STEP_TITLE || field == Field.STEP_AMOUNT
                || field == Field.TRAINING_LOAD
                || field == Field.STEP_INTERVAL;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof ValidationIssue)) return false;
        ValidationIssue value = (ValidationIssue) other;
        return field == value.field && Objects.equals(stepId, value.stepId);
    }

    @Override public int hashCode() { return Objects.hash(field, stepId); }
}
