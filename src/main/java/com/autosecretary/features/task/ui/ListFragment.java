package com.autosecretary.features.task.ui;

import androidx.fragment.app.Fragment;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import com.google.android.material.button.MaterialButtonToggleGroup;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

import com.autosecretary.R;
import com.autosecretary.app.AppCompositionRoot;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.task.ui.edit.TaskEditDialog;
import com.autosecretary.features.task.ui.edit.TaskEditSessionController;

public class ListFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.task_list_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        AutoSecretaryApplication app = AutoSecretaryApplication.from(requireContext());
        AppCompositionRoot compositionRoot = app.getAppCompositionRoot();
        TaskViewModelFactory viewModelFactory = compositionRoot.createTaskViewModelFactory();
        // Keep one ViewModel tied to the activity so list/edit state survives fragment swaps and dialogs.
        TaskViewModel vm = new ViewModelProvider(requireActivity(), viewModelFactory).get(TaskViewModel.class);
        TaskEditSessionController editSessionController = vm.getTaskEditSessionController();
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
        // Generate rebuilds the schedule using current rules, then pushes the refreshed rows to this list.
        generateButton.setOnClickListener(v -> vm.updateList());

        View.OnClickListener createTaskClickListener = v -> {
            // Start from a blank task in shared state, then open the dialog so the user fills details.
            editSessionController.createNewTask();
            new TaskEditDialog().show(getParentFragmentManager(), "create");
        };

        newTaskButton.setOnClickListener(createTaskClickListener);
        view.findViewById(R.id.EmptyStateNewTaskButton).setOnClickListener(createTaskClickListener);

        // Day navigation: arrows to browse today through today+6
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
        // Checklist vs Manage toggles between focused presets for quick completion and planning workflows.
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
    private void openEditDialog(TaskEditSessionController editSessionController, String taskId) {
        // Explicit edit callback shared by row edit controls and optional long-press shortcut.
        editSessionController.beginEditTask(taskId);
        new TaskEditDialog().show(getParentFragmentManager(), "edit");
    }

}
