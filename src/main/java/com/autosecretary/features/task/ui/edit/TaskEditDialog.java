package com.autosecretary.features.task.ui.edit;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.R;
import com.autosecretary.shared.ui.DialogHelper;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.app.AppCompositionRoot;
import com.autosecretary.features.budget.data.entity.BudgetAccountEntity;
import com.autosecretary.features.budget.data.entity.BudgetCategoryEntity;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.task.ui.list.TaskViewModel;
import com.autosecretary.features.task.ui.edit.internal.editor.GoalSectionController;
import com.autosecretary.features.task.ui.edit.internal.editor.PrefSlotSectionController;
import com.autosecretary.features.task.ui.edit.internal.editor.TaskEditFormInputReader;
import com.autosecretary.features.task.ui.edit.internal.editor.TaskEditFormValidator;
import com.autosecretary.features.task.ui.edit.internal.editor.TaskEditSectionBinder;
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
        editState = editSessionController.requireSelectedTask();
        presenter = new TaskEditPresenter(editState);

        View rootView = LayoutInflater.from(getContext()).inflate(R.layout.task_editor_dialog, null);

        TaskEditSectionBinder sectionBinder = new TaskEditSectionBinder(this, rootView, editState, presenter);
        TaskEditSectionBinder.BasicInfoViews basicInfo = sectionBinder.bindBasicInfo();
        TaskEditSectionBinder.SchedulingViews scheduling = sectionBinder.bindScheduling(
                Collections.emptyList(), Collections.emptyList());
        TaskEditSectionBinder.RepetitionViews repetition = sectionBinder.bindRepetition(
            () -> prefSlotSectionController.onRepetitionChanged());
        TaskEditSectionBinder.ProgressViews progress = sectionBinder.bindProgress();

        GoalSectionController goalSectionController = new GoalSectionController(this, rootView, editState);

        prefSlotSectionController = new PrefSlotSectionController(this, rootView, presenter, repetition);
        prefSlotSectionController.rebuildPrefSlotUI();

        formInputReader = new TaskEditFormInputReader(
            basicInfo, scheduling, repetition, progress, goalSectionController
        );
        formValidator = new TaskEditFormValidator(
            requireContext(), basicInfo, scheduling, repetition, progress
        );

        // Load budget accounts and categories asynchronously so the dialog opens instantly.
        // The spinners start empty and populate once the background load completes.
        AppCompositionRoot root = AutoSecretaryApplication.from(requireContext()).getAppCompositionRoot();
        BudgetRepository budgetRepo = root.getBudgetRoomRepository();
        ExecutorService executor = root.getSharedExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                List<BudgetAccountEntity> accounts = budgetRepo.findActiveAccounts();
                List<BudgetCategoryEntity> categories = budgetRepo.findActiveCategories();
                mainHandler.post(() -> {
                    if (isAdded()) {
                        sectionBinder.rebindBudgetSpinners(scheduling, accounts, categories);
                    }
                });
            } catch (Exception e) {
                Log.w("TaskEditDialog", "Failed to load budget data", e);
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

    private void showDeleteConfirmDialog() {
        DialogHelper.showDeleteConfirmation(requireContext(),
                R.string.task_edit_delete_title, R.string.task_edit_delete_message,
                () -> editSessionController.deleteSelectedTask(this::dismiss));
    }

}
