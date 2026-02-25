package com.autosecretary.features.task.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
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
import com.autosecretary.features.task.ui.edit.TaskEditDialog;
import com.autosecretary.features.task.ui.edit.TaskEditSessionController;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

public class ListFragment extends Fragment {
    private TaskViewModel vm;

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
        TaskViewModelFactory viewModelFactory = compositionRoot.createTaskViewModelFactory();
        vm = new ViewModelProvider(requireActivity(), viewModelFactory).get(TaskViewModel.class);
        TaskEditSessionController editSessionController = vm.getTaskEditSessionController();

        ensureCalendarPermission();

        RecyclerView recyclerView = view.findViewById(R.id.TaskList);
        View emptyStateContainer = view.findViewById(R.id.EmptyStateContainer);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        ListRowAdapter adapter = new ListRowAdapter(
                new ArrayList<>(),
                vm::checkOff,
                viewSlot -> openEditDialog(editSessionController, viewSlot.item.taskId)
        );

        recyclerView.setAdapter(adapter);
        vm.getList().observe(getViewLifecycleOwner(), items -> {
            adapter.setList(items);
            boolean hasItems = items != null && !items.isEmpty();
            recyclerView.setVisibility(hasItems ? View.VISIBLE : View.GONE);
            emptyStateContainer.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        });

        Button generateButton = view.findViewById(R.id.Button);
        View newTaskButton = view.findViewById(R.id.NewTaskButton);
        generateButton.setOnClickListener(v -> vm.updateList());

        View.OnClickListener createTaskClickListener = v -> {
            editSessionController.createNewTask();
            new TaskEditDialog().show(getParentFragmentManager(), "create");
        };

        newTaskButton.setOnClickListener(createTaskClickListener);
        view.findViewById(R.id.EmptyStateNewTaskButton).setOnClickListener(createTaskClickListener);

        TextView dayNavPrev = view.findViewById(R.id.DayNavPrev);
        TextView dayNavLabel = view.findViewById(R.id.DayNavLabel);
        TextView dayNavNext = view.findViewById(R.id.DayNavNext);
        DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern("EEEE, d. MMM", Locale.GERMAN);

        dayNavPrev.setOnClickListener(v -> vm.navigatePreviousDay());
        dayNavNext.setOnClickListener(v -> vm.navigateNextDay());

        vm.getSelectedDay().observe(getViewLifecycleOwner(), day -> {
            boolean isToday = day.equals(LocalDate.now());
            dayNavLabel.setText(isToday ? "Heute" : day.format(dayFormat));

            dayNavPrev.setEnabled(!isToday);
            dayNavPrev.setAlpha(isToday ? 0.3f : 1.0f);

            boolean canGoForward = day.isBefore(LocalDate.now().plusDays(6));
            dayNavNext.setEnabled(canGoForward);
            dayNavNext.setAlpha(canGoForward ? 1.0f : 0.3f);

            generateButton.setVisibility(isToday ? View.VISIBLE : View.GONE);
            newTaskButton.setVisibility(isToday ? View.VISIBLE : View.GONE);

            adapter.setInteractionsEnabled(isToday);
        });

        MaterialButtonToggleGroup toggle = view.findViewById(R.id.TaskListToggle);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.ChecklistButton) {
                    vm.applyChecklistPreset();
                } else {
                    vm.applyManagePreset();
                }
            }
        });
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
        new TaskEditDialog().show(getParentFragmentManager(), "edit");
    }
}
