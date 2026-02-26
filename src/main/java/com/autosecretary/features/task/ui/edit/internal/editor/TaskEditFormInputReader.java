package com.autosecretary.features.task.ui.edit.internal.editor;

import com.autosecretary.shared.Period;
import com.autosecretary.features.task.data.TaskCore;

import java.time.LocalDate;
import java.time.LocalTime;
import com.autosecretary.shared.Priority;
import com.autosecretary.features.task.ui.edit.TaskEditPresenter;

public class TaskEditFormInputReader {

    private final TaskEditSectionBinder.BasicInfoViews basicInfoViews;
    private final TaskEditSectionBinder.SchedulingViews schedulingViews;
    private final TaskEditSectionBinder.RepetitionViews repetitionViews;
    private final TaskEditSectionBinder.ProgressViews progressViews;
    private final GoalSectionController goalSectionController;

    public TaskEditFormInputReader(
        TaskEditSectionBinder.BasicInfoViews basicInfoViews,
        TaskEditSectionBinder.SchedulingViews schedulingViews,
        TaskEditSectionBinder.RepetitionViews repetitionViews,
        TaskEditSectionBinder.ProgressViews progressViews,
        GoalSectionController goalSectionController
    ) {
        this.basicInfoViews = basicInfoViews;
        this.schedulingViews = schedulingViews;
        this.repetitionViews = repetitionViews;
        this.progressViews = progressViews;
        this.goalSectionController = goalSectionController;
    }

    private LocalDate parseDateSafe(String value) {
        try {
            return value == null || value.trim().isEmpty() ? null : LocalDate.parse(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private LocalTime parseTimeSafe(String value) {
        try {
            return value == null || value.trim().isEmpty() ? null : LocalTime.parse(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeNullableString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Integer parseIntegerNullable(String value) {
        try {
            return value == null || value.trim().isEmpty() ? null : Integer.parseInt(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    public TaskEditPresenter.FormInput read() {
        TaskEditPresenter.FormInput input = new TaskEditPresenter.FormInput();
        input.title = basicInfoViews.titleView.getText().toString();
        input.description = basicInfoViews.descriptionView.getText().toString();
        input.priority = TaskEditPresenter.coalesce(
            (Priority) basicInfoViews.priorityView.getSelectedItem(),
            TaskEditPresenter.InputDefaults.PRIORITY
        );

        String goalIconText = goalSectionController.getGoalIconView().getText() != null
            ? goalSectionController.getGoalIconView().getText().toString().trim()
            : "";
        input.goalIcon = goalIconText.isEmpty()
            ? TaskEditPresenter.InputDefaults.GOAL_ICON
            : goalIconText;
        input.goalColorHex = TaskEditPresenter.coalesce(
            goalSectionController.getSelectedGoalColorHex(),
            TaskEditPresenter.InputDefaults.GOAL_COLOR_HEX
        );

        input.schedulingType = TaskEditPresenter.coalesce(
            (TaskCore.SchedulingType) schedulingViews.schedulingTypeView.getSelectedItem(),
            TaskEditPresenter.InputDefaults.SCHEDULING_TYPE
        );
        input.fixedDate = parseDateSafe(schedulingViews.fixedDateView.getText().toString());
        input.fixedStart = parseTimeSafe(schedulingViews.fixedStartView.getText().toString());
        input.fixedEnd = parseTimeSafe(schedulingViews.fixedEndView.getText().toString());
        input.fixedDuration = parseIntegerNullable(schedulingViews.fixedDurationView.getText().toString());
        Integer parsedBudgetRequiredCents = parseIntegerNullable(schedulingViews.budgetRequiredCentsView.getText().toString());
        input.budgetRequiredCents = parsedBudgetRequiredCents != null && parsedBudgetRequiredCents > 0
            ? parsedBudgetRequiredCents
            : null;
        input.budgetAccountId = normalizeNullableString(schedulingViews.budgetAccountIdView.getText().toString());
        input.budgetCategoryId = normalizeNullableString(schedulingViews.budgetCategoryIdView.getText().toString());

        input.closeOnMiss = schedulingViews.closeOnMissView.isChecked();
        input.minDuration = TaskEditPresenter.parseIntSafe(
            schedulingViews.minDurationView.getText().toString(),
            TaskEditPresenter.InputDefaults.MIN_DURATION
        );
        input.maxDuration = TaskEditPresenter.parseIntSafe(
            schedulingViews.maxDurationView.getText().toString(),
            TaskEditPresenter.InputDefaults.MAX_DURATION
        );
        input.cooldown = TaskEditPresenter.parseIntSafe(
            schedulingViews.cooldownView.getText().toString(),
            TaskEditPresenter.InputDefaults.COOLDOWN
        );
        input.adaptive = schedulingViews.adaptiveView.isChecked();

        input.repetitionEnabled = repetitionViews.toggleRepetition.isChecked();
        input.reps = TaskEditPresenter.parseIntSafe(
            repetitionViews.repsView.getText().toString(),
            TaskEditPresenter.InputDefaults.REPETITION_REPS
        );
        input.perPeriod = TaskEditPresenter.parseIntSafe(
            repetitionViews.perPeriodView.getText().toString(),
            TaskEditPresenter.InputDefaults.REPETITION_PER_PERIOD
        );
        input.periodUnit = TaskEditPresenter.coalesce(
            (Period) repetitionViews.periodUnitView.getSelectedItem(),
            TaskEditPresenter.InputDefaults.REPETITION_PERIOD_UNIT
        );
        input.completeFirst = repetitionViews.completeFirstView.isChecked();

        input.progressEnabled = progressViews.toggleProgress.isChecked();
        input.unit = progressViews.unitView.getText().toString();
        input.target = TaskEditPresenter.parseIntSafe(
            progressViews.targetView.getText().toString(),
            TaskEditPresenter.InputDefaults.TARGET
        );
        input.current = TaskEditPresenter.parseIntSafe(
            progressViews.currentView.getText().toString(),
            TaskEditPresenter.InputDefaults.CURRENT
        );
        input.resetPerRep = progressViews.resetPerRepView.isChecked();
        input.minPerRep = TaskEditPresenter.parseIntSafe(
            progressViews.minPerRepView.getText().toString(),
            TaskEditPresenter.InputDefaults.MIN_PER_REP
        );
        input.maxPerRep = TaskEditPresenter.parseIntSafe(
            progressViews.maxPerRepView.getText().toString(),
            TaskEditPresenter.InputDefaults.MAX_PER_REP
        );

        return input;
    }
}
