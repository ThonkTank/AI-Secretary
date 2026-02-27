package com.autosecretary.features.task.ui.list;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.R;
import com.autosecretary.app.AppCompositionRoot;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.task.domain.scheduling.SchedulingConflict;
import com.autosecretary.features.task.ui.TaskScheduleConfigDialog;
import com.autosecretary.features.task.ui.edit.TaskEditDialog;
import com.autosecretary.features.task.ui.edit.TaskEditSessionController;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.snackbar.Snackbar;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

public class TaskListFragment extends Fragment {
    public static final String ARG_OPEN_CREATE_TASK = "open_create_task";

    private static final float ALPHA_NAV_ENABLED = 1.0f;
    private static final float ALPHA_NAV_DISABLED = 0.3f;

    private TaskViewModel vm;
    private boolean shouldOpenCreateTask;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        shouldOpenCreateTask = getArguments() != null
                && getArguments().getBoolean(ARG_OPEN_CREATE_TASK, false);
    }

    private final ActivityResultLauncher<String> calendarPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (vm != null) {
                    vm.onCalendarPermissionChanged(granted);
                }
            });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.task_list_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        AutoSecretaryApplication app = AutoSecretaryApplication.from(requireContext());
        AppCompositionRoot compositionRoot = app.getAppCompositionRoot();
        TaskViewModelFactory viewModelFactory = compositionRoot.getTaskViewModelFactory();
        vm = new ViewModelProvider(requireActivity(), viewModelFactory).get(TaskViewModel.class);
        TaskEditSessionController editSessionController = vm.getTaskEditSessionController();

        ensureCalendarPermission();

        RecyclerView recyclerView = view.findViewById(R.id.TaskList);
        View emptyStateContainer = view.findViewById(R.id.EmptyStateContainer);
        TextInputLayout taskSearchLayout = view.findViewById(R.id.TaskSearchLayout);
        TextInputEditText taskSearchInput = view.findViewById(R.id.TaskSearchInput);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        ListRowAdapter adapter = new ListRowAdapter(
                new ArrayList<>(),
                new ListRowAdapter.TaskRowActions(
                        vm::checkOff,
                        viewSlot -> openEditDialog(editSessionController, viewSlot.item.taskId),
                        vm::toggleTimer,
                        vm::incrementProgress,
                        vm::decrementProgress,
                        vm::toggleExpanded,
                        vm::isExpanded)
        );
        recyclerView.setAdapter(adapter);
        vm.getList().observe(getViewLifecycleOwner(), items -> {
            adapter.setList(items);
            boolean hasItems = items != null && !items.isEmpty();
            recyclerView.setVisibility(hasItems ? View.VISIBLE : View.GONE);
            emptyStateContainer.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        });

        vm.getScheduleConflicts().observe(getViewLifecycleOwner(), conflicts -> {
            if (conflicts == null || conflicts.isEmpty()) {
                return;
            }
            String message = getResources().getQuantityString(
                    R.plurals.task_list_schedule_complete, conflicts.size(), conflicts.size());
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
            for (SchedulingConflict conflict : conflicts) {
                Log.w("TaskScheduleConflict", "{taskId=" + conflict.taskId()
                        + ", title=" + conflict.title()
                        + ", day=" + conflict.day()
                        + ", reasonCode=" + conflict.reasonCode()
                        + ", details=" + conflict.details() + "}");
            }
        });

        vm.getSearchQuery().observe(getViewLifecycleOwner(), query -> {
            String currentValue = taskSearchInput.getText().toString();
            String normalizedQuery = query == null ? "" : query;
            if (!normalizedQuery.equals(currentValue)) {
                taskSearchInput.setText(normalizedQuery);
                taskSearchInput.setSelection(normalizedQuery.length());
            }
        });

        taskSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                vm.setSearchQuery(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        Button generateButton = view.findViewById(R.id.GenerateScheduleButton);
        Button scheduleConfigButton = view.findViewById(R.id.ScheduleConfigButton);
        View newTaskButton = view.findViewById(R.id.NewTaskButton);
        generateButton.setOnClickListener(v -> vm.updateList());
        scheduleConfigButton.setOnClickListener(v ->
                new TaskScheduleConfigDialog().show(getParentFragmentManager(), TaskScheduleConfigDialog.TAG)
        );

        View.OnClickListener createTaskClickListener = v -> openCreateTaskDialog(editSessionController);

        newTaskButton.setOnClickListener(createTaskClickListener);
        view.findViewById(R.id.EmptyStateNewTaskButton).setOnClickListener(createTaskClickListener);

        if (shouldOpenCreateTask) {
            shouldOpenCreateTask = false;
            view.post(() -> openCreateTaskDialog(editSessionController));
        }

        TextView dayNavPrev = view.findViewById(R.id.DayNavPrev);
        TextView dayNavLabel = view.findViewById(R.id.DayNavLabel);
        TextView dayNavNext = view.findViewById(R.id.DayNavNext);
        DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern("EEEE, d. MMM", Locale.GERMAN);

        dayNavPrev.setOnClickListener(v -> vm.navigatePreviousDay());
        dayNavNext.setOnClickListener(v -> vm.navigateNextDay());

        vm.getSelectedDay().observe(getViewLifecycleOwner(), day -> {
            boolean isToday = day.equals(LocalDate.now());
            dayNavLabel.setText(isToday ? getString(R.string.task_list_day_nav_today) : day.format(dayFormat));

            dayNavPrev.setEnabled(!isToday);
            dayNavPrev.setAlpha(isToday ? ALPHA_NAV_DISABLED : ALPHA_NAV_ENABLED);

            boolean canGoForward = day.isBefore(LocalDate.now().plusDays(TaskViewModel.MAX_DAY_OFFSET));
            dayNavNext.setEnabled(canGoForward);
            dayNavNext.setAlpha(canGoForward ? ALPHA_NAV_ENABLED : ALPHA_NAV_DISABLED);

            generateButton.setVisibility(isToday ? View.VISIBLE : View.GONE);
            newTaskButton.setVisibility(isToday ? View.VISIBLE : View.GONE);

            adapter.setInteractionsEnabled(isToday);
        });

        MaterialButtonToggleGroup toggle = view.findViewById(R.id.TaskListToggle);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.ChecklistButton) {
                    taskSearchLayout.setVisibility(View.GONE);
                    vm.applyChecklistPreset();
                } else {
                    taskSearchLayout.setVisibility(View.VISIBLE);
                    vm.applyManagePreset();
                }
                adapter.setManageMode(vm.isManageMode());
            }
        });

        taskSearchLayout.setVisibility(toggle.getCheckedButtonId() == R.id.ManagementButton ? View.VISIBLE : View.GONE);
    }

    private void ensureCalendarPermission() {
        boolean granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
        vm.onCalendarPermissionChanged(granted);
        if (!granted) {
            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR);
        }
    }

    private void openEditDialog(TaskEditSessionController editSessionController, String taskId) {
        editSessionController.beginEditTask(taskId);
        new TaskEditDialog().show(getParentFragmentManager(), TaskEditDialog.TAG_EDIT);
    }

    private void openCreateTaskDialog(TaskEditSessionController editSessionController) {
        editSessionController.createNewTask();
        new TaskEditDialog().show(getParentFragmentManager(), TaskEditDialog.TAG_CREATE);
    }
}
