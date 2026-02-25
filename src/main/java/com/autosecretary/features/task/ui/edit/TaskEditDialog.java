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
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.DimenRes;
import androidx.appcompat.app.AlertDialog;
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
    private static final int DAY_PICKER_COLUMN_COUNT = 4;

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
            .setTitle(editSessionController.isNewTask()
                ? R.string.task_edit_dialog_title_create
                : R.string.task_edit_dialog_title_edit)
            .setView(rootView)
            // Save handler with validation is set in onStart() to prevent dialog auto-dismiss on errors
            .setPositiveButton(R.string.task_edit_dialog_positive, null)
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

        GridLayout layout = new GridLayout(requireContext());
        layout.setColumnCount(DAY_PICKER_COLUMN_COUNT);
        layout.setPadding(
            dimenPx(R.dimen.task_editor_day_picker_horizontal_padding),
            dimenPx(R.dimen.task_editor_day_picker_vertical_padding),
            dimenPx(R.dimen.task_editor_day_picker_horizontal_padding),
            dimenPx(R.dimen.task_editor_day_picker_vertical_padding)
        );
        layout.setUseDefaultMargins(false);
        layout.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

        boolean[] selected = new boolean[WEEK_DAY_COUNT];

        for (int i = 0; i < WEEK_DAY_COUNT; i++) {
            DayOfWeek day = weekDays[i];
            boolean isSelected = prefSlot.days != null && prefSlot.days.contains(day);
            boolean isTaken = takenByOthers.contains(day);

            selected[i] = isSelected;

            MaterialButton btn = new MaterialButton(requireContext(), null, 0,
                R.style.Widget_AutoSecretary_TaskEdit_DayPickerButton);
            btn.setText(labels[i]);
            btn.setMaxLines(2);
            btn.setMinWidth(0);
            btn.setMinimumWidth(0);
            btn.setCheckable(true);
            btn.setChecked(isSelected);
            btn.setInsetTop(0);
            btn.setInsetBottom(0);
            int dayButtonHorizontalPadding = dimenPx(R.dimen.task_editor_day_button_horizontal_padding);
            btn.setPadding(dayButtonHorizontalPadding, 0, dayButtonHorizontalPadding, 0);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(i / DAY_PICKER_COLUMN_COUNT, 1f),
                GridLayout.spec(i % DAY_PICKER_COLUMN_COUNT, 1f)
            );
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.setGravity(Gravity.FILL_HORIZONTAL);
            int dayButtonHorizontalMargin = dimenPx(R.dimen.task_editor_day_button_horizontal_margin);
            int dayButtonVerticalMargin = dimenPx(R.dimen.task_editor_day_button_vertical_margin);
            params.setMargins(
                dayButtonHorizontalMargin,
                dayButtonVerticalMargin,
                dayButtonHorizontalMargin,
                dayButtonVerticalMargin
            );
            btn.setLayoutParams(params);

            if (isTaken) {
                btn.setEnabled(false);
            } else {
                final int index = i;
                btn.setOnClickListener(v -> {
                    selected[index] = !selected[index];
                    btn.setChecked(selected[index]);
                });
            }

            layout.addView(btn);
        }

        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.task_edit_day_picker_title)
            .setView(layout)
            .setPositiveButton(R.string.task_edit_day_picker_positive, (d, w) -> {
                EnumSet<DayOfWeek> newDays = EnumSet.noneOf(DayOfWeek.class);
                for (int i = 0; i < WEEK_DAY_COUNT; i++) {
                    if (selected[i]) {
                        newDays.add(weekDays[i]);
                    }
                }
                prefSlot.days = newDays;
                rebuildPrefSlotUI();
            })
            .setNegativeButton(R.string.task_edit_day_picker_negative, null)
            .show();
    }

    private void showTimePicker(PrefSlotEditState prefSlot, TextView timeView) {
        int hour = prefSlot.start != null ? prefSlot.start.getHour() : 6;
        int minute = prefSlot.start != null ? prefSlot.start.getMinute() : 0;

        new TimePickerDialog(requireContext(), (picker, h, m) -> {
            prefSlot.start = LocalTime.of(h, m);
            String formattedTime = prefSlot.start.format(DateTimeFormatter.ofPattern("HH:mm"));
            timeView.setText(getString(R.string.task_editor_pref_slot_time_button, formattedTime));
            timeView.setContentDescription(getString(
                R.string.task_editor_pref_slot_time_content_description,
                formattedTime
            ));
        }, hour, minute, true).show();
    }


    private int dimenPx(@DimenRes int dimenResId) {
        return requireContext().getResources().getDimensionPixelSize(dimenResId);
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
