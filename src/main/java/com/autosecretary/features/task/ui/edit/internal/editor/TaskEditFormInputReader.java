package com.autosecretary.features.task.ui.edit.internal.editor;

import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;
import com.autosecretary.features.task.ui.edit.TaskEditPresenter;

import java.time.LocalDate;
import java.time.LocalTime;

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
    private final EditText budgetRequirementCentsView;
    private final CheckBox adaptiveView;
    private final CheckBox fixedAppointmentView;
    private final EditText fixedDateView;
    private final EditText fixedStartView;
    private final EditText fixedDurationView;

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
        EditText budgetRequirementCentsView,
        CheckBox adaptiveView,
        CheckBox fixedAppointmentView,
        EditText fixedDateView,
        EditText fixedStartView,
        EditText fixedDurationView,
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
        this.budgetRequirementCentsView = budgetRequirementCentsView;
        this.adaptiveView = adaptiveView;
        this.fixedAppointmentView = fixedAppointmentView;
        this.fixedDateView = fixedDateView;
        this.fixedStartView = fixedStartView;
        this.fixedDurationView = fixedDurationView;

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

    private LocalDate parseDateSafe(String value) {
        try {
            String[] parts = value.trim().split("\\.");
            if (parts.length != 3) return null;
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[2]);
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalTime parseTimeSafe(String value) {
        try {
            String[] parts = value.trim().split(":");
            if (parts.length != 2) return null;
            return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (Exception e) {
            return null;
        }
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
        input.budgetRequirementCents = parseLongSafe(
            budgetRequirementCentsView.getText().toString(),
            TaskEditPresenter.InputDefaults.BUDGET_REQUIREMENT_CENTS
        );
        input.adaptive = adaptiveView.isChecked();
        input.isFixedAppointment = fixedAppointmentView.isChecked();
        input.fixedDate = parseDateSafe(fixedDateView.getText() != null ? fixedDateView.getText().toString() : "");
        input.fixedStart = parseTimeSafe(fixedStartView.getText() != null ? fixedStartView.getText().toString() : "");
        input.fixedDurationMinutes = TaskEditPresenter.parseIntSafe(
            fixedDurationView.getText().toString(),
            TaskEditPresenter.InputDefaults.FIXED_DURATION_MINUTES
        );

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

    private long parseLongSafe(String s, long fallback) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}

