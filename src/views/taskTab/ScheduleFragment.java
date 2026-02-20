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

import java.util.ArrayList;

import views.taskTab.TaskRowAdapter;
import views.models.ViewSlot;
import com.autosecretary.R;

public class ScheduleFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_schedule, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        TaskViewModel vm = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        RecyclerView recyclerView = view.findViewById(R.id.TaskList);
        TaskRowAdapter adapter = new TaskRowAdapter(new ArrayList<>(), slot -> vm.checkOff(slot));
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        vm.getCheckList().observe(getViewLifecycleOwner(), content -> {
            adapter.setList(content);
        });
            
        Button button = view.findViewById(R.id.Button);
        button.setOnClickListener(v -> {
            vm.updateList();
        });
    }
}
