package com.autosecretary.views.taskTab;

//Android
import androidx.fragment.app.Fragment;
import android.os.Bundle;
import android.widget.Button;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.button.MaterialButton;

import java.time.LocalDate;
import java.util.ArrayList;

import com.autosecretary.views.taskTab.ListRowAdapter;
import com.autosecretary.views.models.ViewSlotList.ViewSlot;
import com.autosecretary.R;

public class ListFragment extends Fragment {
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_task_list, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        TaskViewModel vm = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        RecyclerView recyclerView = view.findViewById(R.id.TaskList);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        ListRowAdapter adapter = new ListRowAdapter(
            new ArrayList<>(),
            viewSlot -> vm.checkOff(viewSlot),
            viewSlot -> {
                vm.selectedTask = viewSlot.task;
                new TaskEditDialog().show(getParentFragmentManager(), "edit");
            }
        );

        recyclerView.setAdapter(adapter);
        vm.getList().observe(getViewLifecycleOwner(), content -> {
            adapter.setList(content);
        });
            
        Button button = view.findViewById(R.id.Button);
        button.setOnClickListener(v -> {
            vm.updateList();
        });

        view.findViewById(R.id.NewTaskButton).setOnClickListener(v -> {
            vm.createNewTask();
            new TaskEditDialog().show(getParentFragmentManager(), "create");
        });

        MaterialButtonToggleGroup toggle = view.findViewById(R.id.TaskListToggle);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.ChecklistButton) {
                    vm.filters.day = LocalDate.now();
                    vm.filters.displayUnscheduled = false;
                    vm.sorters.byTaskParent = false;
                    vm.sorters.byScore = false;
                    vm.sorters.byTime = true;
                    vm.sorters.byTitle = false;
                } else {
                    vm.filters.day = LocalDate.now();
                    vm.filters.displayUnscheduled = true;
                    vm.sorters.byTaskParent = true;
                    vm.sorters.byScore = false;
                    vm.sorters.byTime = false;
                    vm.sorters.byTitle = true;
                }
                vm.filterList();
            }
        });
    }
}
