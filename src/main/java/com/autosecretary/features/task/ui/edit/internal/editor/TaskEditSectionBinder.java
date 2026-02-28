package com.autosecretary.features.task.ui.edit.internal.editor;

import android.app.DatePickerDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.fragment.app.DialogFragment;

import com.autosecretary.R;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.ui.edit.TaskEditPresenter;
import com.autosecretary.features.task.ui.edit.state.TaskEditDefaults;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;
import com.google.android.material.textfield.TextInputLayout;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Wires each section of the task-edit form: inflates view references, populates
 * them from {@link TaskEditState}, and attaches change listeners.
 *
 * <p>Each {@code bind*()} method returns a typed inner-class handle (e.g.
 * {@link BasicInfoViews}, {@link SchedulingViews}) that the caller keeps around to
 * read field values later — rather than calling {@code findViewById} again at save
 * time. These handles are also consumed by {@link TaskEditFormInputReader} and
 * {@link TaskEditFormValidator}.
 */
public class TaskEditSectionBinder {

    // German locale date format (day.month.year) used consistently throughout the app UI.
    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final DialogFragment fragment;
    private final View rootView;
    private final TaskEditState editState;
    private final TaskEditPresenter presenter;

    public TaskEditSectionBinder(
        DialogFragment fragment,
        View rootView,
        TaskEditState editState,
        TaskEditPresenter presenter
    ) {
        this.fragment = fragment;
        this.rootView = rootView;
        this.editState = editState;
        this.presenter = presenter;
    }

    public BasicInfoViews bindBasicInfo() {
        EditText titleView = rootView.findViewById(R.id.EditTitle);
        EditText descriptionView = rootView.findViewById(R.id.EditDescription);
        Spinner priorityView = rootView.findViewById(R.id.EditPriority);

        titleView.setText(editState.title);
        descriptionView.setText(editState.description);

        bindEnumSpinner(priorityView, Priority.values());
        priorityView.setSelection(editState.priority.ordinal());

        return new BasicInfoViews(titleView, descriptionView, priorityView);
    }

    public SchedulingViews bindScheduling() {
        EditText deadlineView = rootView.findViewById(R.id.EditDeadline);
        TextInputLayout deadlineInputLayout = rootView.findViewById(R.id.DeadlineInputLayout);
        ImageButton clearDeadline = rootView.findViewById(R.id.ClearDeadline);
        Spinner schedulingTypeView = rootView.findViewById(R.id.EditSchedulingType);
        LinearLayout fixedSchedulingContainer = rootView.findViewById(R.id.FixedSchedulingContainer);
        EditText fixedDateView = rootView.findViewById(R.id.EditFixedDate);
        EditText fixedStartView = rootView.findViewById(R.id.EditFixedStart);
        EditText fixedEndView = rootView.findViewById(R.id.EditFixedEnd);
        EditText fixedDurationView = rootView.findViewById(R.id.EditFixedDuration);
        EditText budgetRequiredCentsView = rootView.findViewById(R.id.EditBudgetRequiredCents);
        EditText budgetAccountIdView = rootView.findViewById(R.id.EditBudgetAccountId);
        EditText budgetCategoryIdView = rootView.findViewById(R.id.EditBudgetCategoryId);
        CheckBox closeOnMissView = rootView.findViewById(R.id.EditCloseOnMiss);
        EditText minDurationView = rootView.findViewById(R.id.EditMinDuration);
        EditText maxDurationView = rootView.findViewById(R.id.EditMaxDuration);
        EditText cooldownView = rootView.findViewById(R.id.EditCooldown);
        CheckBox adaptiveView = rootView.findViewById(R.id.EditAdaptive);

        SchedulingViews views = new SchedulingViews(
            deadlineView,
            schedulingTypeView,
            fixedSchedulingContainer,
            fixedDateView,
            fixedStartView,
            fixedEndView,
            fixedDurationView,
            budgetRequiredCentsView,
            budgetAccountIdView,
            budgetCategoryIdView,
            closeOnMissView,
            minDurationView,
            maxDurationView,
            cooldownView,
            adaptiveView
        );

        updateDeadlineDisplay(views);
        bindDeadlineListeners(deadlineView, deadlineInputLayout, clearDeadline, views);

        bindEnumSpinner(schedulingTypeView, TaskCore.SchedulingType.values());
        TaskCore.SchedulingType schedulingType = TaskEditPresenter.coalesce(editState.schedulingType, TaskEditDefaults.SCHEDULING_TYPE);
        schedulingTypeView.setSelection(schedulingType.ordinal());
        fixedDateView.setText(editState.fixedDate != null ? editState.fixedDate.toString() : "");
        fixedStartView.setText(editState.fixedStart != null ? editState.fixedStart.toString() : "");
        fixedEndView.setText(editState.fixedEnd != null ? editState.fixedEnd.toString() : "");
        fixedDurationView.setText(editState.fixedDuration != null ? String.valueOf(editState.fixedDuration) : "");
        budgetRequiredCentsView.setText(editState.budgetRequiredCents != null ? String.valueOf(editState.budgetRequiredCents) : "");
        budgetAccountIdView.setText(editState.budgetAccountId != null ? editState.budgetAccountId : "");
        budgetCategoryIdView.setText(editState.budgetCategoryId != null ? editState.budgetCategoryId : "");
        fixedSchedulingContainer.setVisibility((editState.schedulingType == TaskCore.SchedulingType.TERMIN) ? View.VISIBLE : View.GONE);
        bindSchedulingTypeListener(views);

        closeOnMissView.setChecked(editState.closeOnMiss);
        minDurationView.setText(String.valueOf(editState.minDuration));
        maxDurationView.setText(String.valueOf(editState.maxDuration));
        cooldownView.setText(String.valueOf(editState.cooldown));
        adaptiveView.setChecked(editState.adaptive);

        return views;
    }

    private void bindDeadlineListeners(EditText deadlineView, TextInputLayout deadlineInputLayout,
                                       ImageButton clearDeadline, SchedulingViews views) {
        deadlineView.setOnClickListener(v -> showDatePicker(views));
        deadlineInputLayout.setEndIconOnClickListener(v -> showDatePicker(views));
        clearDeadline.setOnClickListener(v -> {
            presenter.setEditableDeadline(null);
            updateDeadlineDisplay(views);
        });
    }

    private void bindSchedulingTypeListener(SchedulingViews views) {
        views.schedulingTypeView.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                TaskCore.SchedulingType selected = (TaskCore.SchedulingType) views.schedulingTypeView.getSelectedItem();
                views.fixedSchedulingContainer.setVisibility(selected == TaskCore.SchedulingType.TERMIN ? View.VISIBLE : View.GONE);
            }
        });
    }

    /**
     * Wires the repetition section and attaches change listeners that fire
     * {@code onRepetitionChanged} whenever any repetition field (toggle, reps, perPeriod,
     * periodUnit) changes. The caller ({@link com.autosecretary.features.task.ui.edit.internal.editor.PrefSlotSectionController})
     * uses this callback to recompute {@code repsPerDay} and rebuild the pref-slot UI.
     */
    public RepetitionViews bindRepetition(Runnable onRepetitionChanged) {
        CheckBox toggleRepetition = rootView.findViewById(R.id.ToggleRepetition);
        LinearLayout repetitionContainer = rootView.findViewById(R.id.RepetitionContainer);
        EditText repsView = rootView.findViewById(R.id.EditReps);
        EditText perPeriodView = rootView.findViewById(R.id.EditPerPeriod);
        Spinner periodUnitView = rootView.findViewById(R.id.EditPeriodUnit);
        CheckBox completeFirstView = rootView.findViewById(R.id.EditCompleteFirst);

        RepetitionViews views = new RepetitionViews(
            toggleRepetition,
            repetitionContainer,
            repsView,
            perPeriodView,
            periodUnitView,
            completeFirstView
        );

        boolean hasRepetition = editState.reps > 0;
        toggleRepetition.setChecked(hasRepetition);
        repetitionContainer.setVisibility(hasRepetition ? View.VISIBLE : View.GONE);

        repsView.setText(String.valueOf(editState.reps > 0 ? editState.reps : 1));
        perPeriodView.setText(String.valueOf(editState.perPeriod > 0 ? editState.perPeriod : 1));

        bindEnumSpinner(periodUnitView, Period.values());
        Period periodUnit = TaskEditPresenter.coalesce(editState.periodUnit, TaskEditDefaults.REPETITION_PERIOD_UNIT);
        periodUnitView.setSelection(periodUnit.ordinal());
        completeFirstView.setChecked(editState.completeFirst);

        presenter.initializeRepetitionState(
            toggleRepetition.isChecked(),
            repsView.getText().toString(),
            perPeriodView.getText().toString(),
            (Period) periodUnitView.getSelectedItem()
        );

        attachRepetitionListeners(views, onRepetitionChanged);

        return views;
    }

    private void attachRepetitionListeners(RepetitionViews views, Runnable onRepetitionChanged) {
        views.toggleRepetition.setOnCheckedChangeListener((btn, checked) -> {
            views.repetitionContainer.setVisibility(checked ? View.VISIBLE : View.GONE);
            onRepetitionChanged.run();
        });

        TextWatcher repWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                onRepetitionChanged.run();
            }
        };
        views.repsView.addTextChangedListener(repWatcher);
        views.perPeriodView.addTextChangedListener(repWatcher);

        views.periodUnitView.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onRepetitionChanged.run();
            }
        });
    }

    public ProgressViews bindProgress() {
        CheckBox toggleProgress = rootView.findViewById(R.id.ToggleProgress);
        LinearLayout progressContainer = rootView.findViewById(R.id.ProgressContainer);
        EditText unitView = rootView.findViewById(R.id.EditUnit);
        EditText targetView = rootView.findViewById(R.id.EditTarget);
        EditText currentView = rootView.findViewById(R.id.EditCurrent);
        CheckBox resetPerRepView = rootView.findViewById(R.id.EditResetPerRep);
        EditText minPerRepView = rootView.findViewById(R.id.EditMinPerRep);
        EditText maxPerRepView = rootView.findViewById(R.id.EditMaxPerRep);

        ProgressViews views = new ProgressViews(
            toggleProgress,
            progressContainer,
            unitView,
            targetView,
            currentView,
            resetPerRepView,
            minPerRepView,
            maxPerRepView
        );

        boolean hasProgress = editState.target > 0;
        toggleProgress.setChecked(hasProgress);
        progressContainer.setVisibility(hasProgress ? View.VISIBLE : View.GONE);

        unitView.setText(editState.unit != null ? editState.unit : "");
        targetView.setText(String.valueOf(editState.target));
        currentView.setText(String.valueOf(editState.current));
        resetPerRepView.setChecked(editState.resetPerRep);
        minPerRepView.setText(String.valueOf(editState.minPerRep));
        maxPerRepView.setText(String.valueOf(editState.maxPerRep));

        toggleProgress.setOnCheckedChangeListener((btn, checked) ->
            progressContainer.setVisibility(checked ? View.VISIBLE : View.GONE));

        return views;
    }

    private <E> void bindEnumSpinner(Spinner spinner, E[] values) {
        ArrayAdapter<E> adapter = new ArrayAdapter<>(
            fragment.requireContext(),
            android.R.layout.simple_spinner_item,
            values
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void updateDeadlineDisplay(SchedulingViews views) {
        LocalDate deadline = presenter.getEditableDeadline();
        String text = deadline != null
            ? deadline.format(DEADLINE_FORMATTER)
            : fragment.getString(R.string.task_editor_deadline_none);
        views.deadlineView.setText(text);
        views.deadlineView.setContentDescription(
            fragment.getString(R.string.task_edit_deadline_content_description, text)
        );
    }

    private void showDatePicker(SchedulingViews views) {
        LocalDate deadline = presenter.getEditableDeadline();
        LocalDate current = deadline != null ? deadline : LocalDate.now();
        // DatePickerDialog uses 0-based months (0 = January); java.time uses 1-based.
        new DatePickerDialog(fragment.requireContext(), (picker, year, month, day) -> {
            presenter.setEditableDeadline(LocalDate.of(year, month + 1, day));
            updateDeadlineDisplay(views);
        }, current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth()).show();
    }

    /** View-handle returned by {@link #bindBasicInfo()}. Holds title, description, and priority. */
    public static final class BasicInfoViews {
        public final EditText titleView;
        public final EditText descriptionView;
        public final Spinner priorityView;

        private BasicInfoViews(EditText titleView, EditText descriptionView, Spinner priorityView) {
            this.titleView = titleView;
            this.descriptionView = descriptionView;
            this.priorityView = priorityView;
        }
    }

    /**
     * View-handle returned by {@link #bindScheduling()}.
     *
     * <p>The three {@code budget*} fields ({@code budgetRequiredCentsView},
     * {@code budgetAccountIdView}, {@code budgetCategoryIdView}) are logically budget
     * fields but live here because they are optional scheduling-time properties of a
     * task. When a task completes and {@code budgetRequiredCents > 0}, an expense is
     * automatically booked against the linked account — see
     * {@code CheckOffTaskUseCase} and {@code CLAUDE.md §Task→Budget integration}.
     */
    public static final class SchedulingViews {
        public final EditText deadlineView;
        public final Spinner schedulingTypeView;
        public final LinearLayout fixedSchedulingContainer;
        public final EditText fixedDateView;
        public final EditText fixedStartView;
        public final EditText fixedEndView;
        public final EditText fixedDurationView;
        public final EditText budgetRequiredCentsView;
        public final EditText budgetAccountIdView;
        public final EditText budgetCategoryIdView;
        public final CheckBox closeOnMissView;
        public final EditText minDurationView;
        public final EditText maxDurationView;
        public final EditText cooldownView;
        public final CheckBox adaptiveView;

        private SchedulingViews(
            EditText deadlineView,
            Spinner schedulingTypeView,
            LinearLayout fixedSchedulingContainer,
            EditText fixedDateView,
            EditText fixedStartView,
            EditText fixedEndView,
            EditText fixedDurationView,
            EditText budgetRequiredCentsView,
            EditText budgetAccountIdView,
            EditText budgetCategoryIdView,
            CheckBox closeOnMissView,
            EditText minDurationView,
            EditText maxDurationView,
            EditText cooldownView,
            CheckBox adaptiveView
        ) {
            this.deadlineView = deadlineView;
            this.schedulingTypeView = schedulingTypeView;
            this.fixedSchedulingContainer = fixedSchedulingContainer;
            this.fixedDateView = fixedDateView;
            this.fixedStartView = fixedStartView;
            this.fixedEndView = fixedEndView;
            this.fixedDurationView = fixedDurationView;
            this.budgetRequiredCentsView = budgetRequiredCentsView;
            this.budgetAccountIdView = budgetAccountIdView;
            this.budgetCategoryIdView = budgetCategoryIdView;
            this.closeOnMissView = closeOnMissView;
            this.minDurationView = minDurationView;
            this.maxDurationView = maxDurationView;
            this.cooldownView = cooldownView;
            this.adaptiveView = adaptiveView;
        }
    }

    /** View-handle returned by {@link #bindRepetition(Runnable)}. */
    public static final class RepetitionViews {
        public final CheckBox toggleRepetition;
        public final LinearLayout repetitionContainer;
        public final EditText repsView;
        public final EditText perPeriodView;
        public final Spinner periodUnitView;
        public final CheckBox completeFirstView;

        private RepetitionViews(
            CheckBox toggleRepetition,
            LinearLayout repetitionContainer,
            EditText repsView,
            EditText perPeriodView,
            Spinner periodUnitView,
            CheckBox completeFirstView
        ) {
            this.toggleRepetition = toggleRepetition;
            this.repetitionContainer = repetitionContainer;
            this.repsView = repsView;
            this.perPeriodView = perPeriodView;
            this.periodUnitView = periodUnitView;
            this.completeFirstView = completeFirstView;
        }
    }

    /** View-handle returned by {@link #bindProgress()}. */
    public static final class ProgressViews {
        public final CheckBox toggleProgress;
        public final LinearLayout progressContainer;
        public final EditText unitView;
        public final EditText targetView;
        public final EditText currentView;
        public final CheckBox resetPerRepView;
        public final EditText minPerRepView;
        public final EditText maxPerRepView;

        private ProgressViews(
            CheckBox toggleProgress,
            LinearLayout progressContainer,
            EditText unitView,
            EditText targetView,
            EditText currentView,
            CheckBox resetPerRepView,
            EditText minPerRepView,
            EditText maxPerRepView
        ) {
            this.toggleProgress = toggleProgress;
            this.progressContainer = progressContainer;
            this.unitView = unitView;
            this.targetView = targetView;
            this.currentView = currentView;
            this.resetPerRepView = resetPerRepView;
            this.minPerRepView = minPerRepView;
            this.maxPerRepView = maxPerRepView;
        }
    }

    /** No-op adapter so subclasses only need to override {@code afterTextChanged}. */
    private static abstract class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    /** No-op adapter so subclasses only need to override {@code onItemSelected}. */
    private static abstract class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
        @Override public void onNothingSelected(AdapterView<?> parent) {}
    }
}
