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

import com.autosecretary.R;
import com.autosecretary.app.AppCompositionRoot;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.task.application.TaskScheduleConfigService;
import com.autosecretary.features.task.data.TaskScheduleConfig;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TaskScheduleConfigDialog extends DialogFragment {
    private final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN);
    private final Map<DayOfWeek, TaskScheduleConfig> draftByDay = new EnumMap<>(DayOfWeek.class);

    private TaskScheduleConfigService scheduleConfigService;
    private LinearLayout container;
    private View loadingView;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AutoSecretaryApplication app = AutoSecretaryApplication.from(requireContext());
        AppCompositionRoot compositionRoot = app.getAppCompositionRoot();
        scheduleConfigService = compositionRoot.getTaskScheduleConfigService();

        View root = LayoutInflater.from(requireContext()).inflate(R.layout.task_schedule_fragment, null, false);
        container = root.findViewById(R.id.ScheduleConfigContainer);
        loadingView = root.findViewById(R.id.ScheduleConfigLoading);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.task_schedule_dialog_title)
                .setView(root)
                .setPositiveButton(R.string.task_schedule_dialog_save, null)
                .setNegativeButton(R.string.task_schedule_dialog_cancel, null)
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

        scheduleConfigService.loadAll(configs -> {
            draftByDay.clear();
            for (TaskScheduleConfig config : configs) {
                if (config.dayOfWeek != null) {
                    draftByDay.put(config.dayOfWeek, config);
                }
            }
            renderRows();
        });
    }

    private void renderRows() {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (DayOfWeek day : DayOfWeek.values()) {
            TaskScheduleConfig config = draftByDay.get(day);
            if (config == null) {
                config = new TaskScheduleConfig(day, LocalTime.of(6, 0), LocalTime.of(21, 0));
                draftByDay.put(day, config);
            }

            View row = inflater.inflate(R.layout.task_schedule_day_item, container, false);
            TextView dayLabel = row.findViewById(R.id.ScheduleDayLabel);
            Button startButton = row.findViewById(R.id.ScheduleStartButton);
            Button endButton = row.findViewById(R.id.ScheduleEndButton);

            dayLabel.setText(day.getDisplayName(TextStyle.FULL, Locale.GERMAN));
            startButton.setText(config.startTime.format(timeFormat));
            endButton.setText(config.endTime.format(timeFormat));

            startButton.setOnClickListener(v -> showTimePicker(config.startTime, picked -> {
                config.startTime = picked;
                startButton.setText(picked.format(timeFormat));
            }));
            endButton.setOnClickListener(v -> showTimePicker(config.endTime, picked -> {
                config.endTime = picked;
                endButton.setText(picked.format(timeFormat));
            }));

            container.addView(row);
        }

        loadingView.setVisibility(View.GONE);
        container.setVisibility(View.VISIBLE);
    }

    private void save() {
        List<TaskScheduleConfig> result = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            TaskScheduleConfig config = draftByDay.get(day);
            if (config == null) {
                continue;
            }
            if (!config.endTime.isAfter(config.startTime)) {
                Toast.makeText(requireContext(), R.string.task_schedule_validation_error, Toast.LENGTH_SHORT).show();
                return;
            }
            result.add(new TaskScheduleConfig(day, config.startTime, config.endTime));
        }

        scheduleConfigService.saveAll(result, () -> {
            Toast.makeText(requireContext(), R.string.task_schedule_saved, Toast.LENGTH_SHORT).show();
            dismiss();
        });
    }

    private void showTimePicker(LocalTime initial, TimePickedCallback callback) {
        new TimePickerDialog(
                requireContext(),
                (picker, hour, minute) -> callback.onPicked(LocalTime.of(hour, minute)),
                initial.getHour(),
                initial.getMinute(),
                true
        ).show();
    }

    private interface TimePickedCallback {
        void onPicked(LocalTime picked);
    }
}
