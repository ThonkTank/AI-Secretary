package com.autosecretary.features.task.ui.internal.editor;

import com.autosecretary.constants.Period;
import com.autosecretary.constants.Priority;
import com.autosecretary.features.task.ui.TaskEditPresenter;

public class TaskEditFormInputBuilder {

    public TaskEditPresenter.FormInput buildFormInput(TaskEditFormViews formViews) {
        TaskEditPresenter.FormInput input = new TaskEditPresenter.FormInput();
        input.title = formViews.titleView.getText().toString();
        input.description = formViews.descriptionView.getText().toString();
        input.priority = TaskEditPresenter.coalesce(
            (Priority) formViews.priorityView.getSelectedItem(),
            TaskEditPresenter.InputDefaults.PRIORITY
        );

        input.closeOnMiss = formViews.closeOnMissView.isChecked();
        input.minDuration = TaskEditPresenter.parseIntSafe(
            formViews.minDurationView.getText().toString(),
            TaskEditPresenter.InputDefaults.MIN_DURATION
        );
        input.maxDuration = TaskEditPresenter.parseIntSafe(
            formViews.maxDurationView.getText().toString(),
            TaskEditPresenter.InputDefaults.MAX_DURATION
        );
        input.cooldown = TaskEditPresenter.parseIntSafe(
            formViews.cooldownView.getText().toString(),
            TaskEditPresenter.InputDefaults.COOLDOWN
        );
        input.adaptive = formViews.adaptiveView.isChecked();

        input.repetitionEnabled = formViews.toggleRepetition.isChecked();
        input.reps = TaskEditPresenter.parseIntSafe(
            formViews.repsView.getText().toString(),
            TaskEditPresenter.InputDefaults.REPETITION_REPS
        );
        input.perPeriod = TaskEditPresenter.parseIntSafe(
            formViews.perPeriodView.getText().toString(),
            TaskEditPresenter.InputDefaults.REPETITION_PER_PERIOD
        );
        input.periodUnit = TaskEditPresenter.coalesce(
            (Period) formViews.periodUnitView.getSelectedItem(),
            TaskEditPresenter.InputDefaults.REPETITION_PERIOD_UNIT
        );

        input.progressEnabled = formViews.toggleProgress.isChecked();
        input.unit = formViews.unitView.getText().toString();
        input.target = TaskEditPresenter.parseIntSafe(
            formViews.targetView.getText().toString(),
            TaskEditPresenter.InputDefaults.TARGET
        );
        input.current = TaskEditPresenter.parseIntSafe(
            formViews.currentView.getText().toString(),
            TaskEditPresenter.InputDefaults.CURRENT
        );
        input.resetPerRep = formViews.resetPerRepView.isChecked();
        input.minPerRep = TaskEditPresenter.parseIntSafe(
            formViews.minPerRepView.getText().toString(),
            TaskEditPresenter.InputDefaults.MIN_PER_REP
        );
        input.maxPerRep = TaskEditPresenter.parseIntSafe(
            formViews.maxPerRepView.getText().toString(),
            TaskEditPresenter.InputDefaults.MAX_PER_REP
        );

        return input;
    }
}
