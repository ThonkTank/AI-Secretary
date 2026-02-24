package views.taskTab;

import androidx.fragment.app.DialogFragment;
import constants.Priority;
import database.task.Task;
import views.taskTab.TaskViewModel;
import androidx.appcompat.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.R;

public class TaskEditDialog extends DialogFragment {
    EditText title;
    EditText description;
    Spinner priority; 

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        TaskViewModel vm = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        Task task = vm.selectedTask; 

        View view = LayoutInflater.from(getContext())
            .inflate(R.layout.fragment_task_editor, null);

        this.title = view.findViewById(R.id.EditTitle);
        this.description = view.findViewById(R.id.EditDescription);
        this.priority = view.findViewById(R.id.EditPriority);

        this.title.setText(task.core.title);
        this.description.setText(task.core.description);
        ArrayAdapter<Priority> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            Priority.values()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.priority.setAdapter(adapter);
        this.priority.setSelection(task.core.priority.ordinal());
        
        
        return new AlertDialog.Builder(requireContext())
            .setTitle("Task bearbeiten")
            .setView(view)
            .setPositiveButton("Speichern", (d, which) -> { /* speichern */ })
            .setNegativeButton("Abbrechen", null)
            .create();
    }

}
