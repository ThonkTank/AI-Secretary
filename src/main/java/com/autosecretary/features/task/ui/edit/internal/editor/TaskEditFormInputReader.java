package com.autosecretary.features.task.ui.edit.internal.editor;

import com.autosecretary.shared.Period;

import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Function;
import com.autosecretary.shared.Priority;
import com.autosecretary.features.task.domain.model.TaskCore;
import com.autosecretary.features.task.ui.edit.state.TaskEditDefaults;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;
import com.autosecretary.shared.ui.DialogValidation;
import com.autosecretary.shared.ui.SpinnerHelper;

/**
 * Reads the current state of all task-edit form views and writes it directly into a
 * {@link TaskEditState} for the formController to persist.
 *
 * <p>This reader is intentionally lenient: it writes {@code null} for any field whose
 * text cannot be parsed (e.g. an invalid integer or date), rather than throwing. The
 * {@link TaskEditFormValidator} — run before this reader is called — is responsible
 * for catching and surfacing those problems to the user.
 */
public class TaskEditFormInputReader {

    private final TaskEditSectionBinder.BasicInfoViews basicInfoViews;
    private final TaskEditSectionBinder.SchedulingViews schedulingViews;
    private final TaskEditSectionBinder.RepetitionViews repetitionViews;
    private final TaskEditSectionBinder.ProgressViews progressViews;

    public TaskEditFormInputReader(
        TaskEditSectionBinder.BasicInfoViews basicInfoViews,
        TaskEditSectionBinder.SchedulingViews schedulingViews,
        TaskEditSectionBinder.RepetitionViews repetitionViews,
        TaskEditSectionBinder.ProgressViews progressViews
    ) {
        this.basicInfoViews = basicInfoViews;
        this.schedulingViews = schedulingViews;
        this.repetitionViews = repetitionViews;
        this.progressViews = progressViews;
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

    private String normalizeNullableString(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Reads all form views and writes the values directly into {@code state}. */
    public void read(TaskEditState state) {
        readBasicInfo(state);
        readSchedulingSection(state);
        readRepetitionSection(state);
        readProgressSection(state);
    }

    private void readBasicInfo(TaskEditState state) {
        state.title = basicInfoViews.titleView.getText().toString();
        state.description = basicInfoViews.descriptionView.getText().toString();
        state.priority = Objects.requireNonNullElse(
            SpinnerHelper.enumAtPosition(basicInfoViews.priorityView, Priority.values()),
            TaskEditDefaults.PRIORITY
        );
        // Parent spinner options are loaded asynchronously. If the list is not bound yet,
        // keep the existing parentTaskId instead of silently resetting to top-level.
        if (!basicInfoViews.parentTaskItemsBound) {
            return;
        }
        int parentPos = basicInfoViews.parentTaskView.getSelectedItemPosition();
        state.parentTaskId = (parentPos > 0 && parentPos <= basicInfoViews.parentTaskItems.size())
                ? basicInfoViews.parentTaskItems.get(parentPos - 1).id() : null;
    }

    private void readSchedulingSection(TaskEditState state) {
        state.schedulingType = Objects.requireNonNullElse(
            SpinnerHelper.enumAtPosition(schedulingViews.schedulingTypeView, TaskCore.SchedulingType.values()),
            TaskEditDefaults.SCHEDULING_TYPE
        );
        state.fixedDuration = parseSafe(schedulingViews.fixedDurationView.getText().toString(), Integer::parseInt);
        if (state.schedulingType == TaskCore.SchedulingType.TERMIN) {
            // "Ab Datum" is only used for flexible TASK scheduling.
            state.startDate = null;
            state.fixedEnd = null;
        } else {
            // Fixed appointment fields are only used for TERMIN scheduling.
            state.fixedDate = null;
            state.fixedStart = null;
            state.fixedEnd = null;
            state.fixedDuration = null;
        }
        Integer budgetCents = parseSafe(schedulingViews.budgetRequiredCentsView.getText().toString(), Integer::parseInt);
        // 0 is treated as unset - only positive values are meaningful
        state.budgetRequiredCents = (budgetCents != null && budgetCents > 0) ? budgetCents : null;
        // Position 0 is the "none" sentinel; positions 1..n map to budgetAccounts/budgetCategories index n-1
        int accountPos = schedulingViews.budgetAccountView.getSelectedItemPosition();
        state.budgetAccountId = (accountPos > 0 && accountPos <= schedulingViews.budgetAccounts.size())
                ? schedulingViews.budgetAccounts.get(accountPos - 1).id() : null;
        int categoryPos = schedulingViews.budgetCategoryView.getSelectedItemPosition();
        state.budgetCategoryId = (categoryPos > 0 && categoryPos <= schedulingViews.budgetCategories.size())
                ? schedulingViews.budgetCategories.get(categoryPos - 1).id() : null;
        state.closeOnMiss = schedulingViews.closeOnMissView.isChecked();
        state.minDuration = DialogValidation.parseIntOrDefault(
            schedulingViews.minDurationView.getText().toString(), TaskEditDefaults.MIN_DURATION);
        state.maxDuration = DialogValidation.parseIntOrDefault(
            schedulingViews.maxDurationView.getText().toString(), TaskEditDefaults.MAX_DURATION);
        state.cooldown = DialogValidation.parseIntOrDefault(
            schedulingViews.cooldownView.getText().toString(), TaskEditDefaults.COOLDOWN);
        state.adaptive = schedulingViews.adaptiveView.isChecked();
    }

    private void readRepetitionSection(TaskEditState state) {
        boolean repetitionEnabled = repetitionViews.toggleRepetition.isChecked();
        if (repetitionEnabled) {
            int newReps = DialogValidation.parseIntOrDefault(
                repetitionViews.repsView.getText().toString(), TaskEditDefaults.REPETITION_REPS);
            int newPerPeriod = DialogValidation.parseIntOrDefault(
                repetitionViews.perPeriodView.getText().toString(), TaskEditDefaults.REPETITION_PER_PERIOD);
            Period newPeriodUnit = Objects.requireNonNullElse(
                SpinnerHelper.enumAtPosition(repetitionViews.periodUnitView, Period.values()),
                TaskEditDefaults.REPETITION_PERIOD_UNIT
            );
            boolean periodChanged =
                newReps != state.reps ||
                newPerPeriod != state.perPeriod ||
                newPeriodUnit != state.periodUnit;

            state.reps = newReps;
            state.perPeriod = newPerPeriod;
            state.periodUnit = newPeriodUnit;
            state.completeFirst = repetitionViews.completeFirstView.isChecked();

            if (periodChanged || state.periodStart == null) {
                // Repetition schedule changed — old period counters are meaningless under the
                // new schedule, so reset them to start a fresh period from today.
                state.periodStart = LocalDate.now();
                state.periodCompletions = 0;
                state.carryoverDebt = 0;
            }
        } else {
            state.reps = 0;
            state.perPeriod = 1;
            state.periodUnit = Period.DAY;
            state.periodCompletions = 0;
            state.periodStart = null;
            state.completeFirst = false;
            state.carryoverDebt = 0;
        }
    }

    private void readProgressSection(TaskEditState state) {
        boolean progressEnabled = progressViews.toggleProgress.isChecked();
        if (progressEnabled) {
            state.unit = progressViews.unitView.getText().toString();
            state.target = DialogValidation.parseIntOrDefault(
                progressViews.targetView.getText().toString(), TaskEditDefaults.TARGET);
            state.current = DialogValidation.parseIntOrDefault(
                progressViews.currentView.getText().toString(), TaskEditDefaults.CURRENT);
            state.resetPerRep = progressViews.resetPerRepView.isChecked();
            state.minPerRep = DialogValidation.parseIntOrDefault(
                progressViews.minPerRepView.getText().toString(), TaskEditDefaults.MIN_PER_REP);
            state.maxPerRep = DialogValidation.parseIntOrDefault(
                progressViews.maxPerRepView.getText().toString(), TaskEditDefaults.MAX_PER_REP);
        } else {
            state.unit = "";  // Empty string means no progress tracking (matches TaskEditDefaults.UNIT)
            state.target = 0;
            state.current = 0;
            state.resetPerRep = false;
            state.minPerRep = 0;
            state.maxPerRep = 0;
        }
    }
}
