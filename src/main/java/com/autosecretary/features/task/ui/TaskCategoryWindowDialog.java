package com.autosecretary.features.task.ui;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.R;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.task.domain.model.TaskCategory;
import com.autosecretary.features.task.domain.model.TaskCategoryWindow;
import com.autosecretary.shared.DateFormatters;
import com.autosecretary.shared.ui.SpinnerHelper;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Dialog for configuring per-weekday category time windows. Each weekday shows a list of blocks
 * (category picker + start/end time) that reserve part of the day's scheduling window for a
 * category; the scheduler fills a block first with that category, then fills any leftover reserved
 * time with other tasks (two-pass scheduling in {@code DefaultTaskSlotGenerator}). Blocks are
 * edited as in-memory drafts and persisted atomically on save.
 */
public class TaskCategoryWindowDialog extends DialogFragment {
    public static final String TAG = "category_window";

    private static final LocalTime DEFAULT_BLOCK_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_BLOCK_END = LocalTime.of(10, 0);

    private TaskCategoryWindowViewModel viewModel;
    private LinearLayout container;
    private View loadingView;

    private final List<TaskCategoryWindow> drafts = new ArrayList<>();
    private final Set<String> originalIds = new HashSet<>();
    private final List<String> deletedIds = new ArrayList<>();
    private List<TaskCategory> categories = new ArrayList<>();

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (viewModel == null) {
            TaskCategoryWindowViewModelFactory factory = AutoSecretaryApplication.from(requireContext())
                    .getTaskCategoryWindowViewModelFactory();
            viewModel = new ViewModelProvider(this, factory).get(TaskCategoryWindowViewModel.class);
        }

        View root = LayoutInflater.from(requireContext())
                .inflate(R.layout.task_category_window_dialog, null, false);
        container = root.findViewById(R.id.CategoryWindowContainer);
        loadingView = root.findViewById(R.id.CategoryWindowLoading);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.task_category_window_dialog_title)
                .setView(root)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        load();
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

    private void load() {
        loadingView.setVisibility(View.VISIBLE);
        container.setVisibility(View.GONE);
        viewModel.load((windows, loadedCategories) -> {
            drafts.clear();
            originalIds.clear();
            deletedIds.clear();
            drafts.addAll(windows);
            for (TaskCategoryWindow window : windows) {
                originalIds.add(window.id);
            }
            categories = loadedCategories;
            loadingView.setVisibility(View.GONE);
            container.setVisibility(View.VISIBLE);
            renderSections();
        });
    }

    private void renderSections() {
        container.removeAllViews();
        if (categories.isEmpty()) {
            container.addView(hintText(getString(R.string.task_category_window_no_categories)));
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (DayOfWeek day : DayOfWeek.values()) {
            container.addView(dayHeader(day));
            for (TaskCategoryWindow window : draftsForDay(day)) {
                container.addView(buildBlockRow(inflater, window));
            }
            container.addView(addBlockButton(day));
        }
    }

    private List<TaskCategoryWindow> draftsForDay(DayOfWeek day) {
        List<TaskCategoryWindow> forDay = new ArrayList<>();
        for (TaskCategoryWindow window : drafts) {
            if (window.dayOfWeek == day) {
                forDay.add(window);
            }
        }
        return forDay;
    }

    private View buildBlockRow(LayoutInflater inflater, TaskCategoryWindow window) {
        View row = inflater.inflate(R.layout.task_category_window_item, container, false);
        Spinner categorySpinner = row.findViewById(R.id.CategoryWindowCategory);
        Button startButton = row.findViewById(R.id.CategoryWindowStart);
        Button endButton = row.findViewById(R.id.CategoryWindowEnd);
        ImageButton deleteButton = row.findViewById(R.id.CategoryWindowDelete);

        SpinnerHelper.bindList(categorySpinner, categories,
                c -> c.icon + " " + c.name, requireContext());
        SpinnerHelper.setSelection(categorySpinner, categories, window.categoryId, c -> c.id);
        // Keep the draft's categoryId in sync with the picker (also fixes orphaned selections).
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String categoryId = SpinnerHelper.idAtPosition(categories, position, c -> c.id);
                if (categoryId != null) {
                    window.categoryId = categoryId;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        updateTimeButton(startButton, window.startTime);
        updateTimeButton(endButton, window.endTime);
        startButton.setOnClickListener(v -> showTimePicker(window.startTime, picked -> {
            window.startTime = picked;
            updateTimeButton(startButton, picked);
        }));
        endButton.setOnClickListener(v -> showTimePicker(window.endTime, picked -> {
            window.endTime = picked;
            updateTimeButton(endButton, picked);
        }));

        deleteButton.setOnClickListener(v -> {
            drafts.remove(window);
            if (originalIds.contains(window.id)) {
                deletedIds.add(window.id);
            }
            renderSections();
        });
        return row;
    }

    private Button addBlockButton(DayOfWeek day) {
        Button button = new Button(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setAllCaps(false);
        button.setText(R.string.task_category_window_add_block);
        button.setOnClickListener(v -> {
            TaskCategoryWindow window = new TaskCategoryWindow(
                    day, categories.get(0).id, DEFAULT_BLOCK_START, DEFAULT_BLOCK_END);
            drafts.add(window);
            renderSections();
        });
        return button;
    }

    private TextView dayHeader(DayOfWeek day) {
        TextView header = new TextView(requireContext());
        header.setText(day.getDisplayName(TextStyle.FULL, Locale.GERMAN));
        header.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        int padTop = getResources().getDimensionPixelSize(R.dimen.spacing_md);
        header.setPadding(0, padTop, 0, 0);
        return header;
    }

    private TextView hintText(String text) {
        TextView hint = new TextView(requireContext());
        hint.setText(text);
        hint.setGravity(Gravity.CENTER);
        int pad = getResources().getDimensionPixelSize(R.dimen.spacing_lg);
        hint.setPadding(pad, pad, pad, pad);
        return hint;
    }

    private void save() {
        for (TaskCategoryWindow window : drafts) {
            if (window.categoryId == null) {
                Toast.makeText(requireContext(), R.string.task_category_window_category_required,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (window.startTime == null || window.endTime == null || !window.endTime.isAfter(window.startTime)) {
                Toast.makeText(requireContext(), R.string.task_category_window_invalid_range,
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }
        viewModel.save(new ArrayList<>(drafts), new ArrayList<>(deletedIds), () -> {
            Toast.makeText(requireContext(), R.string.task_category_window_saved, Toast.LENGTH_SHORT).show();
            dismiss();
        });
    }

    private void updateTimeButton(Button button, LocalTime time) {
        button.setText(time.format(DateFormatters.TIME_HH_MM));
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
