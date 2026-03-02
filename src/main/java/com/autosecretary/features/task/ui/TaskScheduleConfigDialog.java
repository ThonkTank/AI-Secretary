package com.autosecretary.features.task.ui;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.task.application.config.TaskScheduleConfigRepository;
import com.autosecretary.features.task.data.TaskScheduleConfig;
import com.autosecretary.shared.DateFormatters;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
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
    private final Map<DayOfWeek, TaskScheduleConfig> draftByDay = new EnumMap<>(DayOfWeek.class);

    private TaskScheduleConfigRepository scheduleConfigRepository;
    private ExecutorService executor;
    private Handler mainHandler;
    private LinearLayout container;
    private View loadingView;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AutoSecretaryApplication application = AutoSecretaryApplication.from(requireContext());
        scheduleConfigRepository = application.getAppCompositionRoot().getTaskScheduleConfigRepository();
        executor = application.getAppCompositionRoot().getSharedExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        View root = LayoutInflater.from(requireContext()).inflate(R.layout.task_schedule_config_dialog, null, false);
        container = root.findViewById(R.id.ScheduleConfigContainer);
        loadingView = root.findViewById(R.id.ScheduleConfigLoading);

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

        executor.execute(() -> {
            List<TaskScheduleConfig> configs = scheduleConfigRepository.loadAll();
            mainHandler.post(() -> {
                draftByDay.clear();
                for (TaskScheduleConfig config : configs) {
                    if (config.dayOfWeek != null) {
                        draftByDay.put(config.dayOfWeek, config);
                    }
                }
                renderRows();
            });
        });
    }

    private void renderRows() {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (DayOfWeek day : DayOfWeek.values()) {
            final TaskScheduleConfig rowConfig = draftByDay.get(day);

            View row = inflater.inflate(R.layout.task_schedule_day_item, container, false);
            TextView dayLabel = row.findViewById(R.id.ScheduleDayLabel);
            Button startButton = row.findViewById(R.id.ScheduleStartButton);
            Button endButton = row.findViewById(R.id.ScheduleEndButton);

            String dayName = day.getDisplayName(TextStyle.FULL, Locale.GERMAN);
            dayLabel.setText(dayName);
            updateTimeButton(startButton, rowConfig.startTime, dayName, true);
            updateTimeButton(endButton, rowConfig.endTime, dayName, false);

            startButton.setOnClickListener(v -> showTimePicker(rowConfig.startTime, picked -> {
                rowConfig.startTime = picked;
                updateTimeButton(startButton, picked, dayName, true);
            }));
            endButton.setOnClickListener(v -> showTimePicker(rowConfig.endTime, picked -> {
                rowConfig.endTime = picked;
                updateTimeButton(endButton, picked, dayName, false);
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

        executor.execute(() -> {
            scheduleConfigRepository.saveAll(result);
            mainHandler.post(() -> {
                Toast.makeText(requireContext(), R.string.task_schedule_saved, Toast.LENGTH_SHORT).show();
                dismiss();
            });
        });
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
