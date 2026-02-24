package views.taskTab;

import androidx.fragment.app.DialogFragment;
import constants.Priority;
import database.task.Task;
import database.task.TaskPrefSlot;
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

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.autosecretary.R;

public class TaskEditDialog extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        TaskViewModel vm = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        Task task = vm.selectedTask; 

        View view = LayoutInflater.from(getContext())
            .inflate(R.layout.fragment_task_editor, null);

        //Connecting all the views
        EditText title = view.findViewById(R.id.EditTitle);
        EditText description = view.findViewById(R.id.EditDescription);
        Spinner priority = view.findViewById(R.id.EditPriority);

        /**basic info
         * title
         * description
         * priority
        */
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
        /** scheduling
         * Deadline
         * closeOnMiss
         * 
         * minDuration
         * maxDuration
         * prefTimes
         * adaptive
        */
       
        //PrefSlots + adaptive
        LinearLayout prefSlotContainer = view.findViewById(R.id.PrefSlotContainer);
        List<TaskPrefSlot> prefSlots = new ArrayList<>(task.prefSlots);
        Collections.sort(prefSlots, (a, b) -> a.start.compareTo(b.start));
        Map<Integer, List<TaskPrefSlot>> slotMap = new HashMap<>();
        int repsPerDay = task.repsPerDay();
        int currentRep = 1;
        Set<DayOfWeek> days = new HashSet<>();
        while (currentRep <= repsPerDay) {
            while (!(days.size() >= 7)) {
            Iterator<TaskPrefSlot> it = prefSlots.iterator();
            while (it.hasNext()) {
                TaskPrefSlot prefSlot = it.next();
                if (Collections.disjoint(prefSlot.days, days)) {
                    days.addAll(prefSlot.days);
                    slotMap.computeIfAbsent(currentRep, k -> new ArrayList<>()).add(prefSlot);
                    it.remove(); 
                }
            }
            days.clear();
            currentRep++;
        }

        for (int i = 0; i < repsPerDay; i++) {
            TextView header = new TextView(requireContext());
            header.setText("Wiederholung " + (i+1));
            prefSlotContainer.addView(header);
            for (TaskPrefSlot prefSlot : slotMap.get(i+1)) {
                TextView slotView = new TextView(requireContext());
                String dayString = prefSlot.days.stream()
                    .sorted()
                    .map(d -> d.getDisplayName(TextStyle.SHORT, Locale.GERMAN))
                    .collect(Collectors.joining(" "));
                String timeString = prefSlot.start.format(DateTimeFormatter.ofPattern("HH:mm"));
                slotView.setText(dayString + "  " + timeString);
                prefSlotContainer.addView(slotView);
            }
        }

        /** Repetition (checkbox um rest anzueigen)
         * reps
         * perPeriod
         * periodUnit
         * cooldown
         */

        /** Progress (checkbox um rest anzueigen)
         * unit
         * target
         * current
         * 
         * (wenn repetition true)
         * minPerRep
         * maxPerRep
         */
        
        return new AlertDialog.Builder(requireContext())
            .setTitle("Task bearbeiten")
            .setView(view)
            .setPositiveButton("Speichern", (d, which) -> {
                task.core.title = this.title.getText().toString();
                task.core.description = this.description.getText().toString();
                task.core.priority = (Priority) priority.getSelectedItem();
                vm.saveEditedTask();
             })
            .setNegativeButton("Abbrechen", null)
            .create();
    }

}
