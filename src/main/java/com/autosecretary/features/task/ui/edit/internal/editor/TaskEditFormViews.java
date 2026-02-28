package com.autosecretary.features.task.ui.edit.internal.editor;

import android.widget.CheckBox;
import android.widget.EditText;

import java.util.Arrays;
import java.util.List;

/**
 * A flat projection of the form-view handles that {@link TaskEditFormValidator} needs.
 *
 * <p>{@link TaskEditSectionBinder} returns four typed inner-view groups that group
 * fields by form section. This class extracts only the subset of fields that
 * require validation, so the validator doesn't need to take all four groups directly
 * or know which section each field belongs to.
 */
public class TaskEditFormViews {
    public final EditText titleView;
    public final EditText minDurationView, maxDurationView, cooldownView;
    public final CheckBox toggleRepetition;
    public final EditText repsView, perPeriodView;
    public final CheckBox toggleProgress;
    public final EditText targetView, currentView, minPerRepView, maxPerRepView;

    public TaskEditFormViews(
        TaskEditSectionBinder.BasicInfoViews basicInfoViews,
        TaskEditSectionBinder.SchedulingViews schedulingViews,
        TaskEditSectionBinder.RepetitionViews repetitionViews,
        TaskEditSectionBinder.ProgressViews progressViews
    ) {
        this.titleView = basicInfoViews.titleView;
        this.minDurationView = schedulingViews.minDurationView;
        this.maxDurationView = schedulingViews.maxDurationView;
        this.cooldownView = schedulingViews.cooldownView;
        this.toggleRepetition = repetitionViews.toggleRepetition;
        this.repsView = repetitionViews.repsView;
        this.perPeriodView = repetitionViews.perPeriodView;
        this.toggleProgress = progressViews.toggleProgress;
        this.targetView = progressViews.targetView;
        this.currentView = progressViews.currentView;
        this.minPerRepView = progressViews.minPerRepView;
        this.maxPerRepView = progressViews.maxPerRepView;
    }

    /**
     * Returns all {@link EditText} fields that the validator may call {@code setError} on.
     * Add new validated fields here — {@link TaskEditFormValidator#clearErrors} will pick
     * them up automatically without a separate change.
     */
    public List<EditText> getValidatableFields() {
        return Arrays.asList(
            titleView,
            minDurationView, maxDurationView, cooldownView,
            repsView, perPeriodView,
            targetView, currentView, minPerRepView, maxPerRepView
        );
    }
}
