package views.taskTab;

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

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.button.MaterialButton;

import java.time.LocalDate;
import java.util.ArrayList;

import views.taskTab.ListRowAdapter;
import views.models.ViewSlotList.ViewSlot;
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
        ListRowAdapter adapter = new ListRowAdapter(new ArrayList<>(), slot -> vm.checkOff(slot));
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        vm.getList().observe(getViewLifecycleOwner(), content -> {
            adapter.setList(content);
        });
            
        Button button = view.findViewById(R.id.Button);
        button.setOnClickListener(v -> {
            vm.updateList();
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
