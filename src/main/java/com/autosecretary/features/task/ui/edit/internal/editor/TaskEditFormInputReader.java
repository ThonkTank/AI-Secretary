package com.autosecretary.features.task.ui.edit.internal.editor;

import com.autosecretary.shared.Period;
import com.autosecretary.features.task.data.TaskCore;

import java.time.LocalDate;
import java.time.LocalTime;
import com.autosecretary.shared.Priority;
import com.autosecretary.features.task.ui.edit.TaskEditPresenter;
import com.autosecretary.features.task.ui.edit.state.TaskEditDefaults;

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
        if (value == null) return null;
        String trimmed = value.trim();
        try {
            return trimmed.isEmpty() ? null : LocalDate.parse(trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalTime parseTimeSafe(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        try {
            return trimmed.isEmpty() ? null : LocalTime.parse(trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeNullableString(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Integer parseIntegerNullable(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        try {
            return trimmed.isEmpty() ? null : Integer.parseInt(trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseIntSafe(String s, int fallback) {
        return TaskEditPresenter.parseIntSafe(s, fallback);
    }

    private static <T> T coalesce(T value, T fallback) {
        return TaskEditPresenter.coalesce(value, fallback);
    }

    public TaskEditPresenter.FormInput read() {
        TaskEditPresenter.FormInput input = new TaskEditPresenter.FormInput();
        input.title = basicInfoViews.titleView.getText().toString();
        input.description = basicInfoViews.descriptionView.getText().toString();
        input.priority = coalesce(
            (Priority) basicInfoViews.priorityView.getSelectedItem(),
            TaskEditDefaults.PRIORITY
        );

        String goalIconText = goalSectionController.getGoalIconView().getText().toString().trim();
        input.goalIcon = goalIconText.isEmpty()
            ? TaskEditDefaults.GOAL_ICON
            : goalIconText;
        input.goalColorHex = coalesce(
            goalSectionController.getSelectedGoalColorHex(),
            TaskEditDefaults.GOAL_COLOR_HEX
        );

        input.schedulingType = coalesce(
            (TaskCore.SchedulingType) schedulingViews.schedulingTypeView.getSelectedItem(),
            TaskEditDefaults.SCHEDULING_TYPE
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
        input.minDuration = parseIntSafe(
            schedulingViews.minDurationView.getText().toString(),
            TaskEditDefaults.MIN_DURATION
        );
        input.maxDuration = parseIntSafe(
            schedulingViews.maxDurationView.getText().toString(),
            TaskEditDefaults.MAX_DURATION
        );
        input.cooldown = parseIntSafe(
            schedulingViews.cooldownView.getText().toString(),
            TaskEditDefaults.COOLDOWN
        );
        input.adaptive = schedulingViews.adaptiveView.isChecked();

        input.repetitionEnabled = repetitionViews.toggleRepetition.isChecked();
        input.reps = parseIntSafe(
            repetitionViews.repsView.getText().toString(),
            TaskEditDefaults.REPETITION_REPS
        );
        input.perPeriod = parseIntSafe(
            repetitionViews.perPeriodView.getText().toString(),
            TaskEditDefaults.REPETITION_PER_PERIOD
        );
        input.periodUnit = coalesce(
            (Period) repetitionViews.periodUnitView.getSelectedItem(),
            TaskEditDefaults.REPETITION_PERIOD_UNIT
        );
        input.completeFirst = repetitionViews.completeFirstView.isChecked();

        input.progressEnabled = progressViews.toggleProgress.isChecked();
        input.unit = progressViews.unitView.getText().toString();
        input.target = parseIntSafe(
            progressViews.targetView.getText().toString(),
            TaskEditDefaults.TARGET
        );
        input.current = parseIntSafe(
            progressViews.currentView.getText().toString(),
            TaskEditDefaults.CURRENT
        );
        input.resetPerRep = progressViews.resetPerRepView.isChecked();
        input.minPerRep = parseIntSafe(
            progressViews.minPerRepView.getText().toString(),
            TaskEditDefaults.MIN_PER_REP
        );
        input.maxPerRep = parseIntSafe(
            progressViews.maxPerRepView.getText().toString(),
            TaskEditDefaults.MAX_PER_REP
        );

        return input;
    }
}
