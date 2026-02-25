package com.autosecretary.features.task.ui.edit.internal.editor;

import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;
import com.autosecretary.features.task.ui.edit.TaskEditPresenter;

public class TaskEditFormInputReader {

    private final EditText titleView;
    private final EditText descriptionView;
    private final Spinner priorityView;
    private final EditText goalIconView;
    private final GoalSectionController goalSectionController;

    private final CheckBox closeOnMissView;
    private final EditText minDurationView;
    private final EditText maxDurationView;
    private final EditText cooldownView;
    private final CheckBox adaptiveView;

    private final CheckBox toggleRepetition;
    private final EditText repsView;
    private final EditText perPeriodView;
    private final Spinner periodUnitView;

    private final CheckBox toggleProgress;
    private final EditText unitView;
    private final EditText targetView;
    private final EditText currentView;
    private final CheckBox resetPerRepView;
    private final EditText minPerRepView;
    private final EditText maxPerRepView;

    public TaskEditFormInputReader(
        EditText titleView,
        EditText descriptionView,
        Spinner priorityView,
        GoalSectionController goalSectionController,
        CheckBox closeOnMissView,
        EditText minDurationView,
        EditText maxDurationView,
        EditText cooldownView,
        CheckBox adaptiveView,
        CheckBox toggleRepetition,
        EditText repsView,
        EditText perPeriodView,
        Spinner periodUnitView,
        CheckBox toggleProgress,
        EditText unitView,
        EditText targetView,
        EditText currentView,
        CheckBox resetPerRepView,
        EditText minPerRepView,
        EditText maxPerRepView
    ) {
        this.titleView = titleView;
        this.descriptionView = descriptionView;
        this.priorityView = priorityView;
        this.goalSectionController = goalSectionController;
        this.goalIconView = goalSectionController.getGoalIconView();

        this.closeOnMissView = closeOnMissView;
        this.minDurationView = minDurationView;
        this.maxDurationView = maxDurationView;
        this.cooldownView = cooldownView;
        this.adaptiveView = adaptiveView;

        this.toggleRepetition = toggleRepetition;
        this.repsView = repsView;
        this.perPeriodView = perPeriodView;
        this.periodUnitView = periodUnitView;

        this.toggleProgress = toggleProgress;
        this.unitView = unitView;
        this.targetView = targetView;
        this.currentView = currentView;
        this.resetPerRepView = resetPerRepView;
        this.minPerRepView = minPerRepView;
        this.maxPerRepView = maxPerRepView;
    }

    public TaskEditPresenter.FormInput read() {
        TaskEditPresenter.FormInput input = new TaskEditPresenter.FormInput();
        input.title = titleView.getText().toString();
        input.description = descriptionView.getText().toString();
        input.priority = TaskEditPresenter.coalesce(
            (Priority) priorityView.getSelectedItem(),
            TaskEditPresenter.InputDefaults.PRIORITY
        );
        input.goalIcon = goalIconView.getText() != null && !goalIconView.getText().toString().trim().isEmpty()
            ? goalIconView.getText().toString().trim()
            : TaskEditPresenter.InputDefaults.GOAL_ICON;
        input.goalColorHex = TaskEditPresenter.coalesce(
            goalSectionController.getSelectedGoalColorHex(),
            TaskEditPresenter.InputDefaults.GOAL_COLOR_HEX
        );

        input.closeOnMiss = closeOnMissView.isChecked();
        input.minDuration = TaskEditPresenter.parseIntSafe(
            minDurationView.getText().toString(),
            TaskEditPresenter.InputDefaults.MIN_DURATION
        );
        input.maxDuration = TaskEditPresenter.parseIntSafe(
            maxDurationView.getText().toString(),
            TaskEditPresenter.InputDefaults.MAX_DURATION
        );
        input.cooldown = TaskEditPresenter.parseIntSafe(
            cooldownView.getText().toString(),
            TaskEditPresenter.InputDefaults.COOLDOWN
        );
        input.adaptive = adaptiveView.isChecked();

        input.repetitionEnabled = toggleRepetition.isChecked();
        input.reps = TaskEditPresenter.parseIntSafe(
            repsView.getText().toString(),
            TaskEditPresenter.InputDefaults.REPETITION_REPS
        );
        input.perPeriod = TaskEditPresenter.parseIntSafe(
            perPeriodView.getText().toString(),
            TaskEditPresenter.InputDefaults.REPETITION_PER_PERIOD
        );
        input.periodUnit = TaskEditPresenter.coalesce(
            (Period) periodUnitView.getSelectedItem(),
            TaskEditPresenter.InputDefaults.REPETITION_PERIOD_UNIT
        );

        input.progressEnabled = toggleProgress.isChecked();
        input.unit = unitView.getText().toString();
        input.target = TaskEditPresenter.parseIntSafe(
            targetView.getText().toString(),
            TaskEditPresenter.InputDefaults.TARGET
        );
        input.current = TaskEditPresenter.parseIntSafe(
            currentView.getText().toString(),
            TaskEditPresenter.InputDefaults.CURRENT
        );
        input.resetPerRep = resetPerRepView.isChecked();
        input.minPerRep = TaskEditPresenter.parseIntSafe(
            minPerRepView.getText().toString(),
            TaskEditPresenter.InputDefaults.MIN_PER_REP
        );
        input.maxPerRep = TaskEditPresenter.parseIntSafe(
            maxPerRepView.getText().toString(),
            TaskEditPresenter.InputDefaults.MAX_PER_REP
        );

        return input;
    }
}
