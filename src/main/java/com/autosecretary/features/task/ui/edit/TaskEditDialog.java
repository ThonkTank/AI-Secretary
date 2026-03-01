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
import com.autosecretary.features.task.ui.edit.internal.editor.TaskEditSectionBinder;
import com.autosecretary.features.task.ui.edit.internal.TaskEditStateMapper;
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
    public static final String TAG_EDIT = "edit";
    public static final String TAG_CREATE = "create";

    private TaskEditSessionController editSessionController;
    private TaskEditPresenter presenter;
    private TaskEditFormValidator formValidator;
    private TaskEditFormInputReader formInputReader;
    private PrefSlotSectionController prefSlotSectionController;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        TaskViewModel vm = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        editSessionController = vm.getTaskEditSessionController();
        TaskEditState editState = editSessionController.requireSelectedTask();
        presenter = new TaskEditPresenter(editState, new TaskEditStateMapper());

        View rootView = LayoutInflater.from(getContext()).inflate(R.layout.task_editor_dialog, null);
        BoundSections sections = bindEditorSections(rootView, editState);
        GoalSectionController goalSectionController = new GoalSectionController(this, rootView, editState);

        prefSlotSectionController = new PrefSlotSectionController(
            this,
            rootView,
            presenter,
            sections.repetition
        );
        prefSlotSectionController.rebuildPrefSlotUI();

        formInputReader = new TaskEditFormInputReader(
            sections.basicInfo, sections.scheduling, sections.repetition, sections.progress, goalSectionController
        );
        formValidator = new TaskEditFormValidator(
            requireContext(), sections.basicInfo, sections.scheduling, sections.repetition, sections.progress
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

        // The positive-button listener must be set here in onStart(), not via the AlertDialog.Builder
        // callback. If set in the builder, AlertDialog auto-dismisses after the callback regardless
        // of what it returns — validation failures would still close the dialog. Setting null in
        // the builder and attaching the listener here lets us return early on validation failure.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!formValidator.validate()) {
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

    private BoundSections bindEditorSections(View rootView, TaskEditState editState) {
        TaskEditSectionBinder sectionBinder = new TaskEditSectionBinder(this, rootView, editState, presenter);
        return new BoundSections(
            sectionBinder.bindBasicInfo(),
            sectionBinder.bindScheduling(),
            sectionBinder.bindRepetition(() -> prefSlotSectionController.onRepetitionChanged()),
            sectionBinder.bindProgress()
        );
    }

    private static class BoundSections {
        final TaskEditSectionBinder.BasicInfoViews basicInfo;
        final TaskEditSectionBinder.SchedulingViews scheduling;
        final TaskEditSectionBinder.RepetitionViews repetition;
        final TaskEditSectionBinder.ProgressViews progress;

        BoundSections(TaskEditSectionBinder.BasicInfoViews basicInfo,
                      TaskEditSectionBinder.SchedulingViews scheduling,
                      TaskEditSectionBinder.RepetitionViews repetition,
                      TaskEditSectionBinder.ProgressViews progress) {
            this.basicInfo = basicInfo;
            this.scheduling = scheduling;
            this.repetition = repetition;
            this.progress = progress;
        }
    }
}
