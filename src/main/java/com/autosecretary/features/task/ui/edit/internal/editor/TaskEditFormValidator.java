package com.autosecretary.features.task.ui.edit.internal.editor;

import android.content.Context;
import android.widget.EditText;

import com.autosecretary.R;

public class TaskEditFormValidator {

    private final Context context;

    public TaskEditFormValidator(Context context) {
        this.context = context;
    }

    public boolean validate(TaskEditFormViews views) {
        clearErrors(views);

        boolean valid = true;
        valid &= requireNonEmpty(views.titleView, R.string.task_edit_validation_title_required);

        valid &= validateIntegerField(views.minDurationView, 1, Integer.MAX_VALUE,
            R.string.task_edit_validation_min_duration);
        valid &= validateIntegerField(views.maxDurationView, 1, Integer.MAX_VALUE,
            R.string.task_edit_validation_max_duration);
        valid &= validateIntegerField(views.cooldownView, 0, Integer.MAX_VALUE,
            R.string.task_edit_validation_cooldown);

        if (views.toggleRepetition.isChecked()) {
            valid &= validateIntegerField(views.repsView, 1, Integer.MAX_VALUE,
                R.string.task_edit_validation_reps);
            valid &= validateIntegerField(views.perPeriodView, 1, Integer.MAX_VALUE,
                R.string.task_edit_validation_per_period);
        }

        if (views.toggleProgress.isChecked()) {
            valid &= validateIntegerField(views.targetView, 1, Integer.MAX_VALUE,
                R.string.task_edit_validation_target);
            valid &= validateIntegerField(views.currentView, 0, Integer.MAX_VALUE,
                R.string.task_edit_validation_current);
            valid &= validateIntegerField(views.minPerRepView, 0, Integer.MAX_VALUE,
                R.string.task_edit_validation_min_per_rep);
            valid &= validateIntegerField(views.maxPerRepView, 0, Integer.MAX_VALUE,
                R.string.task_edit_validation_max_per_rep);
        }

        if (valid) {
            valid &= validateMinMaxPair(
                views.minDurationView,
                views.maxDurationView,
                R.string.task_edit_validation_duration_min_exceeds_max,
                R.string.task_edit_validation_duration_max_below_min
            );
        }

        if (valid && views.toggleProgress.isChecked()) {
            valid &= validateMinMaxPair(
                views.minPerRepView,
                views.maxPerRepView,
                R.string.task_edit_validation_per_rep_min_exceeds_max,
                R.string.task_edit_validation_per_rep_max_below_min
            );
            valid &= validateCurrentNotAboveTarget(views.currentView, views.targetView);
        }

        return valid;
    }

    private void clearErrors(TaskEditFormViews views) {
        views.titleView.setError(null);
        views.minDurationView.setError(null);
        views.maxDurationView.setError(null);
        views.cooldownView.setError(null);
        views.repsView.setError(null);
        views.perPeriodView.setError(null);
        views.targetView.setError(null);
        views.currentView.setError(null);
        views.minPerRepView.setError(null);
        views.maxPerRepView.setError(null);
    }

    private boolean requireNonEmpty(EditText field, int messageResId) {
        if (field.getText() == null || field.getText().toString().trim().isEmpty()) {
            field.setError(context.getString(messageResId));
            return false;
        }
        return true;
    }

    private boolean validateIntegerField(EditText field, int min, int max, int rangeMessageResId) {
        String value = field.getText() != null ? field.getText().toString().trim() : "";
        if (value.isEmpty()) {
            field.setError(context.getString(R.string.task_edit_validation_required_format,
                context.getString(rangeMessageResId)));
            return false;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                field.setError(context.getString(rangeMessageResId));
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            field.setError(context.getString(R.string.task_edit_validation_number_format,
                context.getString(rangeMessageResId)));
            return false;
        }
    }

    private boolean validateMinMaxPair(EditText minField, EditText maxField,
                                       int minMessageResId, int maxMessageResId) {
        return validateFieldComparison(minField, maxField,
            (a, b) -> a <= b, minMessageResId, maxMessageResId);
    }

    private boolean validateCurrentNotAboveTarget(EditText currentView, EditText targetView) {
        return validateFieldComparison(currentView, targetView,
            (a, b) -> a <= b,
            R.string.task_edit_validation_current_above_target,
            R.string.task_edit_validation_target_below_current);
    }

    private boolean validateFieldComparison(EditText fieldA, EditText fieldB,
                                             java.util.function.BiPredicate<Integer, Integer> valid,
                                             int errorResA, int errorResB) {
        try {
            int valueA = Integer.parseInt(fieldA.getText().toString().trim());
            int valueB = Integer.parseInt(fieldB.getText().toString().trim());
            if (valid.test(valueA, valueB)) {
                return true;
            }
            fieldA.setError(context.getString(errorResA));
            fieldB.setError(context.getString(errorResB));
            return false;
        } catch (NumberFormatException e) {
            fieldA.setError(context.getString(R.string.task_edit_validation_number_format,
                context.getString(errorResA)));
            fieldB.setError(context.getString(R.string.task_edit_validation_number_format,
                context.getString(errorResB)));
            return false;
        }
    }
}
