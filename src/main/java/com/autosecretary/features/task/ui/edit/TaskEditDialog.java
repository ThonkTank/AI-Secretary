package com.autosecretary.features.task.ui.edit;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.R;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.shared.ui.DialogHelper;
import com.autosecretary.features.task.ui.edit.internal.editor.PrefSlotSectionController;
import com.autosecretary.features.task.ui.edit.internal.editor.TaskEditFormInputReader;
import com.autosecretary.features.task.ui.edit.internal.editor.TaskEditFormValidator;
import com.autosecretary.features.task.ui.edit.internal.editor.TaskEditSectionBinder;
import com.autosecretary.features.task.ui.edit.state.TaskEditDefaults;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;

import java.util.Collections;

/**
 * DialogFragment for creating and editing tasks. Manages five form sections:
 * basic info, scheduling, repetition, preferred slots, and progress.
 * Delegates form logic to {@link TaskEditFormController}. Works with
 * {@link TaskEditState} (mutable UI model) and delegates persistence plus
 * spinner reference-data loading to the ViewModel-owned edit session.
 */
public class TaskEditDialog extends DialogFragment {
    public static final String TAG_EDIT = "edit";
    public static final String TAG_CREATE = "create";

    private TaskEditViewModel editViewModel;
    private TaskEditFormController formController;
    private TaskEditState editState;
    private TaskEditFormValidator formValidator;
    private TaskEditFormInputReader formInputReader;
    private PrefSlotSectionController prefSlotSectionController;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        TaskEditViewModelFactory factory = AutoSecretaryApplication.from(requireContext())
                .getTaskEditViewModelFactory();
        editViewModel = new ViewModelProvider(requireActivity(), factory).get(TaskEditViewModel.class);
        TaskEditState selectedTask = editViewModel.getSelectedTask();
        if (selectedTask == null) {
            // ViewModel was reset (e.g. after process death + fragment restore). Nothing to edit.
            dismissAllowingStateLoss();
            return new AlertDialog.Builder(requireContext()).create();
        }
        editState = selectedTask;
        formController = new TaskEditFormController(editState, editViewModel::createDefaultPrefSlot);

        View rootView = LayoutInflater.from(getContext()).inflate(R.layout.task_editor_dialog, null);

        TaskEditSectionBinder sectionBinder = new TaskEditSectionBinder(this, rootView, editState, formController);
        TaskEditSectionBinder.BasicInfoViews basicInfo = sectionBinder.bindBasicInfo();
        TaskEditSectionBinder.SchedulingViews scheduling = sectionBinder.bindScheduling(
                Collections.emptyList(), Collections.emptyList());
        TaskEditSectionBinder.RepetitionViews repetition = sectionBinder.bindRepetition(
            () -> prefSlotSectionController.onRepetitionChanged());
        TaskEditSectionBinder.ProgressViews progress = sectionBinder.bindProgress();

        prefSlotSectionController = new PrefSlotSectionController(this, rootView, formController, repetition);
        prefSlotSectionController.rebuildPrefSlotUI();

        setupFeatureSections(rootView,
                repetition.toggleRepetition, progress.toggleProgress, scheduling.toggleBudget);
        setupWeitereOptionen(rootView, weitereOptionenHasData(editState, formController));
        setupLeisureToggle(rootView, basicInfo.leisureView,
                repetition.toggleRepetition, progress.toggleProgress);

        formInputReader = new TaskEditFormInputReader(
            basicInfo, scheduling, repetition, progress
        );
        formValidator = new TaskEditFormValidator(
            requireContext(), basicInfo, scheduling, repetition, progress, editState
        );

        loadReferenceData(sectionBinder, basicInfo, scheduling);

        return new AlertDialog.Builder(requireContext())
            .setTitle(editViewModel.isNewTask()
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
            editViewModel.saveEditedTask();
            dismiss();
        });

        View deleteButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (deleteButton == null) {
            return;
        }

        if (editViewModel.isNewTask()) {
            deleteButton.setVisibility(View.GONE);
            return;
        }

        deleteButton.setOnClickListener(v -> showDeleteConfirmDialog());
    }

    private void loadReferenceData(
            TaskEditSectionBinder sectionBinder,
            TaskEditSectionBinder.BasicInfoViews basicInfo,
            TaskEditSectionBinder.SchedulingViews scheduling) {
        editViewModel.loadReferenceData(referenceData -> {
            if (!isAdded()) {
                return;
            }
            sectionBinder.rebindCategorySpinner(basicInfo, referenceData.categories());
            sectionBinder.rebindBudgetSpinners(scheduling,
                    referenceData.budgetAccounts(),
                    referenceData.budgetCategories());
        });
    }

    /**
     * Wires the three feature sections (Wiederholung, Fortschritt, Budget). Each header carries a
     * visible on/off switch; the section body is shown exactly when its switch is on. The switches
     * are pre-set from the task's data by {@link TaskEditSectionBinder}, so a task that already has
     * (say) repetition data opens with that section expanded.
     */
    private void setupFeatureSections(View rootView,
                                      CompoundButton toggleRepetition,
                                      CompoundButton toggleProgress,
                                      CompoundButton toggleBudget) {
        bindFeatureSection(rootView.findViewById(R.id.WiederholungHeader), toggleRepetition,
                rootView.findViewById(R.id.RepetitionContainer));
        bindFeatureSection(rootView.findViewById(R.id.FortschrittHeader), toggleProgress,
                rootView.findViewById(R.id.ProgressContainer));
        bindFeatureSection(rootView.findViewById(R.id.BudgetHeader), toggleBudget,
                rootView.findViewById(R.id.BudgetContainer));
    }

    private void bindFeatureSection(View header, CompoundButton toggle, View container) {
        Runnable sync = () -> container.setVisibility(toggle.isChecked() ? View.VISIBLE : View.GONE);
        // Tapping the header toggles the switch; tapping the switch toggles itself. Either way the
        // container follows. We use click (not checked-change) listeners so we don't clobber the
        // binder's own checked-change listener on the repetition toggle.
        header.setOnClickListener(v -> {
            toggle.toggle();
            sync.run();
        });
        toggle.setOnClickListener(v -> sync.run());
        sync.run();
    }

    /**
     * Wires the "Weitere Optionen" expander: a plain show/hide with an arrow, no feature switch.
     * Auto-expanded when the task already has data in that block.
     */
    private void setupWeitereOptionen(View rootView, boolean autoExpand) {
        View header = rootView.findViewById(R.id.WeitereDetailsHeader);
        View container = rootView.findViewById(R.id.WeitereDetailsContainer);
        TextView label = rootView.findViewById(R.id.WeitereDetailsToggle);
        Runnable updateLabel = () -> label.setText(container.getVisibility() == View.VISIBLE
                ? R.string.task_editor_weitere_optionen_expanded
                : R.string.task_editor_weitere_optionen_collapsed);
        header.setOnClickListener(v -> {
            container.setVisibility(container.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            updateLabel.run();
        });
        container.setVisibility(autoExpand ? View.VISIBLE : View.GONE);
        updateLabel.run();
    }

    /** True when the "Weitere Optionen" block holds any non-default value (category lives above it now). */
    private boolean weitereOptionenHasData(TaskEditState editState, TaskEditFormController formController) {
        return (editState.description != null && !editState.description.isEmpty())
                || editState.startDate != null
                || formController.getEditableDeadline() != null
                || editState.fixedDate != null
                || editState.fixedStart != null
                || (editState.fixedDuration != null && editState.fixedDuration > 0)
                || editState.minDuration != TaskEditDefaults.MIN_DURATION
                || editState.maxDuration != TaskEditDefaults.MAX_DURATION
                || editState.cooldown != TaskEditDefaults.COOLDOWN
                || !editState.closeOnMiss;
    }

    /**
     * A leisure task carries no metrics or performance pressure, so its repetition and
     * progress sections are meaningless. When leisure is on we hide both sections (header +
     * container) and force their feature switches off so no repetition/progress data is saved.
     */
    private void setupLeisureToggle(View rootView, CompoundButton leisureView,
                                    CompoundButton toggleRepetition, CompoundButton toggleProgress) {
        View[] metricViews = {
            rootView.findViewById(R.id.WiederholungHeader),
            rootView.findViewById(R.id.RepetitionContainer),
            rootView.findViewById(R.id.FortschrittHeader),
            rootView.findViewById(R.id.ProgressContainer)
        };
        Runnable apply = () -> {
            boolean leisure = leisureView.isChecked();
            if (leisure) {
                toggleRepetition.setChecked(false);
                toggleProgress.setChecked(false);
            }
            int visibility = leisure ? View.GONE : View.VISIBLE;
            for (View metricView : metricViews) {
                // Containers collapse independently; only reveal the headers here.
                if (metricView.getId() == R.id.WiederholungHeader
                        || metricView.getId() == R.id.FortschrittHeader) {
                    metricView.setVisibility(visibility);
                } else if (leisure) {
                    metricView.setVisibility(View.GONE);
                }
            }
        };
        leisureView.setOnCheckedChangeListener((buttonView, isChecked) -> apply.run());
        apply.run();
    }

    private void showDeleteConfirmDialog() {
        DialogHelper.showDeleteConfirmation(requireContext(),
                R.string.task_edit_delete_title, R.string.task_edit_delete_message,
                () -> editViewModel.deleteSelectedTask(this::dismiss));
    }

}
