package com.autosecretary.features.task.ui.edit;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.R;
import com.autosecretary.constants.Period;
import com.autosecretary.constants.Priority;
import com.autosecretary.features.task.ui.edit.internal.PrefSlotUIBuilder;
import com.autosecretary.features.task.ui.edit.internal.editor.TaskEditFormValidator;
import com.autosecretary.features.task.ui.edit.internal.editor.TaskEditSectionBinder;
import com.autosecretary.features.task.ui.edit.internal.mapper.TaskEditStateMapper;
import com.autosecretary.features.task.ui.TaskViewModel;
import com.autosecretary.features.task.ui.state.PrefSlotEditState;
import com.autosecretary.features.task.ui.state.TaskEditState;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

/**
 * DialogFragment for creating and editing tasks. Manages five form sections:
 * basic info, scheduling, repetition, preferred slots, and progress.
 * Delegates form logic to {@link TaskEditPresenter}. Works with
 * {@link TaskEditState} (mutable UI model), not {@link com.autosecretary.features.task.data.Task}
 * directly. On save, collects fields, applies via presenter, and converts
 * back to Task for persistence.
 */
public class TaskEditDialog extends DialogFragment {

    private static final int WEEK_DAY_COUNT = 7;
    private static final int DAY_PICKER_HORIZONTAL_PADDING_DP = 8;
    private static final int DAY_PICKER_VERTICAL_PADDING_DP = 16;
    private static final int DAY_BUTTON_HORIZONTAL_PADDING_DP = 4;
    private static final int DAY_BUTTON_HORIZONTAL_MARGIN_DP = 2;

    private TaskViewModel vm;
    private TaskEditSessionController editSessionController;
    private TaskEditState editState;
    private TaskEditPresenter presenter;
    private PrefSlotUIBuilder prefSlotUIBuilder;
    private TaskEditSectionBinder sectionBinder;
    private TaskEditFormValidator formValidator;

    private EditText titleView;
    private EditText descriptionView;
    private Spinner priorityView;

    private EditText deadlineView;
    private TextInputLayout deadlineInputLayout;
    private CheckBox closeOnMissView;
    private CheckBox adaptiveView;
    private EditText minDurationView;
    private EditText maxDurationView;
    private EditText cooldownView;

    private CheckBox toggleRepetition;
    private LinearLayout repetitionContainer;
    private EditText repsView;
    private EditText perPeriodView;
    private Spinner periodUnitView;

    private CheckBox toggleProgress;
    private LinearLayout progressContainer;
    private EditText unitView;
    private EditText targetView;
    private EditText currentView;
    private EditText minPerRepView;
    private EditText maxPerRepView;
    private CheckBox resetPerRepView;

    private View rootView;

    // PrefSlots
    private LinearLayout prefSlotContainer;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        editSessionController = vm.getTaskEditSessionController();
        editState = editSessionController.requireSelectedTask();
        presenter = new TaskEditPresenter(editState, new TaskEditStateMapper());
        formValidator = new TaskEditFormValidator();

        rootView = LayoutInflater.from(getContext())
            .inflate(R.layout.task_editor_fragment, null);
        prefSlotUIBuilder = new PrefSlotUIBuilder(requireContext());
        sectionBinder = new TaskEditSectionBinder(this, rootView, editState, presenter);

        TaskEditSectionBinder.BasicInfoViews basicInfoViews = sectionBinder.bindBasicInfo();
        titleView = basicInfoViews.titleView;
        descriptionView = basicInfoViews.descriptionView;
        priorityView = basicInfoViews.priorityView;

        TaskEditSectionBinder.SchedulingViews schedulingViews = sectionBinder.bindScheduling();
        deadlineView = schedulingViews.deadlineView;
        deadlineInputLayout = schedulingViews.deadlineInputLayout;
        closeOnMissView = schedulingViews.closeOnMissView;
        minDurationView = schedulingViews.minDurationView;
        maxDurationView = schedulingViews.maxDurationView;
        cooldownView = schedulingViews.cooldownView;
        adaptiveView = schedulingViews.adaptiveView;

        TaskEditSectionBinder.RepetitionViews repetitionViews = sectionBinder.bindRepetition(this::onRepetitionChanged);
        toggleRepetition = repetitionViews.toggleRepetition;
        repetitionContainer = repetitionViews.repetitionContainer;
        repsView = repetitionViews.repsView;
        perPeriodView = repetitionViews.perPeriodView;
        periodUnitView = repetitionViews.periodUnitView;

        TaskEditSectionBinder.ProgressViews progressViews = sectionBinder.bindProgress();
        toggleProgress = progressViews.toggleProgress;
        progressContainer = progressViews.progressContainer;
        unitView = progressViews.unitView;
        targetView = progressViews.targetView;
        currentView = progressViews.currentView;
        resetPerRepView = progressViews.resetPerRepView;
        minPerRepView = progressViews.minPerRepView;
        maxPerRepView = progressViews.maxPerRepView;

        rebuildPrefSlotUI();

        return new AlertDialog.Builder(requireContext())
            .setTitle(editSessionController.isNewTask() ? "Task erstellen" : "Task bearbeiten")
            .setView(rootView)
            // Save handler with validation is set in onStart() to prevent dialog auto-dismiss on errors
            .setPositiveButton("Speichern", null)
            .setNegativeButton("Abbrechen", null)
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
            if (!formValidator.validateAndCollectAllFields(
                titleView,
                minDurationView,
                maxDurationView,
                cooldownView,
                toggleRepetition,
                repsView,
                perPeriodView,
                toggleProgress,
                targetView,
                currentView,
                minPerRepView,
                maxPerRepView
            )) {
                return;
            }
            presenter.applyForm(readFormInput());
            editSessionController.saveEditedTask(presenter.toTaskForSave(editSessionController.requireSelectedBaseTask()));
            dismiss();
        });
    }

    // Repetition field change triggers prefSlot count recalculation and UI rebuild
    private void onRepetitionChanged() {
        boolean changed = presenter.onRepetitionChanged(
            toggleRepetition.isChecked(),
            repsView.getText().toString(),
            perPeriodView.getText().toString(),
            (Period) periodUnitView.getSelectedItem()
        );
        if (changed) {
            rebuildPrefSlotUI();
        }
    }

    private void rebuildPrefSlotUI() {
        prefSlotContainer = rootView.findViewById(R.id.PrefSlotContainer);
        int repsPerDay = presenter.computeCurrentRepsPerDay(
            toggleRepetition.isChecked(),
            repsView.getText().toString(),
            perPeriodView.getText().toString(),
            (Period) periodUnitView.getSelectedItem()
        );

        prefSlotUIBuilder.rebuild(prefSlotContainer, presenter.getEditablePrefSlots(), repsPerDay,
            new PrefSlotUIBuilder.Listener() {
                @Override
                public void onDaysClicked(PrefSlotEditState prefSlot, Set<DayOfWeek> takenByOthers) {
                    showDayPicker(prefSlot, takenByOthers);
                }

                @Override
                public void onTimeClicked(PrefSlotEditState prefSlot, TextView timeView) {
                    showTimePicker(prefSlot, timeView);
                }
            });
    }

    private void showDayPicker(PrefSlotEditState prefSlot, Set<DayOfWeek> takenByOthers) {
        DayOfWeek[] weekDays = {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        };
        String[] labels = getResources().getStringArray(R.array.task_edit_weekday_short_labels);

        if (labels.length != WEEK_DAY_COUNT) {
            throw new IllegalStateException("Expected exactly 7 localized weekday labels.");
        }

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(
            dpToPx(DAY_PICKER_HORIZONTAL_PADDING_DP),
            dpToPx(DAY_PICKER_VERTICAL_PADDING_DP),
            dpToPx(DAY_PICKER_HORIZONTAL_PADDING_DP),
            dpToPx(DAY_PICKER_VERTICAL_PADDING_DP)
        );
        layout.setGravity(Gravity.CENTER);

        boolean[] selected = new boolean[WEEK_DAY_COUNT];

        int selectedBackgroundColor = ContextCompat.getColor(requireContext(), R.color.task_edit_day_selected_background);
        int selectedTextColor = ContextCompat.getColor(requireContext(), R.color.task_edit_day_selected_text);
        int unselectedTextColor = ContextCompat.getColor(requireContext(), R.color.task_edit_day_unselected_text);
        int transparentBackgroundColor = ContextCompat.getColor(requireContext(), android.R.color.transparent);

        for (int i = 0; i < WEEK_DAY_COUNT; i++) {
            DayOfWeek day = weekDays[i];
            boolean isSelected = prefSlot.days != null && prefSlot.days.contains(day);
            boolean isTaken = takenByOthers.contains(day);

            selected[i] = isSelected;

            MaterialButton btn = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(labels[i]);
            btn.setMinWidth(0);
            btn.setMinimumWidth(0);
            btn.setInsetTop(0);
            btn.setInsetBottom(0);
            btn.setPadding(dpToPx(DAY_BUTTON_HORIZONTAL_PADDING_DP), 0, dpToPx(DAY_BUTTON_HORIZONTAL_PADDING_DP), 0);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.setMargins(dpToPx(DAY_BUTTON_HORIZONTAL_MARGIN_DP), 0, dpToPx(DAY_BUTTON_HORIZONTAL_MARGIN_DP), 0);
            btn.setLayoutParams(params);

            if (isTaken) {
                btn.setEnabled(false);
            } else {
                if (isSelected) {
                    btn.setBackgroundColor(selectedBackgroundColor);
                    btn.setTextColor(selectedTextColor);
                } else {
                    btn.setBackgroundColor(transparentBackgroundColor);
                    btn.setTextColor(unselectedTextColor);
                }

                final int index = i;
                btn.setOnClickListener(v -> {
                    selected[index] = !selected[index];
                    if (selected[index]) {
                        btn.setBackgroundColor(selectedBackgroundColor);
                        btn.setTextColor(selectedTextColor);
                    } else {
                        btn.setBackgroundColor(transparentBackgroundColor);
                        btn.setTextColor(unselectedTextColor);
                    }
                });
            }

            layout.addView(btn);
        }

        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.task_edit_day_picker_title)
            .setView(layout)
            .setPositiveButton("OK", (d, w) -> {
                EnumSet<DayOfWeek> newDays = EnumSet.noneOf(DayOfWeek.class);
                for (int i = 0; i < WEEK_DAY_COUNT; i++) {
                    if (selected[i]) {
                        newDays.add(weekDays[i]);
                    }
                }
                prefSlot.days = newDays;
                rebuildPrefSlotUI();
            })
            .setNegativeButton("Abbrechen", null)
            .show();
    }

    private void showTimePicker(PrefSlotEditState prefSlot, TextView timeView) {
        int hour = prefSlot.start != null ? prefSlot.start.getHour() : 6;
        int minute = prefSlot.start != null ? prefSlot.start.getMinute() : 0;

        new TimePickerDialog(requireContext(), (picker, h, m) -> {
            prefSlot.start = LocalTime.of(h, m);
            String formattedTime = prefSlot.start.format(DateTimeFormatter.ofPattern("HH:mm"));
            timeView.setText("Startzeit wählen: " + formattedTime);
            timeView.setContentDescription("Startzeit wählen. Aktuell: " + formattedTime);
        }, hour, minute, true).show();
    }


    private int dpToPx(int dp) {
        return (int) (dp * requireContext().getResources().getDisplayMetrics().density + 0.5f);
    }

    private TaskEditPresenter.FormInput readFormInput() {
        TaskEditPresenter.FormInput input = new TaskEditPresenter.FormInput();
        input.title = titleView.getText().toString();
        input.description = descriptionView.getText().toString();
        input.priority = TaskEditPresenter.coalesce(
            (Priority) priorityView.getSelectedItem(),
            TaskEditPresenter.InputDefaults.PRIORITY
        );

        input.closeOnMiss = closeOnMissView.isChecked();
        input.minDuration = TaskEditPresenter.parseIntSafe(
            minDurationView.getText().toString(),
            TaskEditPresenter.InputDefaults.MIN_DURATION
        );
        input.maxDuration = TaskEditPresenter.parseIntSafe(
            maxDurationView.getText().toString(),
            TaskEditPresenter.InputDefaults.MAX_DURATION
        );
        input.cooldown = TaskEditPresenter.parseIntSafe(
            cooldownView.getText().toString(),
            TaskEditPresenter.InputDefaults.COOLDOWN
        );
        input.adaptive = adaptiveView.isChecked();

        input.repetitionEnabled = toggleRepetition.isChecked();
        input.reps = TaskEditPresenter.parseIntSafe(
            repsView.getText().toString(),
            TaskEditPresenter.InputDefaults.REPETITION_REPS
        );
        input.perPeriod = TaskEditPresenter.parseIntSafe(
            perPeriodView.getText().toString(),
            TaskEditPresenter.InputDefaults.REPETITION_PER_PERIOD
        );
        input.periodUnit = TaskEditPresenter.coalesce(
            (Period) periodUnitView.getSelectedItem(),
            TaskEditPresenter.InputDefaults.REPETITION_PERIOD_UNIT
        );

        input.progressEnabled = toggleProgress.isChecked();
        input.unit = unitView.getText().toString();
        input.target = TaskEditPresenter.parseIntSafe(
            targetView.getText().toString(),
            TaskEditPresenter.InputDefaults.TARGET
        );
        input.current = TaskEditPresenter.parseIntSafe(
            currentView.getText().toString(),
            TaskEditPresenter.InputDefaults.CURRENT
        );
        input.resetPerRep = resetPerRepView.isChecked();
        input.minPerRep = TaskEditPresenter.parseIntSafe(
            minPerRepView.getText().toString(),
            TaskEditPresenter.InputDefaults.MIN_PER_REP
        );
        input.maxPerRep = TaskEditPresenter.parseIntSafe(
            maxPerRepView.getText().toString(),
            TaskEditPresenter.InputDefaults.MAX_PER_REP
        );

        return input;
    }
}
