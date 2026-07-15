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
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.R;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.task.domain.scheduling.SchedulingConflict;
import com.autosecretary.features.task.ui.edit.TaskEditDialog;
import com.autosecretary.features.task.ui.edit.TaskEditViewModel;
import com.autosecretary.features.task.ui.edit.TaskEditViewModelFactory;
import com.autosecretary.shared.ui.UiConstants;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;


import com.autosecretary.shared.DateFormatters;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Main task list screen. Shows scheduled task slots for a selected day and lets the user
 * check them off, track progress, start/stop timers, and open the task editor.
 *
 * <p>Four display modes are available via a toggle:
 * <ul>
 *   <li><b>Checklist mode</b> — slots scheduled for the selected day, sorted by time.</li>
 *   <li><b>Manage mode</b> — all tasks grouped by parent-child hierarchy, with search.</li>
 *   <li><b>Urgency mode</b> — all open tasks flat, sorted by priority, then deadline.</li>
 *   <li><b>Deadline mode</b> — open one-off tasks with a deadline, nearest first, with a
 *       remaining-time bar.</li>
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
        FloatingActionButton newTaskFab = setupCreateTaskButton(view);
        setupDayNavigation(view, adapter, newTaskFab);
        setupModeToggle(view, taskSearchLayout, adapter);
        observeSchedulingConflicts(view);

        // The fragment is recreated on every tab switch while the ViewModel is activity-scoped, so a
        // refresh here reflects any mutation made elsewhere (e.g. an assistant task change or undo)
        // without cross-ViewModel wiring.
        vm.refreshList();
    }

    /** Surfaces regeneration conflicts once as a Snackbar with an optional detail dialog. */
    private void observeSchedulingConflicts(View view) {
        vm.getSchedulingConflicts().observe(getViewLifecycleOwner(), conflicts -> {
            if (conflicts == null || conflicts.isEmpty()) {
                return;
            }
            Snackbar.make(view,
                            getResources().getQuantityString(R.plurals.task_schedule_conflicts_summary,
                                    conflicts.size(), conflicts.size()),
                            Snackbar.LENGTH_LONG)
                    .setAction(R.string.task_schedule_conflicts_details, v -> showConflictDetails(conflicts))
                    .show();
            vm.consumeSchedulingConflicts();
        });
    }

    private void showConflictDetails(List<SchedulingConflict> conflicts) {
        StringBuilder message = new StringBuilder();
        for (SchedulingConflict conflict : conflicts) {
            message.append(getString(R.string.task_schedule_conflict_line,
                            conflict.title(), getString(reasonLabelRes(conflict.reasonCode()))))
                    .append('\n');
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.task_schedule_conflicts_title)
                .setMessage(message.toString().trim())
                .setPositiveButton(R.string.action_ok, null)
                .show();
    }

    private static int reasonLabelRes(SchedulingConflict.ReasonCode code) {
        switch (code) {
            case OUTSIDE_WINDOW:
                return R.string.task_conflict_reason_outside_window;
            case CALENDAR_OVERLAP:
                return R.string.task_conflict_reason_calendar_overlap;
            case PREREQUISITE_BLOCKED:
                return R.string.task_conflict_reason_prerequisite_blocked;
            case NO_MATCHING_GAP:
            default:
                return R.string.task_conflict_reason_no_matching_gap;
        }
    }

    private TaskViewModel createViewModel() {
        TaskViewModelFactory viewModelFactory = taskUiDependencies().getTaskViewModelFactory();
        return new ViewModelProvider(requireActivity(), viewModelFactory).get(TaskViewModel.class);
    }

    private TaskEditViewModel createTaskEditViewModel() {
        TaskEditViewModelFactory viewModelFactory = taskUiDependencies().getTaskEditViewModelFactory();
        return new ViewModelProvider(requireActivity(), viewModelFactory).get(TaskEditViewModel.class);
    }

    private AutoSecretaryApplication taskUiDependencies() {
        return AutoSecretaryApplication.from(requireContext());
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
        adapter.setDisplayMode(vm.activeListConfig());
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

    private FloatingActionButton setupCreateTaskButton(View view) {
        FloatingActionButton newTaskFab = view.findViewById(R.id.NewTaskFab);
        newTaskFab.setOnClickListener(v -> openCreateTaskDialog());
        if (shouldOpenCreateTask) {
            shouldOpenCreateTask = false;
            view.post(this::openCreateTaskDialog);
        }
        return newTaskFab;
    }

    private void setupDayNavigation(View view, ListRowAdapter adapter, FloatingActionButton newTaskFab) {
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

            if (isToday) {
                newTaskFab.show();
            } else {
                newTaskFab.hide();
            }
            adapter.setInteractionsEnabled(isToday);
        });
    }

    private void setupModeToggle(View view, TextInputLayout taskSearchLayout, ListRowAdapter adapter) {
        MaterialButtonToggleGroup toggle = view.findViewById(R.id.TaskListToggle);
        toggle.addOnButtonCheckedListener(new SimpleButtonCheckedListener() {
            @Override
            public void onChecked(MaterialButtonToggleGroup group, int checkedId) {
                if (checkedId == R.id.TaskChecklistButton) {
                    vm.applyChecklistPreset();
                } else if (checkedId == R.id.TaskManagementButton) {
                    vm.applyManagePreset();
                } else if (checkedId == R.id.TaskUrgencyButton) {
                    vm.applyUrgencyPreset();
                } else {
                    vm.applyDeadlinePreset();
                }
                taskSearchLayout.setVisibility(vm.isManageMode() ? View.VISIBLE : View.GONE);
                adapter.setDisplayMode(vm.activeListConfig());
            }
        });
        // The activity-scoped ViewModel keeps its mode across fragment recreation (tab switches),
        // while the XML default re-checks the checklist button — sync the toggle to the ViewModel.
        toggle.check(toggleButtonIdFor(vm.activeListConfig()));
        taskSearchLayout.setVisibility(vm.isManageMode() ? View.VISIBLE : View.GONE);
    }

    private static int toggleButtonIdFor(ListConfig config) {
        switch (config) {
            case MANAGE:
                return R.id.TaskManagementButton;
            case URGENCY:
                return R.id.TaskUrgencyButton;
            case DEADLINE:
                return R.id.TaskDeadlineButton;
            case CHECKLIST:
            default:
                return R.id.TaskChecklistButton;
        }
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
