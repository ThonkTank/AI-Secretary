package com.autosecretary.features.task.ui.edit.internal.editor;

import android.app.DatePickerDialog;
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
import com.autosecretary.features.budget.data.entity.BudgetAccountEntity;
import com.autosecretary.features.budget.data.entity.BudgetCategoryEntity;
import com.autosecretary.features.budget.ui.internal.BudgetSummaryPresentationMapper;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;
import com.autosecretary.shared.ui.SimpleItemSelectedListener;
import com.autosecretary.shared.ui.SimpleTextWatcher;
import com.autosecretary.shared.ui.SpinnerHelper;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.ui.edit.TaskEditPresenter;
import com.autosecretary.features.task.ui.edit.state.TaskEditDefaults;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;
import com.google.android.material.textfield.TextInputLayout;

import com.autosecretary.shared.DateFormatters;

import java.time.LocalDate;
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
        Spinner parentTaskView = rootView.findViewById(R.id.EditParentTask);

        titleView.setText(editState.title);
        descriptionView.setText(editState.description);

        SpinnerHelper.bindList(priorityView, Arrays.asList(Priority.values()), Object::toString, fragment.requireContext());
        priorityView.setSelection(editState.priority.ordinal());

        // Initially bind with just the "none" entry; populated asynchronously after tasks load.
        SpinnerHelper.bindListWithNone(parentTaskView, new ArrayList<Task>(),
                t -> t.core.title, fragment.getString(R.string.task_editor_parent_none),
                editState.parentTaskId, t -> t.core.id, fragment.requireContext());

        return new BasicInfoViews(titleView, descriptionView, priorityView, parentTaskView);
    }

    /**
     * Re-populates the parent task spinner after an asynchronous load.
     * Filters out the current task (a task cannot be its own parent).
     */
    public void rebindParentSpinner(BasicInfoViews views, List<Task> allTasks) {
        views.parentTaskItems = allTasks.stream()
                .filter(t -> !t.core.id.equals(editState.id))
                .collect(java.util.stream.Collectors.toList());

        SpinnerHelper.bindListWithNone(views.parentTaskView, views.parentTaskItems,
                t -> t.core.title, fragment.getString(R.string.task_editor_parent_none),
                editState.parentTaskId, t -> t.core.id, fragment.requireContext());
    }

    public SchedulingViews bindScheduling(
            List<BudgetAccountEntity> accounts,
            List<BudgetCategoryEntity> categories) {
        TextInputLayout deadlineInputLayout = rootView.findViewById(R.id.DeadlineInputLayout);
        ImageButton clearDeadline = rootView.findViewById(R.id.ClearDeadline);

        SchedulingViews views = SchedulingViews.from(rootView, accounts, categories);

        updateDeadlineDisplay(views);
        bindDeadlineListeners(deadlineInputLayout, clearDeadline, views);
        initializeSchedulingFields(views);

        return views;
    }

    private void initializeSchedulingFields(SchedulingViews views) {
        // TERMIN is excluded from the spinner: the UI scaffolding for fixed scheduling
        // was removed. SchedulingType.TERMIN still exists in the domain and DB but is
        // not surfaced until the feature is complete.
        List<TaskCore.SchedulingType> selectableTypes = Arrays.stream(TaskCore.SchedulingType.values())
                .filter(t -> t != TaskCore.SchedulingType.TERMIN)
                .collect(java.util.stream.Collectors.toList());
        SpinnerHelper.bindList(views.schedulingTypeView, selectableTypes, Object::toString, fragment.requireContext());
        TaskCore.SchedulingType schedulingType = Objects.requireNonNullElse(editState.schedulingType, TaskEditDefaults.SCHEDULING_TYPE);
        // Find position in the filtered list (TERMIN maps to default if somehow stored)
        int typePosition = selectableTypes.indexOf(schedulingType);
        views.schedulingTypeView.setSelection(typePosition >= 0 ? typePosition : 0);

        views.budgetRequiredCentsView.setText(toStringOrEmpty(editState.budgetRequiredCents));

        SpinnerHelper.bindListWithNone(views.budgetAccountView, views.budgetAccounts,
                a -> a.name, fragment.getString(R.string.task_editor_budget_no_account),
                editState.budgetAccountId, a -> a.id, fragment.requireContext());

        SpinnerHelper.bindListWithNone(views.budgetCategoryView, views.budgetCategories,
                c -> BudgetSummaryPresentationMapper.categoryLabel(c.icon, c.name),
                fragment.getString(R.string.task_editor_budget_no_category),
                editState.budgetCategoryId, c -> c.id, fragment.requireContext());

        // Budget section: auto-expand when editing a task that already has a budget link.
        boolean hasBudget = editState.budgetRequiredCents != null && editState.budgetRequiredCents > 0;
        views.budgetContainer.setVisibility(hasBudget ? View.VISIBLE : View.GONE);
        views.toggleBudget.setChecked(hasBudget);
        views.toggleBudget.setOnCheckedChangeListener((btn, checked) ->
                views.budgetContainer.setVisibility(checked ? View.VISIBLE : View.GONE));

        views.closeOnMissView.setChecked(editState.closeOnMiss);
        views.minDurationView.setText(String.valueOf(editState.minDuration));
        views.maxDurationView.setText(String.valueOf(editState.maxDuration));
        views.cooldownView.setText(String.valueOf(editState.cooldown));
        views.adaptiveView.setChecked(editState.adaptive);
    }

    private void bindDeadlineListeners(TextInputLayout deadlineInputLayout,
                                       ImageButton clearDeadline, SchedulingViews views) {
        views.deadlineView.setOnClickListener(v -> showDatePicker(views));
        deadlineInputLayout.setEndIconOnClickListener(v -> showDatePicker(views));
        clearDeadline.setOnClickListener(v -> {
            presenter.setEditableDeadline(null);
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
        views.repetitionContainer.setVisibility(hasRepetition ? View.VISIBLE : View.GONE);

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

        presenter.initializeRepetitionState(
            views.toggleRepetition.isChecked(),
            views.repsView.getText().toString(),
            views.perPeriodView.getText().toString(),
            SpinnerHelper.enumAtPosition(views.periodUnitView, Period.values())
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
        ProgressViews views = ProgressViews.from(rootView);

        boolean hasProgress = !editState.unit.isEmpty();
        views.toggleProgress.setChecked(hasProgress);
        views.progressContainer.setVisibility(hasProgress ? View.VISIBLE : View.GONE);

        views.unitView.setText(editState.unit);
        views.targetView.setText(String.valueOf(editState.target));
        views.currentView.setText(String.valueOf(editState.current));
        views.resetPerRepView.setChecked(editState.resetPerRep);
        views.minPerRepView.setText(String.valueOf(editState.minPerRep));
        views.maxPerRepView.setText(String.valueOf(editState.maxPerRep));

        views.toggleProgress.setOnCheckedChangeListener((btn, checked) ->
            views.progressContainer.setVisibility(checked ? View.VISIBLE : View.GONE));

        return views;
    }

    /**
     * Re-populates the budget account and category spinners after an asynchronous load.
     * Updates the backing lists on {@code views} so that {@link TaskEditFormInputReader}
     * resolves the correct IDs when the user saves the form.
     */
    public void rebindBudgetSpinners(SchedulingViews views,
                                     List<BudgetAccountEntity> accounts,
                                     List<BudgetCategoryEntity> categories) {
        views.budgetAccounts = accounts;
        views.budgetCategories = categories;

        SpinnerHelper.bindListWithNone(views.budgetAccountView, accounts,
                a -> a.name, fragment.getString(R.string.task_editor_budget_no_account),
                editState.budgetAccountId, a -> a.id, fragment.requireContext());

        SpinnerHelper.bindListWithNone(views.budgetCategoryView, categories,
                c -> BudgetSummaryPresentationMapper.categoryLabel(c.icon, c.name),
                fragment.getString(R.string.task_editor_budget_no_category),
                editState.budgetCategoryId, c -> c.id, fragment.requireContext());
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
        LocalDate deadline = presenter.getEditableDeadline();
        String text = deadline != null
            ? deadline.format(DateFormatters.DATE_SHORT)
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

    /** View-handle returned by {@link #bindBasicInfo()}. Holds title, description, priority, and parent task. */
    public static final class BasicInfoViews {
        public final EditText titleView;
        public final EditText descriptionView;
        public final Spinner priorityView;
        public final Spinner parentTaskView;
        /** Backing list for {@link #parentTaskView}. Position 0 = "none" sentinel. Updated by {@link TaskEditSectionBinder#rebindParentSpinner}. */
        public List<Task> parentTaskItems;

        private BasicInfoViews(EditText titleView, EditText descriptionView,
                               Spinner priorityView, Spinner parentTaskView) {
            this.titleView = titleView;
            this.descriptionView = descriptionView;
            this.priorityView = priorityView;
            this.parentTaskView = parentTaskView;
            this.parentTaskItems = new ArrayList<>();
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
        public final Spinner schedulingTypeView;
        public final EditText budgetRequiredCentsView;
        public final Spinner budgetAccountView;
        public final Spinner budgetCategoryView;
        /** Backing list for {@link #budgetAccountView}. Position 0 in the spinner = "none" sentinel. Updated by {@link TaskEditSectionBinder#rebindBudgetSpinners}. */
        public List<BudgetAccountEntity> budgetAccounts;
        /** Backing list for {@link #budgetCategoryView}. Position 0 in the spinner = "none" sentinel. Updated by {@link TaskEditSectionBinder#rebindBudgetSpinners}. */
        public List<BudgetCategoryEntity> budgetCategories;
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
                                List<BudgetAccountEntity> accounts,
                                List<BudgetCategoryEntity> categories) {
            deadlineView = root.findViewById(R.id.EditDeadline);
            schedulingTypeView = root.findViewById(R.id.EditSchedulingType);
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
                                    List<BudgetAccountEntity> accounts,
                                    List<BudgetCategoryEntity> categories) {
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
