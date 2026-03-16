package com.autosecretary.features.task.ui.edit;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.R;
import com.autosecretary.shared.ui.DialogHelper;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.app.AppCompositionRoot;
import com.autosecretary.features.budget.domain.BudgetAccount;
import com.autosecretary.features.budget.domain.BudgetCategory;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.ui.list.TaskViewModel;
import com.autosecretary.features.task.ui.edit.internal.editor.PrefSlotSectionController;
import com.autosecretary.features.task.ui.edit.internal.editor.TaskEditFormInputReader;
import com.autosecretary.features.task.ui.edit.internal.editor.TaskEditFormValidator;
import com.autosecretary.features.task.ui.edit.internal.editor.TaskEditSectionBinder;
import com.autosecretary.features.task.ui.edit.state.TaskEditDefaults;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * DialogFragment for creating and editing tasks. Manages five form sections:
 * basic info, scheduling, repetition, preferred slots, and progress.
 * Delegates form logic to {@link TaskEditPresenter}. Works with
 * {@link TaskEditState} (mutable UI model), not {@link com.autosecretary.features.task.data.Task}
 * directly. On save, reads form fields directly into the state and converts
 * back to Task for persistence.
 */
public class TaskEditDialog extends DialogFragment {
    public static final String TAG_EDIT = "edit";
    public static final String TAG_CREATE = "create";

    private TaskEditSessionController editSessionController;
    private TaskEditPresenter presenter;
    private TaskEditState editState;
    private TaskEditFormValidator formValidator;
    private TaskEditFormInputReader formInputReader;
    private PrefSlotSectionController prefSlotSectionController;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        TaskViewModel vm = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        editSessionController = vm.getTaskEditSessionController();
        TaskEditState selectedTask = editSessionController.getSelectedTask();
        if (selectedTask == null) {
            // ViewModel was reset (e.g. after process death + fragment restore). Nothing to edit.
            dismissAllowingStateLoss();
            return new AlertDialog.Builder(requireContext()).create();
        }
        editState = selectedTask;
        presenter = new TaskEditPresenter(editState);

        View rootView = LayoutInflater.from(getContext()).inflate(R.layout.task_editor_dialog, null);

        TaskEditSectionBinder sectionBinder = new TaskEditSectionBinder(this, rootView, editState, presenter);
        TaskEditSectionBinder.BasicInfoViews basicInfo = sectionBinder.bindBasicInfo();
        TaskEditSectionBinder.SchedulingViews scheduling = sectionBinder.bindScheduling(
                Collections.emptyList(), Collections.emptyList());
        TaskEditSectionBinder.RepetitionViews repetition = sectionBinder.bindRepetition(
            () -> prefSlotSectionController.onRepetitionChanged());
        TaskEditSectionBinder.ProgressViews progress = sectionBinder.bindProgress();

        prefSlotSectionController = new PrefSlotSectionController(this, rootView, presenter, repetition);
        prefSlotSectionController.rebuildPrefSlotUI();

        setupAccordion(rootView, editState, presenter,
                repetition.toggleRepetition, progress.toggleProgress, scheduling.toggleBudget);

        formInputReader = new TaskEditFormInputReader(
            basicInfo, scheduling, repetition, progress
        );
        formValidator = new TaskEditFormValidator(
            requireContext(), basicInfo, scheduling, repetition, progress, editState
        );

        // Load data asynchronously so the dialog opens instantly.
        // Spinners start empty and populate once the background loads complete.
        AppCompositionRoot root = AutoSecretaryApplication.from(requireContext()).getAppCompositionRoot();
        BudgetRepository budgetRepo = root.getBudgetRoomRepository();
        TaskDao taskDao = root.getTaskDao();
        ExecutorService executor = root.getSharedExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                List<BudgetAccount> accounts = budgetRepo.findActiveAccounts();
                List<BudgetCategory> categories = budgetRepo.findActiveCategories();
                mainHandler.post(() -> {
                    if (isAdded()) {
                        sectionBinder.rebindBudgetSpinners(scheduling, accounts, categories);
                    }
                });
            } catch (Exception e) {
                Log.w("TaskEditDialog", "Failed to load budget data", e);
            }
        });

        executor.execute(() -> {
            try {
                List<Task> allTasks = taskDao.readAll();
                mainHandler.post(() -> {
                    if (isAdded()) {
                        sectionBinder.rebindParentSpinner(basicInfo, allTasks);
                    }
                });
            } catch (Exception e) {
                Log.w("TaskEditDialog", "Failed to load tasks for parent spinner", e);
            }
        });

        return new AlertDialog.Builder(requireContext())
            .setTitle(editSessionController.isNewTask()
                ? R.string.task_edit_dialog_title_create
                : R.string.task_edit_dialog_title_edit)
            .setView(rootView)
            .setPositiveButton(R.string.action_save, null)
            .setNeutralButton(R.string.action_delete, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) {
            return;
        }

        // The positive-button listener must be set here in onStart(), not via the AlertDialog.Builder
        // callback. If set in the builder, AlertDialog auto-dismisses after the callback regardless
        // of what it returns — validation failures would still close the dialog. Setting null in
        // the builder and attaching the listener here lets us return early on validation failure.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!formValidator.validate()) {
                return;
            }
            formInputReader.read(editState);
            editSessionController.saveEditedTask(presenter.toTaskForSave(editSessionController.requireSelectedBaseTask()));
            dismiss();
        });

        View deleteButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (deleteButton == null) {
            return;
        }

        if (editSessionController.isNewTask()) {
            deleteButton.setVisibility(View.GONE);
            return;
        }

        deleteButton.setOnClickListener(v -> showDeleteConfirmDialog());
    }

    /**
     * Wires four collapsible accordion sections (Weitere Details, Wiederholung, Fortschritt,
     * Budget). Only one section is open at a time. Opening a section with a feature switch
     * enables that feature; explicitly closing it (tapping its own header) disables it.
     * Collapsing a section because another opened does NOT change its switch state.
     */
    private void setupAccordion(View rootView, TaskEditState editState, TaskEditPresenter presenter,
                                CompoundButton toggleRepetition, CompoundButton toggleProgress,
                                CompoundButton toggleBudget) {
        View[] containers = {
            rootView.findViewById(R.id.WeitereDetailsContainer),
            rootView.findViewById(R.id.RepetitionContainer),
            rootView.findViewById(R.id.ProgressContainer),
            rootView.findViewById(R.id.BudgetContainer)
        };
        TextView[] labels = {
            rootView.findViewById(R.id.WeitereDetailsToggle),
            rootView.findViewById(R.id.WiederholungToggle),
            rootView.findViewById(R.id.FortschrittToggle),
            rootView.findViewById(R.id.BudgetToggle)
        };
        // null for sections without a feature switch (Weitere Details)
        CompoundButton[] switches = { null, toggleRepetition, toggleProgress, toggleBudget };
        int[] collapsedRes = {
            R.string.task_editor_weitere_details_collapsed,
            R.string.task_editor_section_wiederholung_collapsed,
            R.string.task_editor_section_fortschritt_collapsed,
            R.string.task_editor_section_budget_collapsed
        };
        int[] expandedRes = {
            R.string.task_editor_weitere_details_expanded,
            R.string.task_editor_section_wiederholung_expanded,
            R.string.task_editor_section_fortschritt_expanded,
            R.string.task_editor_section_budget_expanded
        };
        int[] headerIds = {
            R.id.WeitereDetailsHeader,
            R.id.WiederholungHeader,
            R.id.FortschrittHeader,
            R.id.BudgetHeader
        };

        for (int i = 0; i < containers.length; i++) {
            final int idx = i;
            rootView.findViewById(headerIds[i]).setOnClickListener(v -> {
                boolean isOpen = containers[idx].getVisibility() == View.VISIBLE;
                if (isOpen) {
                    // Explicit close: collapse + disable feature
                    containers[idx].setVisibility(View.GONE);
                    labels[idx].setText(collapsedRes[idx]);
                    if (switches[idx] != null) switches[idx].setChecked(false);
                } else {
                    // Collapse all other open sections without touching their switch state
                    for (int j = 0; j < containers.length; j++) {
                        if (j != idx && containers[j].getVisibility() == View.VISIBLE) {
                            containers[j].setVisibility(View.GONE);
                            labels[j].setText(collapsedRes[j]);
                        }
                    }
                    // Expand this section and enable its feature
                    containers[idx].setVisibility(View.VISIBLE);
                    labels[idx].setText(expandedRes[idx]);
                    if (switches[idx] != null) switches[idx].setChecked(true);
                }
            });
        }

        // Auto-expand the first section that has non-default data
        boolean weitereDetailsHasData = (editState.description != null && !editState.description.isEmpty())
                || editState.parentTaskId != null
                || editState.startDate != null
                || presenter.getEditableDeadline() != null
                || editState.fixedDate != null
                || editState.fixedStart != null
                || (editState.fixedDuration != null && editState.fixedDuration > 0)
                || editState.minDuration != TaskEditDefaults.MIN_DURATION
                || editState.maxDuration != TaskEditDefaults.MAX_DURATION
                || editState.cooldown != TaskEditDefaults.COOLDOWN
                || !editState.closeOnMiss;
        boolean wiederholungHasData = editState.reps > 0;
        boolean fortschrittHasData = editState.unit != null && !editState.unit.isEmpty();
        boolean budgetHasData = editState.budgetRequiredCents != null && editState.budgetRequiredCents > 0;

        int autoExpand = weitereDetailsHasData ? 0
                : wiederholungHasData ? 1
                : fortschrittHasData ? 2
                : budgetHasData ? 3
                : -1;
        if (autoExpand >= 0) {
            containers[autoExpand].setVisibility(View.VISIBLE);
            labels[autoExpand].setText(expandedRes[autoExpand]);
        }
    }

    private void showDeleteConfirmDialog() {
        DialogHelper.showDeleteConfirmation(requireContext(),
                R.string.task_edit_delete_title, R.string.task_edit_delete_message,
                () -> editSessionController.deleteSelectedTask(this::dismiss));
    }

}
