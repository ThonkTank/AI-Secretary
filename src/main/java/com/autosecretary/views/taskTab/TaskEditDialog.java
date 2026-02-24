package com.autosecretary.views.taskTab;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
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
import com.autosecretary.views.taskTab.mapper.TaskEditStateMapper;
import com.autosecretary.views.taskTab.model.PrefSlotEditState;
import com.autosecretary.views.taskTab.model.TaskEditState;
import com.google.android.material.button.MaterialButton;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

public class TaskEditDialog extends DialogFragment {

    private static final int WEEK_DAY_COUNT = 7;
    private static final int DAY_PICKER_HORIZONTAL_PADDING_DP = 8;
    private static final int DAY_PICKER_VERTICAL_PADDING_DP = 16;
    private static final int DAY_BUTTON_HORIZONTAL_PADDING_DP = 4;
    private static final int DAY_BUTTON_HORIZONTAL_MARGIN_DP = 2;

    private TaskViewModel vm;
    private TaskEditState editState;
    private TaskEditPresenter presenter;
    private PrefSlotUIBuilder prefSlotUIBuilder;
    private View rootView;

    // Basic info
    private EditText titleView, descriptionView;
    private Spinner priorityView;

    // Scheduling
    private TextView deadlineView;
    private CheckBox closeOnMissView, adaptiveView;
    private EditText minDurationView, maxDurationView, cooldownView;

    // Repetition
    private CheckBox toggleRepetition;
    private LinearLayout repetitionContainer;
    private EditText repsView, perPeriodView;
    private Spinner periodUnitView;

    // Progress
    private CheckBox toggleProgress;
    private LinearLayout progressContainer;
    private EditText unitView, targetView, currentView, minPerRepView, maxPerRepView;
    private CheckBox resetPerRepView;

    // PrefSlots
    private LinearLayout prefSlotContainer;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        editState = vm.requireSelectedTask();
        presenter = new TaskEditPresenter(editState, new TaskEditStateMapper());

        rootView = LayoutInflater.from(getContext())
            .inflate(R.layout.fragment_task_editor, null);
        prefSlotUIBuilder = new PrefSlotUIBuilder(requireContext(), this::dpToPx);

        bindBasicInfo();
        bindScheduling();
        bindRepetition();
        bindProgress();
        rebuildPrefSlotUI();

        return new AlertDialog.Builder(requireContext())
            .setTitle(vm.isNewTask() ? "Task erstellen" : "Task bearbeiten")
            .setView(rootView)
            .setPositiveButton("Speichern", (d, which) -> {
                collectAllFields();
                vm.saveEditedTask(presenter.toTaskForSave(vm.requireSelectedBaseTask()));
            })
            .setNegativeButton("Abbrechen", null)
            .create();
    }

    private void bindBasicInfo() {
        titleView = rootView.findViewById(R.id.EditTitle);
        descriptionView = rootView.findViewById(R.id.EditDescription);
        priorityView = rootView.findViewById(R.id.EditPriority);

        titleView.setText(editState.title);
        descriptionView.setText(editState.description);

        ArrayAdapter<Priority> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            Priority.values()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        priorityView.setAdapter(adapter);
        priorityView.setSelection(editState.priority.ordinal());
    }

    private void bindScheduling() {
        deadlineView = rootView.findViewById(R.id.EditDeadline);
        ImageButton clearDeadline = rootView.findViewById(R.id.ClearDeadline);
        closeOnMissView = rootView.findViewById(R.id.EditCloseOnMiss);
        minDurationView = rootView.findViewById(R.id.EditMinDuration);
        maxDurationView = rootView.findViewById(R.id.EditMaxDuration);
        cooldownView = rootView.findViewById(R.id.EditCooldown);
        adaptiveView = rootView.findViewById(R.id.EditAdaptive);

        updateDeadlineDisplay();
        deadlineView.setOnClickListener(v -> showDatePicker());
        clearDeadline.setOnClickListener(v -> {
            presenter.setEditableDeadline(null);
            updateDeadlineDisplay();
        });

        closeOnMissView.setChecked(editState.closeOnMiss);
        minDurationView.setText(String.valueOf(editState.minDuration));
        maxDurationView.setText(String.valueOf(editState.maxDuration));
        cooldownView.setText(String.valueOf(editState.cooldown));
        adaptiveView.setChecked(editState.adaptive);
    }

    private void updateDeadlineDisplay() {
        if (presenter.getEditableDeadline() != null) {
            deadlineView.setText(presenter.getEditableDeadline().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        } else {
            deadlineView.setText("Keine Frist");
        }
    }

    private void showDatePicker() {
        LocalDate current = presenter.getEditableDeadline() != null ? presenter.getEditableDeadline() : LocalDate.now();
        new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
            presenter.setEditableDeadline(LocalDate.of(year, month + 1, day));
            updateDeadlineDisplay();
        }, current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth()).show();
    }

    private void bindRepetition() {
        toggleRepetition = rootView.findViewById(R.id.ToggleRepetition);
        repetitionContainer = rootView.findViewById(R.id.RepetitionContainer);
        repsView = rootView.findViewById(R.id.EditReps);
        perPeriodView = rootView.findViewById(R.id.EditPerPeriod);
        periodUnitView = rootView.findViewById(R.id.EditPeriodUnit);

        boolean hasRepetition = editState.reps > 0;
        toggleRepetition.setChecked(hasRepetition);
        repetitionContainer.setVisibility(hasRepetition ? View.VISIBLE : View.GONE);

        repsView.setText(String.valueOf(editState.reps > 0 ? editState.reps : 1));
        perPeriodView.setText(String.valueOf(editState.perPeriod > 0 ? editState.perPeriod : 1));

        ArrayAdapter<Period> periodAdapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            Period.values()
        );
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        periodUnitView.setAdapter(periodAdapter);
        periodUnitView.setSelection((editState.periodUnit != null ? editState.periodUnit : Period.DAY).ordinal());

        presenter.initializeRepetitionState(
            toggleRepetition.isChecked(),
            repsView.getText().toString(),
            perPeriodView.getText().toString(),
            (Period) periodUnitView.getSelectedItem()
        );

        toggleRepetition.setOnCheckedChangeListener((btn, checked) -> {
            repetitionContainer.setVisibility(checked ? View.VISIBLE : View.GONE);
            onRepetitionChanged();
        });

        TextWatcher repWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                onRepetitionChanged();
            }
        };
        repsView.addTextChangedListener(repWatcher);
        perPeriodView.addTextChangedListener(repWatcher);

        periodUnitView.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onRepetitionChanged();
            }
        });
    }

    private void bindProgress() {
        toggleProgress = rootView.findViewById(R.id.ToggleProgress);
        progressContainer = rootView.findViewById(R.id.ProgressContainer);
        unitView = rootView.findViewById(R.id.EditUnit);
        targetView = rootView.findViewById(R.id.EditTarget);
        currentView = rootView.findViewById(R.id.EditCurrent);
        resetPerRepView = rootView.findViewById(R.id.EditResetPerRep);
        minPerRepView = rootView.findViewById(R.id.EditMinPerRep);
        maxPerRepView = rootView.findViewById(R.id.EditMaxPerRep);

        boolean hasProgress = editState.target > 0;
        toggleProgress.setChecked(hasProgress);
        progressContainer.setVisibility(hasProgress ? View.VISIBLE : View.GONE);

        unitView.setText(editState.unit != null ? editState.unit : "");
        targetView.setText(String.valueOf(editState.target));
        currentView.setText(String.valueOf(editState.current));
        resetPerRepView.setChecked(editState.resetPerRep);
        minPerRepView.setText(String.valueOf(editState.minPerRep));
        maxPerRepView.setText(String.valueOf(editState.maxPerRep));

        toggleProgress.setOnCheckedChangeListener((btn, checked) ->
            progressContainer.setVisibility(checked ? View.VISIBLE : View.GONE));
    }

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
            timeView.setText(prefSlot.start.format(DateTimeFormatter.ofPattern("HH:mm")));
        }, hour, minute, true).show();
    }

    private void collectAllFields() {
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

        presenter.applyForm(input);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static abstract class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    private static abstract class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
        @Override public void onNothingSelected(AdapterView<?> parent) {}
    }
}
