package com.autosecretary.features.task.ui;

import androidx.fragment.app.Fragment;
import android.os.Bundle;
import android.widget.Button;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import com.google.android.material.button.MaterialButtonToggleGroup;
import java.util.ArrayList;

import com.autosecretary.R;
import com.autosecretary.app.AppCompositionRoot;

public class ListFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_task_list, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        AppCompositionRoot compositionRoot = new AppCompositionRoot(requireActivity().getApplication());
        TaskViewModelFactory viewModelFactory = compositionRoot.createTaskViewModelFactory();
        // Keep one ViewModel tied to the activity so list/edit state survives fragment swaps and dialogs.
        TaskViewModel vm = new ViewModelProvider(requireActivity(), viewModelFactory).get(TaskViewModel.class);
        RecyclerView recyclerView = view.findViewById(R.id.TaskList);
        View emptyStateContainer = view.findViewById(R.id.EmptyStateContainer);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        ListRowAdapter adapter = new ListRowAdapter(
                new ArrayList<>(),
                vm::checkOff,
                viewSlot -> openEditDialog(vm, viewSlot.item.taskId),
                viewSlot -> openEditDialog(vm, viewSlot.item.taskId)
        );

        recyclerView.setAdapter(adapter);
        vm.getList().observe(getViewLifecycleOwner(), items -> {
            adapter.setList(items);
            boolean hasItems = items != null && !items.isEmpty();
            recyclerView.setVisibility(hasItems ? View.VISIBLE : View.GONE);
            emptyStateContainer.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        });

        Button button = view.findViewById(R.id.Button);
        // Generate rebuilds the schedule using current rules, then pushes the refreshed rows to this list.
        button.setOnClickListener(v -> vm.updateList());

        View.OnClickListener createTaskClickListener = v -> {
            // Start from a blank task in shared state, then open the dialog so the user fills details.
            vm.createNewTask();
            new TaskEditDialog().show(getParentFragmentManager(), "create");
        };

        view.findViewById(R.id.NewTaskButton).setOnClickListener(createTaskClickListener);
        view.findViewById(R.id.EmptyStateNewTaskButton).setOnClickListener(createTaskClickListener);

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
    private void openEditDialog(TaskViewModel vm, String taskId) {
        // Explicit edit callback shared by row edit controls and optional long-press shortcut.
        vm.beginEditTask(taskId);
        new TaskEditDialog().show(getParentFragmentManager(), "edit");
    }

}
