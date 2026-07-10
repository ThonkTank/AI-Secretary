package com.autosecretary.features.task.ui.edit.internal.editor;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.R;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;
import com.autosecretary.shared.ui.SimpleItemSelectedListener;
import com.autosecretary.shared.ui.SimpleTextWatcher;
import com.autosecretary.shared.ui.SpinnerHelper;
import com.autosecretary.features.task.domain.model.TaskCore;
import com.autosecretary.features.task.application.edit.TaskEditOption;
import com.autosecretary.features.task.ui.edit.TaskEditFormController;
import com.autosecretary.features.task.ui.edit.state.TaskEditDefaults;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;
import com.google.android.material.textfield.TextInputLayout;

import com.autosecretary.shared.DateFormatters;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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

    private final DialogFragment fragment;
    private final View rootView;
    private final TaskEditState editState;
    private final TaskEditFormController formController;

    public TaskEditSectionBinder(
        DialogFragment fragment,
        View rootView,
        TaskEditState editState,
        TaskEditFormController formController
    ) {
        this.fragment = fragment;
        this.rootView = rootView;
        this.editState = editState;
        this.formController = formController;
    }

    public BasicInfoViews bindBasicInfo() {
        EditText titleView = rootView.findViewById(R.id.EditTitle);
        EditText descriptionView = rootView.findViewById(R.id.EditDescription);
        Spinner priorityView = rootView.findViewById(R.id.EditPriority);
        Spinner parentTaskView = rootView.findViewById(R.id.EditParentTask);

        titleView.setText(editState.title);
        descriptionView.setText(editState.description);

        SpinnerHelper.bindList(priorityView, Arrays.asList(Priority.values()), Object::toString, fragment.requireContext());
        priorityView.setSelection(editState.priority.ordinal());

        // Initially bind with just the "none" entry; populated asynchronously after tasks load.
        SpinnerHelper.bindListWithNone(parentTaskView, new ArrayList<TaskEditOption>(),
                TaskEditOption::label, fragment.getString(R.string.task_editor_parent_none),
                editState.parentTaskId, TaskEditOption::id, fragment.requireContext());

        return new BasicInfoViews(titleView, descriptionView, priorityView, parentTaskView);
    }

    /**
     * Re-populates the parent task spinner after an asynchronous load.
     * Filters out the current task (a task cannot be its own parent).
     */
    public void rebindParentSpinner(BasicInfoViews views, List<TaskEditOption> parentTasks) {
        views.parentTaskItems = parentTasks;
        SpinnerHelper.bindListWithNone(views.parentTaskView, views.parentTaskItems,
                TaskEditOption::label, fragment.getString(R.string.task_editor_parent_none),
                editState.parentTaskId, TaskEditOption::id, fragment.requireContext());
        views.parentTaskItemsBound = true;
    }

    public SchedulingViews bindScheduling(
            List<TaskEditOption> accounts,
            List<TaskEditOption> categories) {
        TextInputLayout startDateInputLayout = rootView.findViewById(R.id.StartDateInputLayout);
        ImageButton clearStartDate = rootView.findViewById(R.id.ClearStartDate);
        TextInputLayout fixedDateInputLayout = rootView.findViewById(R.id.FixedDateInputLayout);
        ImageButton clearFixedDate = rootView.findViewById(R.id.ClearFixedDate);
        TextInputLayout fixedStartInputLayout = rootView.findViewById(R.id.FixedStartInputLayout);
        ImageButton clearFixedStart = rootView.findViewById(R.id.ClearFixedStart);
        TextInputLayout deadlineInputLayout = rootView.findViewById(R.id.DeadlineInputLayout);
        ImageButton clearDeadline = rootView.findViewById(R.id.ClearDeadline);

        SchedulingViews views = SchedulingViews.from(rootView, accounts, categories);

        updateStartDateDisplay(views);
        updateFixedDateDisplay(views);
        updateFixedStartDisplay(views);
        updateDeadlineDisplay(views);
        bindSchedulingDateListeners(startDateInputLayout, clearStartDate,
                fixedDateInputLayout, clearFixedDate,
                fixedStartInputLayout, clearFixedStart,
                deadlineInputLayout, clearDeadline, views);
        initializeSchedulingFields(views);

        return views;
    }

    private void initializeSchedulingFields(SchedulingViews views) {
        List<TaskCore.SchedulingType> selectableTypes = Arrays.asList(TaskCore.SchedulingType.values());
        SpinnerHelper.bindList(views.schedulingTypeView, selectableTypes, this::schedulingTypeLabel, fragment.requireContext());
        TaskCore.SchedulingType schedulingType = Objects.requireNonNullElse(formController.getEditableSchedulingType(), TaskEditDefaults.SCHEDULING_TYPE);
        int typePosition = selectableTypes.indexOf(schedulingType);
        views.schedulingTypeView.setSelection(typePosition >= 0 ? typePosition : 0);
        views.schedulingTypeView.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                TaskCore.SchedulingType selected = SpinnerHelper.enumAtPosition(views.schedulingTypeView, TaskCore.SchedulingType.values());
                formController.setEditableSchedulingType(selected != null ? selected : TaskEditDefaults.SCHEDULING_TYPE);
                applySchedulingTypeVisibility(views);
            }
        });
        applySchedulingTypeVisibility(views);

        views.budgetRequiredCentsView.setText(toStringOrEmpty(editState.budgetRequiredCents));
        views.fixedDurationView.setText(toStringOrEmpty(editState.fixedDuration));

        SpinnerHelper.bindListWithNone(views.budgetAccountView, views.budgetAccounts,
                TaskEditOption::label, fragment.getString(R.string.task_editor_budget_no_account),
                editState.budgetAccountId, TaskEditOption::id, fragment.requireContext());

        SpinnerHelper.bindListWithNone(views.budgetCategoryView, views.budgetCategories,
                TaskEditOption::label,
                fragment.getString(R.string.task_editor_budget_no_category),
                editState.budgetCategoryId, TaskEditOption::id, fragment.requireContext());

        boolean hasBudget = editState.budgetRequiredCents != null && editState.budgetRequiredCents > 0;
        views.toggleBudget.setChecked(hasBudget);

        views.closeOnMissView.setChecked(editState.closeOnMiss);
        views.minDurationView.setText(String.valueOf(editState.minDuration));
        views.maxDurationView.setText(String.valueOf(editState.maxDuration));
        views.cooldownView.setText(String.valueOf(editState.cooldown));
        views.adaptiveView.setChecked(editState.adaptive);
    }

    private void bindSchedulingDateListeners(TextInputLayout startDateInputLayout,
                                             ImageButton clearStartDate,
                                             TextInputLayout fixedDateInputLayout,
                                             ImageButton clearFixedDate,
                                             TextInputLayout fixedStartInputLayout,
                                             ImageButton clearFixedStart,
                                             TextInputLayout deadlineInputLayout,
                                             ImageButton clearDeadline,
                                             SchedulingViews views) {
        views.startDateView.setOnClickListener(v -> showDatePicker(
                formController.getEditableStartDate(),
                picked -> {
                    formController.setEditableStartDate(picked);
                    updateStartDateDisplay(views);
                }));
        startDateInputLayout.setEndIconOnClickListener(v -> showDatePicker(
                formController.getEditableStartDate(),
                picked -> {
                    formController.setEditableStartDate(picked);
                    updateStartDateDisplay(views);
                }));
        clearStartDate.setOnClickListener(v -> {
            formController.setEditableStartDate(null);
            updateStartDateDisplay(views);
        });

        views.fixedDateView.setOnClickListener(v -> showDatePicker(
                formController.getEditableFixedDate(),
                picked -> {
                    formController.setEditableFixedDate(picked);
                    updateFixedDateDisplay(views);
                }));
        fixedDateInputLayout.setEndIconOnClickListener(v -> showDatePicker(
                formController.getEditableFixedDate(),
                picked -> {
                    formController.setEditableFixedDate(picked);
                    updateFixedDateDisplay(views);
                }));
        clearFixedDate.setOnClickListener(v -> {
            formController.setEditableFixedDate(null);
            updateFixedDateDisplay(views);
        });

        views.fixedStartView.setOnClickListener(v -> showTimePicker(
                formController.getEditableFixedStart(),
                picked -> {
                    formController.setEditableFixedStart(picked);
                    updateFixedStartDisplay(views);
                }));
        fixedStartInputLayout.setEndIconOnClickListener(v -> showTimePicker(
                formController.getEditableFixedStart(),
                picked -> {
                    formController.setEditableFixedStart(picked);
                    updateFixedStartDisplay(views);
                }));
        clearFixedStart.setOnClickListener(v -> {
            formController.setEditableFixedStart(null);
            updateFixedStartDisplay(views);
        });

        views.deadlineView.setOnClickListener(v -> showDatePicker(views));
        deadlineInputLayout.setEndIconOnClickListener(v -> showDatePicker(views));
        clearDeadline.setOnClickListener(v -> {
            formController.setEditableDeadline(null);
            updateDeadlineDisplay(views);
        });
    }

    /**
     * Wires the repetition section and attaches change listeners that fire
     * {@code onRepetitionChanged} whenever any repetition field (toggle, reps, perPeriod,
     * periodUnit) changes. The caller ({@link com.autosecretary.features.task.ui.edit.internal.editor.PrefSlotSectionController})
     * uses this callback to recompute {@code repsPerDay} and rebuild the pref-slot UI.
     */
    public RepetitionViews bindRepetition(Runnable onRepetitionChanged) {
        RepetitionViews views = RepetitionViews.from(rootView);

        boolean hasRepetition = editState.reps > 0;
        views.toggleRepetition.setChecked(hasRepetition);

        // Show a compact summary next to the toggle when repetition is set, e.g. "3× pro Woche".
        if (hasRepetition && editState.periodUnit != null) {
            views.repetitionSummary.setText(formatRepetitionSummary(editState.reps, editState.perPeriod, editState.periodUnit));
            views.repetitionSummary.setVisibility(View.VISIBLE);
        } else {
            views.repetitionSummary.setVisibility(View.GONE);
        }

        views.repsView.setText(String.valueOf(editState.reps > 0 ? editState.reps : TaskEditDefaults.REPETITION_REPS));
        views.perPeriodView.setText(String.valueOf(editState.perPeriod > 0 ? editState.perPeriod : TaskEditDefaults.REPETITION_PER_PERIOD));

        SpinnerHelper.bindList(views.periodUnitView, Arrays.asList(Period.values()), Object::toString, fragment.requireContext());
        Period periodUnit = Objects.requireNonNullElse(editState.periodUnit, TaskEditDefaults.REPETITION_PERIOD_UNIT);
        views.periodUnitView.setSelection(periodUnit.ordinal());
        views.completeFirstView.setChecked(editState.completeFirst);

        formController.initializeRepetitionState(
            views.toggleRepetition.isChecked(),
            views.repsView.getText().toString(),
            views.perPeriodView.getText().toString(),
            SpinnerHelper.enumAtPosition(views.periodUnitView, Period.values())
        );

        attachRepetitionListeners(views, onRepetitionChanged);

        return views;
    }

    private void attachRepetitionListeners(RepetitionViews views, Runnable onRepetitionChanged) {
        views.toggleRepetition.setOnCheckedChangeListener((btn, checked) -> onRepetitionChanged.run());

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
        ProgressViews views = ProgressViews.from(rootView);

        boolean hasProgress = !editState.unit.isEmpty();
        views.toggleProgress.setChecked(hasProgress);

        views.unitView.setText(editState.unit);
        views.targetView.setText(String.valueOf(editState.target));
        views.currentView.setText(String.valueOf(editState.current));
        views.resetPerRepView.setChecked(editState.resetPerRep);
        views.minPerRepView.setText(String.valueOf(editState.minPerRep));
        views.maxPerRepView.setText(String.valueOf(editState.maxPerRep));

        return views;
    }

    /**
     * Re-populates the budget account and category spinners after an asynchronous load.
     * Updates the backing lists on {@code views} so that {@link TaskEditFormInputReader}
     * resolves the correct IDs when the user saves the form.
     */
    public void rebindBudgetSpinners(SchedulingViews views,
                                     List<TaskEditOption> accounts,
                                     List<TaskEditOption> categories) {
        views.budgetAccounts = accounts;
        views.budgetCategories = categories;

        SpinnerHelper.bindListWithNone(views.budgetAccountView, accounts,
                TaskEditOption::label, fragment.getString(R.string.task_editor_budget_no_account),
                editState.budgetAccountId, TaskEditOption::id, fragment.requireContext());

        SpinnerHelper.bindListWithNone(views.budgetCategoryView, categories,
                TaskEditOption::label,
                fragment.getString(R.string.task_editor_budget_no_category),
                editState.budgetCategoryId, TaskEditOption::id, fragment.requireContext());
    }

    /** Formats a compact repetition summary string, e.g. "3× pro Woche" or "2× pro 2 Wochen". */
    private static String formatRepetitionSummary(int reps, int perPeriod, Period periodUnit) {
        String periodLabel;
        switch (periodUnit) {
            case DAY:   periodLabel = perPeriod == 1 ? "Tag"   : perPeriod + " Tage";    break;
            case WEEK:  periodLabel = perPeriod == 1 ? "Woche" : perPeriod + " Wochen";  break;
            case MONTH: periodLabel = perPeriod == 1 ? "Monat" : perPeriod + " Monate";  break;
            default:    periodLabel = periodUnit.toString(); break;
        }
        return reps + "\u00d7 pro " + periodLabel;
    }

    private void updateDeadlineDisplay(SchedulingViews views) {
        LocalDate deadline = formController.getEditableDeadline();
        String text = deadline != null
            ? deadline.format(DateFormatters.DATE_SHORT)
            : fragment.getString(R.string.task_editor_deadline_none);
        views.deadlineView.setText(text);
        views.deadlineView.setContentDescription(
            fragment.getString(R.string.task_edit_deadline_content_description, text)
        );
    }

    private void updateStartDateDisplay(SchedulingViews views) {
        LocalDate startDate = formController.getEditableStartDate();
        String text = startDate != null
                ? startDate.format(DateFormatters.DATE_SHORT)
                : fragment.getString(R.string.task_editor_start_date_none);
        views.startDateView.setText(text);
        views.startDateView.setContentDescription(
                fragment.getString(R.string.task_edit_start_date_content_description, text)
        );
    }

    private void updateFixedDateDisplay(SchedulingViews views) {
        LocalDate fixedDate = formController.getEditableFixedDate();
        String text = fixedDate != null
                ? fixedDate.format(DateFormatters.DATE_SHORT)
                : fragment.getString(R.string.task_editor_fixed_date_none);
        views.fixedDateView.setText(text);
        views.fixedDateView.setContentDescription(
                fragment.getString(R.string.task_edit_fixed_date_content_description, text)
        );
    }

    private void updateFixedStartDisplay(SchedulingViews views) {
        LocalTime fixedStart = formController.getEditableFixedStart();
        String text = fixedStart != null
                ? fixedStart.format(DateFormatters.TIME_HH_MM)
                : fragment.getString(R.string.task_editor_fixed_start_none);
        views.fixedStartView.setText(text);
        views.fixedStartView.setContentDescription(
                fragment.getString(R.string.task_edit_fixed_start_content_description, text)
        );
    }

    private void applySchedulingTypeVisibility(SchedulingViews views) {
        boolean isTermin = formController.getEditableSchedulingType() == TaskCore.SchedulingType.TERMIN;
        views.taskStartDateRow.setVisibility(isTermin ? View.GONE : View.VISIBLE);
        views.terminFieldsContainer.setVisibility(isTermin ? View.VISIBLE : View.GONE);
    }

    private String schedulingTypeLabel(TaskCore.SchedulingType type) {
        if (type == TaskCore.SchedulingType.TERMIN) {
            return fragment.getString(R.string.task_editor_scheduling_type_label_termin);
        }
        return fragment.getString(R.string.task_editor_scheduling_type_label_task);
    }

    private void showDatePicker(@Nullable LocalDate currentDate,
                                java.util.function.Consumer<LocalDate> onPicked) {
        LocalDate current = currentDate != null ? currentDate : LocalDate.now();
        // DatePickerDialog uses 0-based months (0 = January); java.time uses 1-based.
        new DatePickerDialog(fragment.requireContext(), (picker, year, month, day) ->
                onPicked.accept(LocalDate.of(year, month + 1, day)),
                current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth()).show();
    }

    private void showTimePicker(@Nullable LocalTime currentTime,
                                java.util.function.Consumer<LocalTime> onPicked) {
        LocalTime current = currentTime != null ? currentTime : LocalTime.of(9, 0);
        new TimePickerDialog(fragment.requireContext(), (picker, hour, minute) ->
                onPicked.accept(LocalTime.of(hour, minute)),
                current.getHour(), current.getMinute(), true).show();
    }

    private void showDatePicker(SchedulingViews views) {
        showDatePicker(formController.getEditableDeadline(), picked -> {
            // Presenter owns deadline state to preserve existing behavior.
            formController.setEditableDeadline(picked);
            updateDeadlineDisplay(views);
        });
    }

    /** View-handle returned by {@link #bindBasicInfo()}. Holds title, description, priority, and parent task. */
    public static final class BasicInfoViews {
        public final EditText titleView;
        public final EditText descriptionView;
        public final Spinner priorityView;
        public final Spinner parentTaskView;
        /** Backing list for {@link #parentTaskView}. Position 0 = "none" sentinel. Updated by {@link TaskEditSectionBinder#rebindParentSpinner}. */
        public List<TaskEditOption> parentTaskItems;
        /**
         * True once {@link TaskEditSectionBinder#rebindParentSpinner} has loaded real task data.
         * Guards against overwriting an existing parent with "none" when the dialog is saved
         * before async spinner data has arrived.
         */
        public boolean parentTaskItemsBound;

        private BasicInfoViews(EditText titleView, EditText descriptionView,
                               Spinner priorityView, Spinner parentTaskView) {
            this.titleView = titleView;
            this.descriptionView = descriptionView;
            this.priorityView = priorityView;
            this.parentTaskView = parentTaskView;
            this.parentTaskItems = new ArrayList<>();
            this.parentTaskItemsBound = false;
        }
    }

    /**
     * View-handle returned by {@link #bindScheduling(List, List)}.
     *
     * <p>The {@code budget*} fields ({@code budgetRequiredCentsView},
     * {@code budgetAccountView}, {@code budgetCategoryView}, {@code toggleBudget},
     * {@code budgetContainer}) are logically budget fields but live here because they
     * are optional scheduling-time properties of a task. When a task completes and
     * {@code budgetRequiredCents > 0}, an expense is automatically booked against the
     * linked account — see {@code CheckOffTaskUseCase} and
     * {@code CLAUDE.md §Task→Budget integration}.
     *
     * <p>Note: The budget fields appear visually at the bottom of the form (after the
     * Progress section) but remain in this handle for binder cohesion, because the
     * async {@link TaskEditSectionBinder#rebindBudgetSpinners} callback updates them
     * regardless of their visual position.
     *
     * <p>{@code budgetAccountView} and {@code budgetCategoryView} are {@link Spinner}
     * dropdowns. Position 0 in each is the "none" sentinel (maps to {@code null} ID).
     * The backing lists ({@code budgetAccounts}, {@code budgetCategories}) are kept here
     * so {@link TaskEditFormInputReader} can resolve the selected position back to a UUID.
     */
    public static final class SchedulingViews {
        public final EditText deadlineView;
        public final EditText startDateView;
        public final EditText fixedDateView;
        public final EditText fixedStartView;
        public final EditText fixedDurationView;
        public final Spinner schedulingTypeView;
        public final LinearLayout taskStartDateRow;
        public final LinearLayout terminFieldsContainer;
        public final EditText budgetRequiredCentsView;
        public final Spinner budgetAccountView;
        public final Spinner budgetCategoryView;
        /** Backing list for {@link #budgetAccountView}. Position 0 in the spinner = "none" sentinel. Updated by {@link TaskEditSectionBinder#rebindBudgetSpinners}. */
        public List<TaskEditOption> budgetAccounts;
        /** Backing list for {@link #budgetCategoryView}. Position 0 in the spinner = "none" sentinel. Updated by {@link TaskEditSectionBinder#rebindBudgetSpinners}. */
        public List<TaskEditOption> budgetCategories;
        /** Budget section toggle; auto-expanded when editing a task with budgetRequiredCents > 0. */
        public final CompoundButton toggleBudget;
        /** Budget section container; visibility controlled by {@link #toggleBudget}. */
        public final LinearLayout budgetContainer;
        public final CheckBox closeOnMissView;
        public final EditText minDurationView;
        public final EditText maxDurationView;
        public final EditText cooldownView;
        public final CheckBox adaptiveView;

        private SchedulingViews(View root,
                                List<TaskEditOption> accounts,
                                List<TaskEditOption> categories) {
            deadlineView = root.findViewById(R.id.EditDeadline);
            startDateView = root.findViewById(R.id.EditStartDate);
            fixedDateView = root.findViewById(R.id.EditFixedDate);
            fixedStartView = root.findViewById(R.id.EditFixedStart);
            fixedDurationView = root.findViewById(R.id.EditFixedDuration);
            schedulingTypeView = root.findViewById(R.id.EditSchedulingType);
            taskStartDateRow = root.findViewById(R.id.TaskStartDateRow);
            terminFieldsContainer = root.findViewById(R.id.TerminFieldsContainer);
            budgetRequiredCentsView = root.findViewById(R.id.EditBudgetRequiredCents);
            budgetAccountView = root.findViewById(R.id.EditBudgetAccountId);
            budgetCategoryView = root.findViewById(R.id.EditBudgetCategoryId);
            budgetAccounts = accounts;
            budgetCategories = categories;
            toggleBudget = root.findViewById(R.id.ToggleBudget);
            budgetContainer = root.findViewById(R.id.BudgetContainer);
            closeOnMissView = root.findViewById(R.id.EditCloseOnMiss);
            minDurationView = root.findViewById(R.id.EditMinDuration);
            maxDurationView = root.findViewById(R.id.EditMaxDuration);
            cooldownView = root.findViewById(R.id.EditCooldown);
            adaptiveView = root.findViewById(R.id.EditAdaptive);
        }

        static SchedulingViews from(View root,
                                    List<TaskEditOption> accounts,
                                    List<TaskEditOption> categories) {
            return new SchedulingViews(root, accounts, categories);
        }
    }

    /** View-handle returned by {@link #bindRepetition(Runnable)}. */
    public static final class RepetitionViews {
        /** SwitchMaterial toggle; typed as CompoundButton (common parent) to avoid a hard dependency on the widget class. */
        public final CompoundButton toggleRepetition;
        /** Shows a compact summary of the current repetition pattern, e.g. "3× pro Woche". Hidden when repetition is off. */
        public final TextView repetitionSummary;
        public final LinearLayout repetitionContainer;
        public final EditText repsView;
        public final EditText perPeriodView;
        public final Spinner periodUnitView;
        public final CheckBox completeFirstView;

        private RepetitionViews(View root) {
            toggleRepetition = root.findViewById(R.id.ToggleRepetition);
            repetitionSummary = root.findViewById(R.id.RepetitionSummary);
            repetitionContainer = root.findViewById(R.id.RepetitionContainer);
            repsView = root.findViewById(R.id.EditReps);
            perPeriodView = root.findViewById(R.id.EditPerPeriod);
            periodUnitView = root.findViewById(R.id.EditPeriodUnit);
            completeFirstView = root.findViewById(R.id.EditCompleteFirst);
        }

        static RepetitionViews from(View root) { return new RepetitionViews(root); }
    }

    /** View-handle returned by {@link #bindProgress()}. */
    public static final class ProgressViews {
        /** SwitchMaterial toggle; typed as CompoundButton (common parent) to avoid a hard dependency on the widget class. */
        public final CompoundButton toggleProgress;
        public final LinearLayout progressContainer;
        public final EditText unitView;
        public final EditText targetView;
        public final EditText currentView;
        public final CheckBox resetPerRepView;
        public final EditText minPerRepView;
        public final EditText maxPerRepView;

        private ProgressViews(View root) {
            toggleProgress = root.findViewById(R.id.ToggleProgress);
            progressContainer = root.findViewById(R.id.ProgressContainer);
            unitView = root.findViewById(R.id.EditUnit);
            targetView = root.findViewById(R.id.EditTarget);
            currentView = root.findViewById(R.id.EditCurrent);
            resetPerRepView = root.findViewById(R.id.EditResetPerRep);
            minPerRepView = root.findViewById(R.id.EditMinPerRep);
            maxPerRepView = root.findViewById(R.id.EditMaxPerRep);
        }

        static ProgressViews from(View root) { return new ProgressViews(root); }
    }

    /** Returns {@code value.toString()} if non-null, or {@code ""} — for populating nullable fields into EditText. */
    private static String toStringOrEmpty(Object value) {
        return value != null ? value.toString() : "";
    }

}
