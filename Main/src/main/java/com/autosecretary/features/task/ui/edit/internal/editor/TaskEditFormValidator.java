package com.autosecretary.features.task.ui.edit.internal.editor;

import android.content.Context;
import android.widget.EditText;

import com.autosecretary.R;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;
import com.autosecretary.shared.ui.SpinnerHelper;

import java.util.Arrays;

/**
 * Validates the task-edit form by reading the live view-group handles directly
 * and calling {@link android.widget.EditText#setError} inline on failing fields.
 *
 * <p>Validation is two-phase:
 * <ol>
 *   <li>Individual-field checks (non-empty, in-range integer) run first.
 *   <li>Cross-field checks (e.g. min &lt;= max) only run once all individual checks
 *       pass — this guarantees the values are parseable integers before the comparison.
 * </ol>
 */
public class TaskEditFormValidator {

    private final Context context;
    private final TaskEditSectionBinder.BasicInfoViews basicInfoViews;
    private final TaskEditSectionBinder.SchedulingViews schedulingViews;
    private final TaskEditSectionBinder.RepetitionViews repetitionViews;
    private final TaskEditSectionBinder.ProgressViews progressViews;
    private final TaskEditState editState;

    public TaskEditFormValidator(
        Context context,
        TaskEditSectionBinder.BasicInfoViews basicInfoViews,
        TaskEditSectionBinder.SchedulingViews schedulingViews,
        TaskEditSectionBinder.RepetitionViews repetitionViews,
        TaskEditSectionBinder.ProgressViews progressViews,
        TaskEditState editState
    ) {
        this.context = context;
        this.basicInfoViews = basicInfoViews;
        this.schedulingViews = schedulingViews;
        this.repetitionViews = repetitionViews;
        this.progressViews = progressViews;
        this.editState = editState;
    }

    /**
     * Runs all validation rules against the current view state and sets inline
     * errors on failing fields via {@link android.widget.EditText#setError}.
     *
     * @return {@code true} if all fields pass; {@code false} if any error was set.
     */
    public boolean validate() {
        clearErrors();
        boolean valid = validateIndividualFields();
        if (valid) valid = validateCrossFieldConstraints();
        return valid;
    }

    /** Phase 1: per-field checks (non-empty, parseable, in-range). */
    private boolean validateIndividualFields() {
        boolean valid = requireNonEmpty(basicInfoViews.titleView, R.string.task_edit_validation_title_required);

        valid &= validateIntegerField(schedulingViews.minDurationView, 1, Integer.MAX_VALUE,
            R.string.task_edit_validation_min_duration);
        valid &= validateIntegerField(schedulingViews.maxDurationView, 1, Integer.MAX_VALUE,
            R.string.task_edit_validation_max_duration);
        valid &= validateIntegerField(schedulingViews.cooldownView, 0, Integer.MAX_VALUE,
            R.string.task_edit_validation_cooldown);

        TaskCore.SchedulingType schedulingType = selectedSchedulingType();
        if (schedulingType == TaskCore.SchedulingType.TERMIN) {
            if (editState.fixedDate == null) {
                schedulingViews.fixedDateView.setError(context.getString(R.string.task_edit_validation_fixed_date_required));
                valid = false;
            }
            if (editState.fixedStart == null) {
                schedulingViews.fixedStartView.setError(context.getString(R.string.task_edit_validation_fixed_start_required));
                valid = false;
            }
            valid &= validateIntegerField(schedulingViews.fixedDurationView, 1, Integer.MAX_VALUE,
                    R.string.task_edit_validation_fixed_duration);
        }

        if (repetitionViews.toggleRepetition.isChecked()) {
            valid &= validateIntegerField(repetitionViews.repsView, 1, Integer.MAX_VALUE,
                R.string.task_edit_validation_reps);
            valid &= validateIntegerField(repetitionViews.perPeriodView, 1, Integer.MAX_VALUE,
                R.string.task_edit_validation_per_period);
        }

        if (progressViews.toggleProgress.isChecked()) {
            valid &= validateIntegerField(progressViews.targetView, 1, Integer.MAX_VALUE,
                R.string.task_edit_validation_target);
            valid &= validateIntegerField(progressViews.currentView, 0, Integer.MAX_VALUE,
                R.string.task_edit_validation_current);
            valid &= validateIntegerField(progressViews.minPerRepView, 0, Integer.MAX_VALUE,
                R.string.task_edit_validation_min_per_rep);
            valid &= validateIntegerField(progressViews.maxPerRepView, 0, Integer.MAX_VALUE,
                R.string.task_edit_validation_max_per_rep);
        }

        return valid;
    }

    /**
     * Phase 2: cross-field constraints. Only called when all individual fields are valid,
     * guaranteeing that values are parseable integers before comparison.
     */
    private boolean validateCrossFieldConstraints() {
        boolean valid = validateFirstNotAboveSecond(
            schedulingViews.minDurationView,
            schedulingViews.maxDurationView,
            R.string.task_edit_validation_duration_min_exceeds_max,
            R.string.task_edit_validation_duration_max_below_min
        );

        if (valid && progressViews.toggleProgress.isChecked()) {
            valid &= validateFirstNotAboveSecond(
                progressViews.minPerRepView,
                progressViews.maxPerRepView,
                R.string.task_edit_validation_per_rep_min_exceeds_max,
                R.string.task_edit_validation_per_rep_max_below_min
            );
            valid &= validateFirstNotAboveSecond(progressViews.currentView, progressViews.targetView,
                R.string.task_edit_validation_current_above_target,
                R.string.task_edit_validation_target_below_current);
        }

        if (selectedSchedulingType() == TaskCore.SchedulingType.TASK
                && editState.startDate != null
                && editState.deadline != null
                && editState.startDate.isAfter(editState.deadline)) {
            schedulingViews.startDateView.setError(context.getString(R.string.task_edit_validation_start_after_deadline));
            schedulingViews.deadlineView.setError(context.getString(R.string.task_edit_validation_deadline_before_start));
            valid = false;
        }

        return valid;
    }

    private void clearErrors() {
        for (EditText field : Arrays.asList(
            basicInfoViews.titleView,
            schedulingViews.startDateView, schedulingViews.fixedDateView,
            schedulingViews.fixedStartView, schedulingViews.fixedDurationView, schedulingViews.deadlineView,
            schedulingViews.minDurationView, schedulingViews.maxDurationView, schedulingViews.cooldownView,
            repetitionViews.repsView, repetitionViews.perPeriodView,
            progressViews.targetView, progressViews.currentView, progressViews.minPerRepView, progressViews.maxPerRepView
        )) {
            field.setError(null);
        }
    }

    private boolean requireNonEmpty(EditText field, int messageResId) {
        if (field.getText().toString().trim().isEmpty()) {
            field.setError(context.getString(messageResId));
            return false;
        }
        return true;
    }

    private boolean validateIntegerField(EditText field, int min, int max, int fieldMessageResId) {
        String value = field.getText().toString().trim();
        if (value.isEmpty()) {
            field.setError(context.getString(R.string.task_edit_validation_required_format,
                context.getString(fieldMessageResId)));
            return false;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                field.setError(context.getString(fieldMessageResId));
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            field.setError(context.getString(R.string.task_edit_validation_number_format,
                context.getString(fieldMessageResId)));
            return false;
        }
    }

    // Precondition: both fields have already passed validateIntegerField — parseable integers guaranteed.
    private boolean validateFirstNotAboveSecond(EditText fieldA, EditText fieldB,
                                                int errorResA, int errorResB) {
        int valueA = Integer.parseInt(fieldA.getText().toString().trim());
        int valueB = Integer.parseInt(fieldB.getText().toString().trim());
        if (valueA <= valueB) {
            return true;
        }
        fieldA.setError(context.getString(errorResA));
        fieldB.setError(context.getString(errorResB));
        return false;
    }

    private TaskCore.SchedulingType selectedSchedulingType() {
        TaskCore.SchedulingType selected = SpinnerHelper.enumAtPosition(
                schedulingViews.schedulingTypeView, TaskCore.SchedulingType.values());
        return selected != null ? selected : TaskCore.SchedulingType.TASK;
    }
}
