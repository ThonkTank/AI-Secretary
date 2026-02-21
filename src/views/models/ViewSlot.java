package views.models;

import androidx.room.Embedded;
import androidx.room.Relation;
import androidx.room.Ignore;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import database.task.*;

public class ViewSlot {
    public String title;
    public int indent;
    public boolean completed;
    public Long taskId;
    public List<ViewSlots> children;

    // Nur für scheduling relevant
    public LocalTime start;
    public LocalTime end;
    public Long slotId;

    public static List<ViewSlot> buildViewSlots(List<Task> tasks, LocalDateTime start, LocalDateTime end, int indent) {
        //Day entscheided, ob die row für Schedule oder Manager ist. 
        // Wenn start/end == null, Manager, 1 row pro Task. 
        // Wenn start/end != null, Schedule, 1 Row pro slot wenn slot.day = LocalDate.now()
        // Für Schedule ist die Liste bereits über SQL getTaskByDue vorgefiltert sodass sie nur Tasks enthällt die heute fällig sind.
        tasks = Task.buildTree(tasks);
        List<ViewSlot> viewSlots = new ArrayList<>();
        for (Task task : tasks) {
            if (start == null) {
                ViewSlot viewSlot = new ViewSlot();
                viewSlot.title = task.core.title;
                viewSlot.indent = indent;
                viewSlot.completed = task.isCompleted;
                viewSlot.taskId = task.core.id;
                viewSlots.add(slot);
            } else {
                for (TaskSlot slot : task.slots) {
                    if (slot.start.isAfter(start) && slot.end.isBefore(end)) {
                        ViewSlot viewSlot = new ViewSlot();
                        viewSlot.title = task.core.title;
                        viewSlot.indent = indent;
                        viewSlot.completed = task.isCompleted;
                        viewSlot.taskId = task.core.id;
                        viewSlot.start = slot.start;
                        viewSlot.end = slot.end;
                        viewSlot.slotId = slot.id;
                        viewSlots.add(slot);
                    }
                }
            }
            viewSlots.addAll(buildViewSlots(task.children, start, end, indent+1));
        }
        return viewSlots;
    }

    public static List<ViewSlot> fromTask(List<Task> tasks) {
        List<ViewSlot> viewSlots = new ArrayList<>();
        return viewSlots;
    }

    public static List<ViewSlot> fromSlot(List<TaskSlot> slots) {
        List<ViewSlot> viewSlots = new ArrayList<>();
        return viewSlots;
    }
}