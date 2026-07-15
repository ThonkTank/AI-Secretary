package com.autosecretary.features.task.ui.list;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;

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
import com.google.android.material.chip.ChipGroup;
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
 * <p>Four display modes are available via a scrollable chip row:
 * <ul>
 *   <li><b>Checkliste</b> — the day's agenda: slots for the selected day, sorted by time.</li>
 *   <li><b>Verwalten</b> — all tasks grouped under category headers, with search.</li>
 *   <li><b>Priorität</b> — all open tasks flat, sorted by priority, then deadline.</li>
 *   <li><b>Frist</b> — open one-off tasks with a deadline, nearest first, with a
 *       remaining-time bar.</li>
 * </ul>
 *
 * <p>The header shows the selected day (with prev/next chevrons) in the day-scoped modes and
 * the mode title in the flat modes, where day navigation is hidden and the list stays
 * interactive regardless of the selected day.</p>
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
     * 6. Header (headline, day chevrons) + mode chips + interaction gating
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
        setupHeaderAndModeChips(view, taskSearchLayout, adapter, newTaskFab);
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

    /**
     * Wires the header (headline + day chevrons), the mode chip row, and the shared
     * interaction gate. The headline and chevrons render from the active mode and selected
     * day via {@link #renderHeaderState}; the flat modes (Priorität/Frist) hide the chevrons
     * and keep the list interactive regardless of the selected day.
     */
    private void setupHeaderAndModeChips(View view,
                                         TextInputLayout taskSearchLayout,
                                         ListRowAdapter adapter,
                                         FloatingActionButton newTaskFab) {
        TextView headerTitle = view.findViewById(R.id.TaskHeaderTitle);
        ImageButton dayPrev = view.findViewById(R.id.TaskDayPrev);
        ImageButton dayNext = view.findViewById(R.id.TaskDayNext);
        ChipGroup modeChips = view.findViewById(R.id.TaskModeChips);

        dayPrev.setOnClickListener(v -> vm.navigatePreviousDay());
        dayNext.setOnClickListener(v -> vm.navigateNextDay());

        Runnable renderHeader = () -> renderHeaderState(headerTitle, dayPrev, dayNext, adapter, newTaskFab);

        modeChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            applyModeForChip(checkedIds.get(0));
            taskSearchLayout.setVisibility(vm.isManageMode() ? View.VISIBLE : View.GONE);
            adapter.setDisplayMode(vm.activeListConfig());
            renderHeader.run();
        });

        // The activity-scoped ViewModel keeps its mode across fragment recreation (tab switches),
        // while the XML default re-checks the checklist chip — sync the chips to the ViewModel.
        modeChips.check(chipIdFor(vm.activeListConfig()));
        taskSearchLayout.setVisibility(vm.isManageMode() ? View.VISIBLE : View.GONE);

        vm.getSelectedDay().observe(getViewLifecycleOwner(), day -> renderHeader.run());
        renderHeader.run();
    }

    private void applyModeForChip(int chipId) {
        if (chipId == R.id.TaskModeManageChip) {
            vm.applyManagePreset();
        } else if (chipId == R.id.TaskModeUrgencyChip) {
            vm.applyUrgencyPreset();
        } else if (chipId == R.id.TaskModeDeadlineChip) {
            vm.applyDeadlinePreset();
        } else {
            vm.applyChecklistPreset();
        }
    }

    private static int chipIdFor(ListConfig config) {
        switch (config) {
            case MANAGE:
                return R.id.TaskModeManageChip;
            case URGENCY:
                return R.id.TaskModeUrgencyChip;
            case DEADLINE:
                return R.id.TaskModeDeadlineChip;
            case CHECKLIST:
            default:
                return R.id.TaskModeChecklistChip;
        }
    }

    /**
     * Renders the header and the interaction gate from the active mode and selected day.
     * Day-scoped modes show "Heute"/the date plus chevrons and are read-only on other days;
     * the flat modes show their title, hide the chevrons, and stay interactive (task creation
     * and checkoff are day-independent there).
     */
    private void renderHeaderState(TextView headerTitle,
                                   ImageButton dayPrev,
                                   ImageButton dayNext,
                                   ListRowAdapter adapter,
                                   FloatingActionButton newTaskFab) {
        ListConfig config = vm.activeListConfig();
        LocalDate day = vm.getSelectedDay().getValue();
        boolean dayScoped = config.isDayScoped();
        boolean isToday = day != null && day.equals(LocalDate.now());

        if (!dayScoped) {
            headerTitle.setText(config == ListConfig.URGENCY
                    ? R.string.task_list_header_urgency
                    : R.string.task_list_header_deadline);
            dayPrev.setVisibility(View.GONE);
            dayNext.setVisibility(View.GONE);
        } else {
            headerTitle.setText(day == null || isToday
                    ? getString(R.string.task_list_day_nav_today)
                    : day.format(DateFormatters.DAY_NAV_LABEL));
            dayPrev.setVisibility(View.VISIBLE);
            dayNext.setVisibility(View.VISIBLE);
            dayPrev.setEnabled(!isToday);
            dayPrev.setAlpha(isToday ? UiConstants.ALPHA_DISABLED : UiConstants.ALPHA_ENABLED);
            boolean canGoForward = day != null
                    && day.isBefore(LocalDate.now().plusDays(TaskViewModel.MAX_DAY_OFFSET));
            dayNext.setEnabled(canGoForward);
            dayNext.setAlpha(canGoForward ? UiConstants.ALPHA_ENABLED : UiConstants.ALPHA_DISABLED);
        }

        boolean interactive = !dayScoped || isToday;
        if (interactive) {
            newTaskFab.show();
        } else {
            newTaskFab.hide();
        }
        adapter.setInteractionsEnabled(interactive);
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
