package com.autosecretary.features.task.ui.edit;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.R;
import com.autosecretary.features.task.ui.list.TaskViewModel;
import com.autosecretary.features.task.ui.edit.internal.editor.GoalSectionController;
import com.autosecretary.features.task.ui.edit.internal.editor.PrefSlotSectionController;
import com.autosecretary.features.task.ui.edit.internal.editor.TaskEditFormInputReader;
import com.autosecretary.features.task.ui.edit.internal.editor.TaskEditFormValidator;
import com.autosecretary.features.task.ui.edit.internal.editor.TaskEditFormViews;
import com.autosecretary.features.task.ui.edit.internal.editor.TaskEditSectionBinder;
import com.autosecretary.features.task.ui.edit.internal.mapper.TaskEditStateMapper;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;

/**
 * DialogFragment for creating and editing tasks. Manages five form sections:
 * basic info, scheduling, repetition, preferred slots, and progress.
 * Delegates form logic to {@link TaskEditPresenter}. Works with
 * {@link TaskEditState} (mutable UI model), not {@link com.autosecretary.features.task.data.Task}
 * directly. On save, collects fields, applies via presenter, and converts
 * back to Task for persistence.
 */
public class TaskEditDialog extends DialogFragment {

    private TaskEditSessionController editSessionController;
    private TaskEditPresenter presenter;
    private TaskEditFormValidator formValidator;
    private TaskEditFormInputReader formInputReader;
    private TaskEditFormViews formViews;
    private PrefSlotSectionController prefSlotSectionController;

    private TaskEditSectionBinder.BasicInfoViews basicInfoViews;
    private TaskEditSectionBinder.SchedulingViews schedulingViews;
    private TaskEditSectionBinder.RepetitionViews repetitionViews;
    private TaskEditSectionBinder.ProgressViews progressViews;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        TaskViewModel vm = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        editSessionController = vm.getTaskEditSessionController();
        TaskEditState editState = editSessionController.requireSelectedTask();
        presenter = new TaskEditPresenter(editState, new TaskEditStateMapper());
        formValidator = new TaskEditFormValidator(requireContext());

        View rootView = LayoutInflater.from(getContext()).inflate(R.layout.task_editor_fragment, null);
        bindEditorSections(rootView, editState);
        GoalSectionController goalSectionController = new GoalSectionController(this, rootView, editState);

        prefSlotSectionController = new PrefSlotSectionController(
            this,
            rootView,
            presenter,
            repetitionViews
        );
        prefSlotSectionController.rebuildPrefSlotUI();

        formInputReader = new TaskEditFormInputReader(
            basicInfoViews, schedulingViews, repetitionViews, progressViews, goalSectionController
        );
        formViews = new TaskEditFormViews(
            basicInfoViews, schedulingViews, repetitionViews, progressViews
        );

        return new AlertDialog.Builder(requireContext())
            .setTitle(editSessionController.isNewTask()
                ? R.string.task_edit_dialog_title_create
                : R.string.task_edit_dialog_title_edit)
            .setView(rootView)
            .setPositiveButton(R.string.task_edit_dialog_positive, null)
            .setNeutralButton(R.string.task_edit_dialog_delete, null)
            .setNegativeButton(R.string.task_edit_dialog_negative, null)
            .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) {
            return;
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!formValidator.validate(formViews)) {
                return;
            }
            presenter.applyForm(formInputReader.read());
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
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.task_edit_delete_title)
            .setMessage(R.string.task_edit_delete_message)
            .setPositiveButton(R.string.task_edit_delete_confirm, (dialog, which) ->
                editSessionController.deleteSelectedTask(this::dismiss)
            )
            .setNegativeButton(R.string.task_edit_delete_cancel, null)
            .show();
    }

    private void onRepetitionChanged() {
        prefSlotSectionController.onRepetitionChanged();
    }

    private void bindEditorSections(View rootView, TaskEditState editState) {
        TaskEditSectionBinder sectionBinder = new TaskEditSectionBinder(this, rootView, editState, presenter);
        basicInfoViews = sectionBinder.bindBasicInfo();
        schedulingViews = sectionBinder.bindScheduling();
        repetitionViews = sectionBinder.bindRepetition(this::onRepetitionChanged);
        progressViews = sectionBinder.bindProgress();
    }
}
