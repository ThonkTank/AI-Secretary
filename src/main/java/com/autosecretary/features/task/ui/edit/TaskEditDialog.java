package com.autosecretary.features.task.ui.edit;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

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

    private EditText titleView;
    private EditText descriptionView;
    private Spinner priorityView;

    private CheckBox closeOnMissView;
    private CheckBox adaptiveView;
    private EditText minDurationView;
    private EditText maxDurationView;
    private EditText cooldownView;
    private EditText budgetRequirementCentsView;

    private CheckBox toggleRepetition;
    private EditText repsView;
    private EditText perPeriodView;
    private Spinner periodUnitView;

    private CheckBox toggleProgress;
    private EditText unitView;
    private EditText targetView;
    private EditText currentView;
    private EditText minPerRepView;
    private EditText maxPerRepView;
    private CheckBox resetPerRepView;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        TaskViewModel vm = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        editSessionController = vm.getTaskEditSessionController();
        TaskEditState editState = editSessionController.requireSelectedTask();
        presenter = new TaskEditPresenter(editState, new TaskEditStateMapper());
        formValidator = new TaskEditFormValidator();

        View rootView = LayoutInflater.from(getContext()).inflate(R.layout.task_editor_fragment, null);
        EditorSectionBindings sectionBindings = bindEditorSections(rootView, editState);
        GoalSectionController goalSectionController = new GoalSectionController(this, rootView, editState);

        titleView = sectionBindings.titleView;
        descriptionView = sectionBindings.descriptionView;
        priorityView = sectionBindings.priorityView;
        closeOnMissView = sectionBindings.closeOnMissView;
        adaptiveView = sectionBindings.adaptiveView;
        minDurationView = sectionBindings.minDurationView;
        maxDurationView = sectionBindings.maxDurationView;
        cooldownView = sectionBindings.cooldownView;
        budgetRequirementCentsView = sectionBindings.budgetRequirementCentsView;
        toggleRepetition = sectionBindings.toggleRepetition;
        repsView = sectionBindings.repsView;
        perPeriodView = sectionBindings.perPeriodView;
        periodUnitView = sectionBindings.periodUnitView;
        toggleProgress = sectionBindings.toggleProgress;
        unitView = sectionBindings.unitView;
        targetView = sectionBindings.targetView;
        currentView = sectionBindings.currentView;
        minPerRepView = sectionBindings.minPerRepView;
        maxPerRepView = sectionBindings.maxPerRepView;
        resetPerRepView = sectionBindings.resetPerRepView;

        prefSlotSectionController = new PrefSlotSectionController(
            this,
            rootView,
            presenter,
            toggleRepetition,
            repsView,
            perPeriodView,
            periodUnitView
        );
        prefSlotSectionController.rebuildPrefSlotUI();

        formInputReader = createFormInputReader(goalSectionController);
        formViews = createFormViews();

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

    private EditorSectionBindings bindEditorSections(View rootView, TaskEditState editState) {
        TaskEditSectionBinder sectionBinder = new TaskEditSectionBinder(this, rootView, editState, presenter);
        TaskEditSectionBinder.BasicInfoViews basicInfoViews = sectionBinder.bindBasicInfo();
        TaskEditSectionBinder.SchedulingViews schedulingViews = sectionBinder.bindScheduling();
        TaskEditSectionBinder.RepetitionViews repetitionViews = sectionBinder.bindRepetition(this::onRepetitionChanged);
        TaskEditSectionBinder.ProgressViews progressViews = sectionBinder.bindProgress();
        return new EditorSectionBindings(basicInfoViews, schedulingViews, repetitionViews, progressViews);
    }

    private TaskEditFormInputReader createFormInputReader(GoalSectionController goalSectionController) {
        return new TaskEditFormInputReader(
            titleView,
            descriptionView,
            priorityView,
            goalSectionController,
            closeOnMissView,
            minDurationView,
            maxDurationView,
            cooldownView,
            budgetRequirementCentsView,
            adaptiveView,
            toggleRepetition,
            repsView,
            perPeriodView,
            periodUnitView,
            toggleProgress,
            unitView,
            targetView,
            currentView,
            resetPerRepView,
            minPerRepView,
            maxPerRepView
        );
    }

    private TaskEditFormViews createFormViews() {
        return new TaskEditFormViews(
            titleView,
            minDurationView,
            maxDurationView,
            cooldownView,
            budgetRequirementCentsView,
            toggleRepetition,
            repsView,
            perPeriodView,
            toggleProgress,
            targetView,
            currentView,
            minPerRepView,
            maxPerRepView
        );
    }

    private static final class EditorSectionBindings {
        private final EditText titleView;
        private final EditText descriptionView;
        private final Spinner priorityView;
        private final CheckBox closeOnMissView;
        private final CheckBox adaptiveView;
        private final EditText minDurationView;
        private final EditText maxDurationView;
        private final EditText cooldownView;
        private final EditText budgetRequirementCentsView;
        private final CheckBox toggleRepetition;
        private final EditText repsView;
        private final EditText perPeriodView;
        private final Spinner periodUnitView;
        private final CheckBox toggleProgress;
        private final EditText unitView;
        private final EditText targetView;
        private final EditText currentView;
        private final EditText minPerRepView;
        private final EditText maxPerRepView;
        private final CheckBox resetPerRepView;

        private EditorSectionBindings(
            TaskEditSectionBinder.BasicInfoViews basicInfoViews,
            TaskEditSectionBinder.SchedulingViews schedulingViews,
            TaskEditSectionBinder.RepetitionViews repetitionViews,
            TaskEditSectionBinder.ProgressViews progressViews
        ) {
            titleView = basicInfoViews.titleView;
            descriptionView = basicInfoViews.descriptionView;
            priorityView = basicInfoViews.priorityView;

            closeOnMissView = schedulingViews.closeOnMissView;
            adaptiveView = schedulingViews.adaptiveView;
            minDurationView = schedulingViews.minDurationView;
            maxDurationView = schedulingViews.maxDurationView;
            cooldownView = schedulingViews.cooldownView;
            budgetRequirementCentsView = schedulingViews.budgetRequirementCentsView;

            toggleRepetition = repetitionViews.toggleRepetition;
            repsView = repetitionViews.repsView;
            perPeriodView = repetitionViews.perPeriodView;
            periodUnitView = repetitionViews.periodUnitView;

            toggleProgress = progressViews.toggleProgress;
            unitView = progressViews.unitView;
            targetView = progressViews.targetView;
            currentView = progressViews.currentView;
            minPerRepView = progressViews.minPerRepView;
            maxPerRepView = progressViews.maxPerRepView;
            resetPerRepView = progressViews.resetPerRepView;
        }
    }
}
