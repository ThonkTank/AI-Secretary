package com.autosecretary.features.task.ui.list;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;

import com.autosecretary.shared.ui.SimpleButtonCheckedListener;
import com.autosecretary.shared.ui.SimpleTextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.R;
import com.autosecretary.app.AppCompositionRoot;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.task.ui.edit.TaskEditDialog;
import com.autosecretary.features.task.ui.edit.TaskEditViewModel;
import com.autosecretary.features.task.ui.edit.TaskEditViewModelFactory;
import com.autosecretary.shared.ui.UiConstants;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;


import com.autosecretary.shared.DateFormatters;

import java.time.LocalDate;
import java.util.ArrayList;

/**
 * Main task list screen. Shows scheduled task slots for a selected day and lets the user
 * check them off, track progress, start/stop timers, and open the task editor.
 *
 * <p>Two display modes are available via a toggle:
 * <ul>
 *   <li><b>Checklist mode</b> — slots scheduled for the selected day, sorted by time.</li>
 *   <li><b>Manage mode</b> — all tasks grouped by parent-child hierarchy, with search.</li>
 * </ul>
 *
 * <p>See {@link TaskViewModel} for the data flow, {@link ListConfig} for mode definitions,
 * and {@code README.md} in this package for an overview.
 */
public class TaskListFragment extends Fragment {
    /**
     * Boolean argument: if {@code true}, the create-task dialog is opened immediately after
     * the view is created. Used by the home screen widget's "new task" shortcut.
     */
    public static final String ARG_OPEN_CREATE_TASK = "open_create_task";

    private TaskViewModel vm;
    private TaskEditViewModel taskEditViewModel;
    /** Set to true when ARG_OPEN_CREATE_TASK is present; consumed once on first view creation. */
    private boolean shouldOpenCreateTask;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        shouldOpenCreateTask = getArguments() != null
                && getArguments().getBoolean(ARG_OPEN_CREATE_TASK, false);
    }

    /**
     * Handles the result of the READ_CALENDAR permission request.
     * When granted, the ViewModel re-runs the filter pipeline so calendar events
     * are appended to the display list. When denied, calendar rows remain hidden.
     */
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

    /**
     * Wires the full task list UI in this order:
     * 1. ViewModel + calendar permission
     * 2. RecyclerView + adapter with action callbacks
     * 3. LiveData observers (display list, schedule conflicts, search query sync)
     * 4. Search bar text watcher
     * 5. Action buttons (new task)
     * 6. Day navigation (prev/next arrows, date label, interaction gate)
     * 7. Checklist/Manage mode toggle
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = createViewModel();
        taskEditViewModel = createTaskEditViewModel();
        taskEditViewModel.getChangeVersion().observe(getViewLifecycleOwner(), version -> {
            if (version != null && version > 0) {
                vm.refreshList();
            }
        });

        ensureCalendarPermission();

        TextInputLayout taskSearchLayout = view.findViewById(R.id.TaskSearchLayout);
        TextInputEditText taskSearchInput = view.findViewById(R.id.TaskSearchInput);
        View emptyStateContainer = configureEmptyState(view);
        ListRowAdapter adapter = setupList(view, emptyStateContainer, taskSearchInput);
        View newTaskButton = setupCreateTaskButton(view);
        setupDayNavigation(view, adapter, newTaskButton);
        setupModeToggle(view, taskSearchLayout, adapter);
    }

    private TaskViewModel createViewModel() {
        AutoSecretaryApplication app = AutoSecretaryApplication.from(requireContext());
        AppCompositionRoot compositionRoot = app.getAppCompositionRoot();
        TaskViewModelFactory viewModelFactory = compositionRoot.getTaskViewModelFactory();
        return new ViewModelProvider(requireActivity(), viewModelFactory).get(TaskViewModel.class);
    }

    private TaskEditViewModel createTaskEditViewModel() {
        AutoSecretaryApplication app = AutoSecretaryApplication.from(requireContext());
        AppCompositionRoot compositionRoot = app.getAppCompositionRoot();
        TaskEditViewModelFactory viewModelFactory = compositionRoot.getTaskEditViewModelFactory();
        return new ViewModelProvider(requireActivity(), viewModelFactory).get(TaskEditViewModel.class);
    }

    private View configureEmptyState(View view) {
        View emptyStateContainer = view.findViewById(R.id.TaskEmptyState);
        ((TextView) view.findViewById(R.id.EmptyStateTitle)).setText(R.string.task_list_empty_title);
        ((TextView) view.findViewById(R.id.EmptyStateSubtitle)).setText(R.string.task_list_empty_subtitle);
        return emptyStateContainer;
    }

    private ListRowAdapter setupList(View view,
                                     View emptyStateContainer,
                                     TextInputEditText taskSearchInput) {
        RecyclerView recyclerView = view.findViewById(R.id.TaskList);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        ListRowAdapter adapter = new ListRowAdapter(
                new ArrayList<>(),
                new ListRowAdapter.TaskRowActions(
                        vm::checkOff,
                        vm::undoCheckOff,
                        viewSlot -> openEditDialog(viewSlot.getItem().taskId),
                        vm::incrementProgress,
                        vm::decrementProgress,
                        vm::toggleExpanded,
                        vm::isExpanded)
        );
        adapter.setManageMode(vm.isManageMode());
        recyclerView.setAdapter(adapter);

        vm.getList().observe(getViewLifecycleOwner(), items -> {
            adapter.setList(items);
            boolean hasItems = items != null && !items.isEmpty();
            recyclerView.setVisibility(hasItems ? View.VISIBLE : View.GONE);
            emptyStateContainer.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        });
        vm.getSearchQuery().observe(getViewLifecycleOwner(), query -> syncSearchInput(taskSearchInput, query));
        taskSearchInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                vm.setSearchQuery(s == null ? "" : s.toString());
            }
        });
        return adapter;
    }

    private void syncSearchInput(TextInputEditText taskSearchInput, String query) {
        String currentValue = taskSearchInput.getText().toString();
        String normalizedQuery = query == null ? "" : query;
        if (!normalizedQuery.equals(currentValue)) {
            taskSearchInput.setText(normalizedQuery);
            taskSearchInput.setSelection(normalizedQuery.length());
        }
    }

    private View setupCreateTaskButton(View view) {
        View newTaskButton = view.findViewById(R.id.NewTaskButton);
        newTaskButton.setOnClickListener(v -> openCreateTaskDialog());
        if (shouldOpenCreateTask) {
            shouldOpenCreateTask = false;
            view.post(this::openCreateTaskDialog);
        }
        return newTaskButton;
    }

    private void setupDayNavigation(View view, ListRowAdapter adapter, View newTaskButton) {
        ImageButton dayNavPrev = view.findViewById(R.id.NavPrev);
        TextView dayNavLabel = view.findViewById(R.id.NavLabel);
        ImageButton dayNavNext = view.findViewById(R.id.NavNext);
        dayNavPrev.setContentDescription(getString(R.string.task_list_day_nav_prev_desc));
        dayNavNext.setContentDescription(getString(R.string.task_list_day_nav_next_desc));
        dayNavPrev.setOnClickListener(v -> vm.navigatePreviousDay());
        dayNavNext.setOnClickListener(v -> vm.navigateNextDay());

        vm.getSelectedDay().observe(getViewLifecycleOwner(), day -> {
            boolean isToday = day.equals(LocalDate.now());
            dayNavLabel.setText(isToday ? getString(R.string.task_list_day_nav_today) : day.format(DateFormatters.DAY_NAV_LABEL));
            dayNavPrev.setEnabled(!isToday);
            dayNavPrev.setAlpha(isToday ? UiConstants.ALPHA_DISABLED : UiConstants.ALPHA_ENABLED);

            boolean canGoForward = day.isBefore(LocalDate.now().plusDays(TaskViewModel.MAX_DAY_OFFSET));
            dayNavNext.setEnabled(canGoForward);
            dayNavNext.setAlpha(canGoForward ? UiConstants.ALPHA_ENABLED : UiConstants.ALPHA_DISABLED);

            newTaskButton.setVisibility(isToday ? View.VISIBLE : View.GONE);
            adapter.setInteractionsEnabled(isToday);
        });
    }

    private void setupModeToggle(View view, TextInputLayout taskSearchLayout, ListRowAdapter adapter) {
        MaterialButtonToggleGroup toggle = view.findViewById(R.id.TaskListToggle);
        toggle.addOnButtonCheckedListener(new SimpleButtonCheckedListener() {
            @Override
            public void onChecked(MaterialButtonToggleGroup group, int checkedId) {
                boolean checklistMode = checkedId == R.id.TaskChecklistButton;
                taskSearchLayout.setVisibility(checklistMode ? View.GONE : View.VISIBLE);
                if (checklistMode) {
                    vm.applyChecklistPreset();
                } else {
                    vm.applyManagePreset();
                }
                adapter.setManageMode(vm.isManageMode());
            }
        });
        taskSearchLayout.setVisibility(toggle.getCheckedButtonId() == R.id.TaskManagementButton ? View.VISIBLE : View.GONE);
    }

    private void ensureCalendarPermission() {
        boolean granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
        vm.onCalendarPermissionChanged(granted);
        if (!granted) {
            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR);
        }
    }

    private void openEditDialog(String taskId) {
        taskEditViewModel.beginEditTask(taskId, () ->
            new TaskEditDialog().show(getParentFragmentManager(), TaskEditDialog.TAG_EDIT));
    }

    private void openCreateTaskDialog() {
        taskEditViewModel.createNewTask();
        new TaskEditDialog().show(getParentFragmentManager(), TaskEditDialog.TAG_CREATE);
    }
}
