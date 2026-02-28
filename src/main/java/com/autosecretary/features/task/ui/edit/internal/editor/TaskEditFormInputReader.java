package com.autosecretary.features.task.ui.edit.internal.editor;

import com.autosecretary.shared.Period;
import com.autosecretary.features.task.data.TaskCore;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.function.Function;
import com.autosecretary.shared.Priority;
import com.autosecretary.features.task.ui.edit.FormInput;
import com.autosecretary.features.task.ui.edit.TaskEditPresenter;
import com.autosecretary.features.task.ui.edit.state.TaskEditDefaults;

/**
 * Reads the current state of all task-edit form views and assembles it into a
 * {@link FormInput} POJO for the presenter to persist.
 *
 * <p>This reader is intentionally lenient: it returns {@code null} for any field whose
 * text cannot be parsed (e.g. an invalid integer or date), rather than throwing. The
 * {@link TaskEditFormValidator} — run before the presenter is called — is responsible
 * for catching and surfacing those problems to the user.
 */
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

    /**
     * Applies {@code parser} to the trimmed input; returns {@code null} if the value
     * is empty or unparseable. Exceptions are intentionally swallowed here — the
     * validator already ensures fields are valid before this reader is called.
     */
    private <T> T parseSafe(String value, Function<String, T> parser) {
        if (value == null) return null;
        String trimmed = value.trim();
        try {
            return trimmed.isEmpty() ? null : parser.apply(trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseDateSafe(String value) {
        return parseSafe(value, LocalDate::parse);
    }

    private LocalTime parseTimeSafe(String value) {
        return parseSafe(value, LocalTime::parse);
    }

    private String normalizeNullableString(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public FormInput read() {
        FormInput input = new FormInput();
        readBasicInfo(input);
        readGoalSection(input);
        readSchedulingSection(input);
        readRepetitionSection(input);
        readProgressSection(input);
        return input;
    }

    private void readBasicInfo(FormInput input) {
        input.title = basicInfoViews.titleView.getText().toString();
        input.description = basicInfoViews.descriptionView.getText().toString();
        input.priority = TaskEditPresenter.coalesce(
            (Priority) basicInfoViews.priorityView.getSelectedItem(),
            TaskEditDefaults.PRIORITY
        );
    }

    private void readGoalSection(FormInput input) {
        String goalIconText = goalSectionController.getGoalIconView().getText().toString().trim();
        input.goalIcon = goalIconText.isEmpty() ? TaskEditDefaults.GOAL_ICON : goalIconText;
        input.goalColorHex = TaskEditPresenter.coalesce(
            goalSectionController.getSelectedGoalColorHex(),
            TaskEditDefaults.GOAL_COLOR_HEX
        );
    }

    private void readSchedulingSection(FormInput input) {
        input.schedulingType = TaskEditPresenter.coalesce(
            (TaskCore.SchedulingType) schedulingViews.schedulingTypeView.getSelectedItem(),
            TaskEditDefaults.SCHEDULING_TYPE
        );
        input.fixedDate = parseDateSafe(schedulingViews.fixedDateView.getText().toString());
        input.fixedStart = parseTimeSafe(schedulingViews.fixedStartView.getText().toString());
        input.fixedEnd = parseTimeSafe(schedulingViews.fixedEndView.getText().toString());
        input.fixedDuration = parseSafe(schedulingViews.fixedDurationView.getText().toString(), Integer::parseInt);
        Integer parsedBudgetRequiredCents = parseSafe(schedulingViews.budgetRequiredCentsView.getText().toString(), Integer::parseInt);
        // 0 is treated as unset — only positive values are meaningful
        input.budgetRequiredCents = parsedBudgetRequiredCents != null && parsedBudgetRequiredCents > 0
            ? parsedBudgetRequiredCents
            : null;
        input.budgetAccountId = normalizeNullableString(schedulingViews.budgetAccountIdView.getText().toString());
        input.budgetCategoryId = normalizeNullableString(schedulingViews.budgetCategoryIdView.getText().toString());
        input.closeOnMiss = schedulingViews.closeOnMissView.isChecked();
        input.minDuration = TaskEditPresenter.parseIntSafe(
            schedulingViews.minDurationView.getText().toString(), TaskEditDefaults.MIN_DURATION);
        input.maxDuration = TaskEditPresenter.parseIntSafe(
            schedulingViews.maxDurationView.getText().toString(), TaskEditDefaults.MAX_DURATION);
        input.cooldown = TaskEditPresenter.parseIntSafe(
            schedulingViews.cooldownView.getText().toString(), TaskEditDefaults.COOLDOWN);
        input.adaptive = schedulingViews.adaptiveView.isChecked();
    }

    private void readRepetitionSection(FormInput input) {
        input.repetitionEnabled = repetitionViews.toggleRepetition.isChecked();
        input.reps = TaskEditPresenter.parseIntSafe(
            repetitionViews.repsView.getText().toString(), TaskEditDefaults.REPETITION_REPS);
        input.perPeriod = TaskEditPresenter.parseIntSafe(
            repetitionViews.perPeriodView.getText().toString(), TaskEditDefaults.REPETITION_PER_PERIOD);
        input.periodUnit = TaskEditPresenter.coalesce(
            (Period) repetitionViews.periodUnitView.getSelectedItem(),
            TaskEditDefaults.REPETITION_PERIOD_UNIT
        );
        input.completeFirst = repetitionViews.completeFirstView.isChecked();
    }

    private void readProgressSection(FormInput input) {
        input.progressEnabled = progressViews.toggleProgress.isChecked();
        input.unit = progressViews.unitView.getText().toString();
        input.target = TaskEditPresenter.parseIntSafe(
            progressViews.targetView.getText().toString(), TaskEditDefaults.TARGET);
        input.current = TaskEditPresenter.parseIntSafe(
            progressViews.currentView.getText().toString(), TaskEditDefaults.CURRENT);
        input.resetPerRep = progressViews.resetPerRepView.isChecked();
        input.minPerRep = TaskEditPresenter.parseIntSafe(
            progressViews.minPerRepView.getText().toString(), TaskEditDefaults.MIN_PER_REP);
        input.maxPerRep = TaskEditPresenter.parseIntSafe(
            progressViews.maxPerRepView.getText().toString(), TaskEditDefaults.MAX_PER_REP);
    }
}
