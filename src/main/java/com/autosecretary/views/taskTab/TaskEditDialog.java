package com.autosecretary.views.taskTab;

import androidx.fragment.app.DialogFragment;
import com.autosecretary.constants.Period;
import com.autosecretary.constants.Priority;
import com.autosecretary.database.task.Task;
import com.autosecretary.database.task.TaskPrefSlot;
import com.autosecretary.views.taskTab.TaskViewModel;
import androidx.appcompat.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.graphics.Typeface;
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
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.autosecretary.R;

public class TaskEditDialog extends DialogFragment {

    private TaskViewModel vm;
    private Task task;
    private View rootView;
    private List<TaskPrefSlot> editablePrefSlots;

    // Basic info
    private EditText titleView, descriptionView;
    private Spinner priorityView;

    // Scheduling
    private TextView deadlineView;
    private CheckBox closeOnMissView, adaptiveView;
    private EditText minDurationView, maxDurationView, cooldownView;
    private LocalDate editableDeadline;

    // Repetition
    private CheckBox toggleRepetition;
    private LinearLayout repetitionContainer;
    private EditText repsView, perPeriodView;
    private Spinner periodUnitView;
    private int lastRepsPerDay = -1;

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
        task = vm.selectedTask;

        rootView = LayoutInflater.from(getContext())
            .inflate(R.layout.fragment_task_editor, null);

        // Deep-copy prefSlots for non-destructive editing
        editablePrefSlots = new ArrayList<>();
        for (TaskPrefSlot ps : task.prefSlots) {
            TaskPrefSlot copy = new TaskPrefSlot();
            copy.id = ps.id;
            copy.taskId = ps.taskId;
            copy.days = ps.days != null ? EnumSet.copyOf(ps.days) : EnumSet.noneOf(DayOfWeek.class);
            copy.start = ps.start;
            editablePrefSlots.add(copy);
        }

        editableDeadline = task.core.deadline;

        bindBasicInfo();
        bindScheduling();
        bindRepetition();
        bindProgress();
        rebuildPrefSlotUI();

        return new AlertDialog.Builder(requireContext())
            .setTitle(vm.isNewTask ? "Task erstellen" : "Task bearbeiten")
            .setView(rootView)
            .setPositiveButton("Speichern", (d, which) -> {
                collectAllFields();
                vm.saveEditedTask();
            })
            .setNegativeButton("Abbrechen", null)
            .create();
    }

    // ===== Basic Info =====

    private void bindBasicInfo() {
        titleView = rootView.findViewById(R.id.EditTitle);
        descriptionView = rootView.findViewById(R.id.EditDescription);
        priorityView = rootView.findViewById(R.id.EditPriority);

        titleView.setText(task.core.title);
        descriptionView.setText(task.core.description);

        ArrayAdapter<Priority> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            Priority.values()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        priorityView.setAdapter(adapter);
        priorityView.setSelection(task.core.priority.ordinal());
    }

    // ===== Scheduling =====

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
            editableDeadline = null;
            updateDeadlineDisplay();
        });

        closeOnMissView.setChecked(task.core.closeOnMiss);
        minDurationView.setText(String.valueOf(task.core.minDuration));
        maxDurationView.setText(String.valueOf(task.core.maxDuration));
        cooldownView.setText(String.valueOf(task.core.cooldown));
        adaptiveView.setChecked(task.core.adaptive);
    }

    private void updateDeadlineDisplay() {
        if (editableDeadline != null) {
            deadlineView.setText(editableDeadline.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        } else {
            deadlineView.setText("Keine Frist");
        }
    }

    private void showDatePicker() {
        LocalDate current = editableDeadline != null ? editableDeadline : LocalDate.now();
        new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
            editableDeadline = LocalDate.of(year, month + 1, day);
            updateDeadlineDisplay();
        }, current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth()).show();
    }

    // ===== Repetition =====

    private void bindRepetition() {
        toggleRepetition = rootView.findViewById(R.id.ToggleRepetition);
        repetitionContainer = rootView.findViewById(R.id.RepetitionContainer);
        repsView = rootView.findViewById(R.id.EditReps);
        perPeriodView = rootView.findViewById(R.id.EditPerPeriod);
        periodUnitView = rootView.findViewById(R.id.EditPeriodUnit);

        boolean hasRepetition = task.core.repetition != null && task.core.repetition.reps > 0;
        toggleRepetition.setChecked(hasRepetition);
        repetitionContainer.setVisibility(hasRepetition ? View.VISIBLE : View.GONE);

        if (task.core.repetition != null) {
            repsView.setText(String.valueOf(task.core.repetition.reps));
            perPeriodView.setText(String.valueOf(task.core.repetition.perPeriod));
        }

        ArrayAdapter<Period> periodAdapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            Period.values()
        );
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        periodUnitView.setAdapter(periodAdapter);
        if (task.core.repetition != null && task.core.repetition.periodUnit != null) {
            periodUnitView.setSelection(task.core.repetition.periodUnit.ordinal());
        }

        // Initialize lastRepsPerDay before attaching listeners
        lastRepsPerDay = computeCurrentRepsPerDay();

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

    // ===== Progress =====

    private void bindProgress() {
        toggleProgress = rootView.findViewById(R.id.ToggleProgress);
        progressContainer = rootView.findViewById(R.id.ProgressContainer);
        unitView = rootView.findViewById(R.id.EditUnit);
        targetView = rootView.findViewById(R.id.EditTarget);
        currentView = rootView.findViewById(R.id.EditCurrent);
        resetPerRepView = rootView.findViewById(R.id.EditResetPerRep);
        minPerRepView = rootView.findViewById(R.id.EditMinPerRep);
        maxPerRepView = rootView.findViewById(R.id.EditMaxPerRep);

        boolean hasProgress = task.core.progress != null && task.core.progress.target > 0;
        toggleProgress.setChecked(hasProgress);
        progressContainer.setVisibility(hasProgress ? View.VISIBLE : View.GONE);

        if (task.core.progress != null) {
            unitView.setText(task.core.progress.unit != null ? task.core.progress.unit : "");
            targetView.setText(String.valueOf(task.core.progress.target));
            currentView.setText(String.valueOf(task.core.progress.current));
            resetPerRepView.setChecked(task.core.progress.resetPerRep);
            minPerRepView.setText(String.valueOf(task.core.progress.minPerRep));
            maxPerRepView.setText(String.valueOf(task.core.progress.maxPerRep));
        }

        toggleProgress.setOnCheckedChangeListener((btn, checked) -> {
            progressContainer.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
    }

    // ===== Repetition <-> PrefSlots Reactivity =====

    private void onRepetitionChanged() {
        int newRepsPerDay = computeCurrentRepsPerDay();
        if (newRepsPerDay == lastRepsPerDay) return;
        lastRepsPerDay = newRepsPerDay;

        int currentCount = editablePrefSlots.size();

        if (newRepsPerDay > currentCount) {
            for (int i = currentCount; i < newRepsPerDay; i++) {
                TaskPrefSlot newSlot = new TaskPrefSlot();
                newSlot.taskId = task.core.id;
                newSlot.days = EnumSet.allOf(DayOfWeek.class);
                newSlot.start = LocalTime.of(6, 0);
                editablePrefSlots.add(newSlot);
            }
        } else if (newRepsPerDay < currentCount && newRepsPerDay > 0) {
            while (editablePrefSlots.size() > newRepsPerDay) {
                editablePrefSlots.remove(editablePrefSlots.size() - 1);
            }
        }

        rebuildPrefSlotUI();
    }

    private int computeCurrentRepsPerDay() {
        if (!toggleRepetition.isChecked()) return 1;

        int reps = parseIntSafe(repsView.getText().toString(), 1);
        int perPeriod = parseIntSafe(perPeriodView.getText().toString(), 1);
        Period periodUnit = (Period) periodUnitView.getSelectedItem();
        if (periodUnit == null) periodUnit = Period.DAY;

        int periodInDays = periodUnit.value * perPeriod;
        if (periodInDays <= 0) periodInDays = 1;
        return (int) Math.ceil((double) reps / (double) periodInDays);
    }

    // ===== PrefSlot UI =====

    private void rebuildPrefSlotUI() {
        prefSlotContainer = rootView.findViewById(R.id.PrefSlotContainer);
        prefSlotContainer.removeAllViews();

        // Sort by start time (working copy)
        List<TaskPrefSlot> sorted = new ArrayList<>(editablePrefSlots);
        Collections.sort(sorted, (a, b) -> {
            if (a.start == null && b.start == null) return 0;
            if (a.start == null) return 1;
            if (b.start == null) return -1;
            return a.start.compareTo(b.start);
        });

        // Group into repetition buckets (existing algorithm)
        int repsPerDay = computeCurrentRepsPerDay();
        Map<Integer, List<TaskPrefSlot>> slotMap = new HashMap<>();
        Set<DayOfWeek> usedDays = new HashSet<>();
        List<TaskPrefSlot> remaining = new ArrayList<>(sorted);

        int currentRep = 1;
        while (currentRep <= repsPerDay) {
            Iterator<TaskPrefSlot> it = remaining.iterator();
            while (it.hasNext()) {
                TaskPrefSlot prefSlot = it.next();
                if (Collections.disjoint(prefSlot.days, usedDays)) {
                    usedDays.addAll(prefSlot.days);
                    slotMap.computeIfAbsent(currentRep, k -> new ArrayList<>()).add(prefSlot);
                    it.remove();
                }
            }
            usedDays.clear();
            currentRep++;
        }

        // Build UI rows
        for (int key = 1; key <= repsPerDay; key++) {
            TextView header = new TextView(requireContext());
            header.setText("Wiederholung " + key);
            header.setTypeface(null, Typeface.BOLD);
            header.setPadding(0, dpToPx(12), 0, dpToPx(4));
            prefSlotContainer.addView(header);

            List<TaskPrefSlot> slotsInGroup = slotMap.getOrDefault(key, Collections.emptyList());

            for (TaskPrefSlot prefSlot : slotsInGroup) {
                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dpToPx(16), dpToPx(4), 0, dpToPx(4));

                // Days text (clickable)
                TextView daysView = new TextView(requireContext());
                daysView.setText(formatDaysAsRanges(prefSlot.days));
                daysView.setPadding(0, dpToPx(4), dpToPx(16), dpToPx(4));
                daysView.setTextSize(14);

                Set<DayOfWeek> takenByOthers = computeTakenDays(prefSlot, slotsInGroup);
                daysView.setOnClickListener(v -> showDayPicker(prefSlot, takenByOthers));

                // Time text (clickable)
                TextView timeView = new TextView(requireContext());
                timeView.setText(prefSlot.start != null
                    ? prefSlot.start.format(DateTimeFormatter.ofPattern("HH:mm"))
                    : "--:--");
                timeView.setPadding(0, dpToPx(4), 0, dpToPx(4));
                timeView.setTextSize(14);
                timeView.setTypeface(Typeface.MONOSPACE);
                timeView.setOnClickListener(v -> showTimePicker(prefSlot, timeView));

                row.addView(daysView);
                row.addView(timeView);
                prefSlotContainer.addView(row);
            }
        }
    }

    // ===== Day Picker Dialog =====

    private void showDayPicker(TaskPrefSlot prefSlot, Set<DayOfWeek> takenByOthers) {
        DayOfWeek[] weekDays = {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        };
        String[] labels = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(dpToPx(8), dpToPx(16), dpToPx(8), dpToPx(16));
        layout.setGravity(Gravity.CENTER);

        boolean[] selected = new boolean[7];
        MaterialButton[] buttons = new MaterialButton[7];

        for (int i = 0; i < 7; i++) {
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
            btn.setPadding(dpToPx(4), 0, dpToPx(4), 0);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.setMargins(dpToPx(2), 0, dpToPx(2), 0);
            btn.setLayoutParams(params);

            if (isTaken) {
                btn.setEnabled(false);
            } else {
                if (isSelected) {
                    btn.setBackgroundColor(0xFF6200EE);
                    btn.setTextColor(0xFFFFFFFF);
                }

                final int index = i;
                btn.setOnClickListener(v -> {
                    selected[index] = !selected[index];
                    if (selected[index]) {
                        btn.setBackgroundColor(0xFF6200EE);
                        btn.setTextColor(0xFFFFFFFF);
                    } else {
                        btn.setBackgroundColor(0x00000000);
                        btn.setTextColor(0xFF6200EE);
                    }
                });
            }

            buttons[i] = btn;
            layout.addView(btn);
        }

        new AlertDialog.Builder(requireContext())
            .setTitle("Tage auswählen")
            .setView(layout)
            .setPositiveButton("OK", (d, w) -> {
                EnumSet<DayOfWeek> newDays = EnumSet.noneOf(DayOfWeek.class);
                for (int i = 0; i < 7; i++) {
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

    // ===== Time Picker =====

    private void showTimePicker(TaskPrefSlot prefSlot, TextView timeView) {
        int hour = prefSlot.start != null ? prefSlot.start.getHour() : 6;
        int minute = prefSlot.start != null ? prefSlot.start.getMinute() : 0;

        new TimePickerDialog(requireContext(), (picker, h, m) -> {
            prefSlot.start = LocalTime.of(h, m);
            timeView.setText(prefSlot.start.format(DateTimeFormatter.ofPattern("HH:mm")));
        }, hour, minute, true).show();
    }

    // ===== Weekday Formatting =====

    static String formatDaysAsRanges(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) return "Keine Tage";

        List<DayOfWeek> sorted = new ArrayList<>(days);
        Collections.sort(sorted);

        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < sorted.size()) {
            DayOfWeek rangeStart = sorted.get(i);
            DayOfWeek rangeEnd = rangeStart;

            while (i + 1 < sorted.size()
                    && sorted.get(i + 1).getValue() == sorted.get(i).getValue() + 1) {
                i++;
                rangeEnd = sorted.get(i);
            }

            String startLabel = dayLabel(rangeStart);
            if (rangeStart == rangeEnd) {
                parts.add(startLabel);
            } else {
                parts.add(startLabel + "-" + dayLabel(rangeEnd));
            }
            i++;
        }

        return String.join(" ", parts);
    }

    private static String dayLabel(DayOfWeek day) {
        return day.getDisplayName(TextStyle.SHORT, Locale.GERMAN).replace(".", "");
    }

    // ===== Save =====

    private void collectAllFields() {
        // Basic info
        task.core.title = titleView.getText().toString();
        task.core.description = descriptionView.getText().toString();
        task.core.priority = (Priority) priorityView.getSelectedItem();

        // Scheduling
        task.core.deadline = editableDeadline;
        task.core.closeOnMiss = closeOnMissView.isChecked();
        task.core.minDuration = parseIntSafe(minDurationView.getText().toString(), 5);
        task.core.maxDuration = parseIntSafe(maxDurationView.getText().toString(), 10);
        task.core.cooldown = parseIntSafe(cooldownView.getText().toString(), 1);
        task.core.adaptive = adaptiveView.isChecked();

        // Repetition
        if (toggleRepetition.isChecked()) {
            int newReps = parseIntSafe(repsView.getText().toString(), 1);
            int newPerPeriod = parseIntSafe(perPeriodView.getText().toString(), 1);
            Period newPeriodUnit = (Period) periodUnitView.getSelectedItem();

            boolean periodChanged =
                newReps != task.core.repetition.reps ||
                newPerPeriod != task.core.repetition.perPeriod ||
                newPeriodUnit != task.core.repetition.periodUnit;

            task.core.repetition.reps = newReps;
            task.core.repetition.perPeriod = newPerPeriod;
            task.core.repetition.periodUnit = newPeriodUnit;

            if (periodChanged || task.core.repetition.periodStart == null) {
                task.core.repetition.periodStart = LocalDate.now();
                task.core.repetition.periodCompletions = 0;
            }
        } else {
            task.core.repetition.reps = 0;
            task.core.repetition.perPeriod = 1;
            task.core.repetition.periodUnit = Period.DAY;
            task.core.repetition.periodCompletions = 0;
            task.core.repetition.periodStart = null;
        }

        // Progress
        if (toggleProgress.isChecked()) {
            task.core.progress.unit = unitView.getText().toString();
            task.core.progress.target = parseIntSafe(targetView.getText().toString(), 0);
            task.core.progress.current = parseIntSafe(currentView.getText().toString(), 0);
            task.core.progress.resetPerRep = resetPerRepView.isChecked();
            task.core.progress.minPerRep = parseIntSafe(minPerRepView.getText().toString(), 0);
            task.core.progress.maxPerRep = parseIntSafe(maxPerRepView.getText().toString(), 0);
        } else {
            task.core.progress.target = 0;
        }

        // PrefSlots
        task.prefSlots = new ArrayList<>(editablePrefSlots);
    }

    // ===== Helpers =====

    private Set<DayOfWeek> computeTakenDays(TaskPrefSlot current, List<TaskPrefSlot> groupSlots) {
        Set<DayOfWeek> taken = EnumSet.noneOf(DayOfWeek.class);
        for (TaskPrefSlot other : groupSlots) {
            if (other != current && other.days != null) {
                taken.addAll(other.days);
            }
        }
        return taken;
    }

    private static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ===== Inner helper classes =====

    private static abstract class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    private static abstract class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
        @Override public void onNothingSelected(AdapterView<?> parent) {}
    }
}
