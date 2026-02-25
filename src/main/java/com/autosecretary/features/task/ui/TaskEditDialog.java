package com.autosecretary.features.task.ui;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.R;
import com.autosecretary.constants.Period;
import com.autosecretary.features.task.ui.internal.PrefSlotUIBuilder;
import com.autosecretary.features.task.ui.internal.editor.TaskEditFormInputBuilder;
import com.autosecretary.features.task.ui.internal.editor.TaskEditFormValidator;
import com.autosecretary.features.task.ui.internal.editor.TaskEditFormViews;
import com.autosecretary.features.task.ui.internal.editor.TaskEditSectionBinder;
import com.autosecretary.features.task.ui.internal.mapper.TaskEditStateMapper;
import com.autosecretary.features.task.ui.state.PrefSlotEditState;
import com.autosecretary.features.task.ui.state.TaskEditState;
import com.google.android.material.button.MaterialButton;

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
    private TaskEditFormInputBuilder formInputBuilder;
    private final TaskEditFormViews formViews = new TaskEditFormViews();
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
        formInputBuilder = new TaskEditFormInputBuilder();

        rootView = LayoutInflater.from(getContext())
            .inflate(R.layout.fragment_task_editor, null);
        prefSlotUIBuilder = new PrefSlotUIBuilder(requireContext());
        sectionBinder = new TaskEditSectionBinder(this, rootView, editState, presenter, formViews);

        sectionBinder.bindBasicInfo();
        sectionBinder.bindScheduling();
        sectionBinder.bindRepetition(this::onRepetitionChanged);
        sectionBinder.bindProgress();
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
            if (!formValidator.validateAndCollectAllFields(formViews)) {
                return;
            }
            presenter.applyForm(formInputBuilder.buildFormInput(formViews));
            editSessionController.saveEditedTask(presenter.toTaskForSave(editSessionController.requireSelectedBaseTask()));
            dismiss();
        });
    }

    // Repetition field change triggers prefSlot count recalculation and UI rebuild
    private void onRepetitionChanged() {
        boolean changed = presenter.onRepetitionChanged(
            formViews.toggleRepetition.isChecked(),
            formViews.repsView.getText().toString(),
            formViews.perPeriodView.getText().toString(),
            (Period) formViews.periodUnitView.getSelectedItem()
        );
        if (changed) {
            rebuildPrefSlotUI();
        }
    }

    private void rebuildPrefSlotUI() {
        prefSlotContainer = rootView.findViewById(R.id.PrefSlotContainer);
        int repsPerDay = presenter.computeCurrentRepsPerDay(
            formViews.toggleRepetition.isChecked(),
            formViews.repsView.getText().toString(),
            formViews.perPeriodView.getText().toString(),
            (Period) formViews.periodUnitView.getSelectedItem()
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
}
