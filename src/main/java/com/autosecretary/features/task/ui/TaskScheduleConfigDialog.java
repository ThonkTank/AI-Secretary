package com.autosecretary.features.task.ui;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import com.autosecretary.R;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.task.application.config.SchedulingSettings;
import com.autosecretary.features.task.ui.state.DayScheduleRow;
import com.autosecretary.shared.DateFormatters;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dialog for configuring per-day scheduling windows (start and end times for each day of the week).
 *
 * <p>These windows define when the scheduler is allowed to place task slots on a given day.
 * For example, setting Monday 08:00–18:00 means the scheduler will only generate Monday slots
 * within that time range. The windows are persisted via {@link TaskScheduleConfigRepository}
 * and consumed by {@code DefaultTaskSlotGenerator} in the domain layer when it builds
 * candidate slots during daily planning.
 *
 * <p>The dialog loads the current per-day configs on open, shows one row per day with
 * time-picker buttons, and saves all rows atomically on confirmation. The positive button
 * listener is attached in {@link #onStart()} (not in the builder) so that validation
 * failures can prevent dismissal — the same pattern used in {@code TaskEditDialog}.
 */
public class TaskScheduleConfigDialog extends DialogFragment {
    public static final String TAG = "schedule_config";
    /** Draft edits keyed by day. Mutated in place by time-picker callbacks; written to DB on save. */
    private final Map<DayOfWeek, DayScheduleRow> draftByDay =
            new EnumMap<>(DayOfWeek.class);

    private TaskScheduleConfigViewModel viewModel;
    private LinearLayout container;
    private View loadingView;
    private TextInputEditText pauseInput;
    private TextInputEditText leadInput;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (viewModel == null) {
            TaskScheduleConfigViewModelFactory factory = AutoSecretaryApplication.from(requireContext())
                    .getTaskScheduleConfigViewModelFactory();
            viewModel = new ViewModelProvider(this, factory).get(TaskScheduleConfigViewModel.class);
        }

        View root = LayoutInflater.from(requireContext()).inflate(R.layout.task_schedule_config_dialog, null, false);
        container = root.findViewById(R.id.ScheduleConfigContainer);
        loadingView = root.findViewById(R.id.ScheduleConfigLoading);

        SwitchMaterial schedulingEnabledSwitch = root.findViewById(R.id.SchedulingEnabledSwitch);
        schedulingEnabledSwitch.setChecked(SchedulingSettings.isSchedulingEnabled(requireContext()));
        // Persist immediately and regenerate so the checklist reflects the new state at once
        // (off clears it; on rebuilds today's schedule).
        schedulingEnabledSwitch.setOnCheckedChangeListener((button, checked) -> {
            SchedulingSettings.setSchedulingEnabled(requireContext(), checked);
            viewModel.regenerateSchedule();
        });

        pauseInput = root.findViewById(R.id.SchedulingPauseInput);
        leadInput = root.findViewById(R.id.SchedulingLeadInput);
        pauseInput.setText(String.valueOf(SchedulingSettings.getSlotPauseMinutes(requireContext())));
        leadInput.setText(String.valueOf(SchedulingSettings.getAppointmentLeadMinutes(requireContext())));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.task_schedule_dialog_title)
                .setView(root)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        loadConfigs();
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) {
            return;
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> save());
    }

    private void loadConfigs() {
        loadingView.setVisibility(View.VISIBLE);
        container.setVisibility(View.GONE);
        viewModel.loadConfigs(configs -> {
            draftByDay.clear();
            for (DayScheduleRow config : configs) {
                draftByDay.put(config.dayOfWeek(), config);
            }
            renderRows();
        });
    }

    private void renderRows() {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (DayOfWeek day : DayOfWeek.values()) {
            final DayScheduleRow rowConfig = draftByDay.get(day);

            View row = inflater.inflate(R.layout.task_schedule_day_item, container, false);
            TextView dayLabel = row.findViewById(R.id.ScheduleDayLabel);
            Button startButton = row.findViewById(R.id.ScheduleStartButton);
            Button endButton = row.findViewById(R.id.ScheduleEndButton);

            String dayName = day.getDisplayName(TextStyle.FULL, Locale.GERMAN);
            dayLabel.setText(dayName);
            updateTimeButton(startButton, rowConfig.startTime(), dayName, true);
            updateTimeButton(endButton, rowConfig.endTime(), dayName, false);

            startButton.setOnClickListener(v -> showTimePicker(rowConfig.startTime(), picked -> {
                updateDraftRow(day, picked, null);
                updateTimeButton(startButton, picked, dayName, true);
            }));
            endButton.setOnClickListener(v -> showTimePicker(rowConfig.endTime(), picked -> {
                updateDraftRow(day, null, picked);
                updateTimeButton(endButton, picked, dayName, false);
            }));

            container.addView(row);
        }

        loadingView.setVisibility(View.GONE);
        container.setVisibility(View.VISIBLE);
    }

    private void save() {
        saveBufferSettings();
        viewModel.saveRows(new ArrayList<>(draftByDay.values()), () -> {
            Toast.makeText(requireContext(), R.string.task_schedule_saved, Toast.LENGTH_SHORT).show();
            dismiss();
        }, () -> Toast.makeText(requireContext(),
                R.string.task_schedule_validation_error,
                Toast.LENGTH_SHORT).show());
    }

    /** Persists the buffer fields; a blank or unparsable field keeps the stored value. */
    private void saveBufferSettings() {
        Integer pause = parseMinutes(pauseInput);
        if (pause != null) {
            SchedulingSettings.setSlotPauseMinutes(requireContext(), pause);
        }
        Integer lead = parseMinutes(leadInput);
        if (lead != null) {
            SchedulingSettings.setAppointmentLeadMinutes(requireContext(), lead);
        }
    }

    private static Integer parseMinutes(TextInputEditText input) {
        if (input == null || input.getText() == null) {
            return null;
        }
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Math.max(0, Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void updateDraftRow(DayOfWeek day, LocalTime startTime, LocalTime endTime) {
        DayScheduleRow current = draftByDay.get(day);
        if (current == null) {
            return;
        }
        draftByDay.put(day, new DayScheduleRow(
                day,
                startTime != null ? startTime : current.startTime(),
                endTime != null ? endTime : current.endTime()));
    }

    private void updateTimeButton(Button button, LocalTime time, String dayName, boolean isStart) {
        String formatted = time.format(DateFormatters.TIME_HH_MM);
        button.setText(formatted);
        button.setContentDescription(getString(
                isStart ? R.string.task_schedule_start_desc : R.string.task_schedule_end_desc,
                dayName, formatted));
    }

    private void showTimePicker(LocalTime initial, Consumer<LocalTime> callback) {
        new TimePickerDialog(
                requireContext(),
                (picker, hour, minute) -> callback.accept(LocalTime.of(hour, minute)),
                initial.getHour(),
                initial.getMinute(),
                true
        ).show();
    }
}
