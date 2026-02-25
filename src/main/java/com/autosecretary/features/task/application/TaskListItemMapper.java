package com.autosecretary.features.task.application;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskRelation;
import com.autosecretary.features.task.data.TaskSlot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps {@link Task} + {@link TaskSlot} pairs into flat {@link TaskListItem} instances
 * for the presentation layer.
 */
public class TaskListItemMapper {

    public List<TaskListItem> map(List<Task> tasks) {
        List<TaskListItem> items = new ArrayList<>();
        for (Task task : tasks) {
            int nrSlots = 0;
            for (TaskSlot slot : task.slots) {
                items.add(toItem(task, slot));
                nrSlots++;
            }
            // Placeholder item for tasks without slots so they still appear in Manage mode
            if (nrSlots == 0) {
                TaskSlot placeholder = new TaskSlot();
                placeholder.day = LocalDate.now();
                items.add(toItem(task, placeholder));
            }
        }
        return items;
    }

    private TaskListItem toItem(Task task, TaskSlot slot) {
        List<String> parentTaskIds = new ArrayList<>();
        for (TaskRelation rel : task.parents) {
            parentTaskIds.add(rel.parent);
        }

        return new TaskListItem(
                task.core.id,
                slot.id,
                slot.parent,
                parentTaskIds,
                task.core.title,
                task.core.type,
                slot.day,
                slot.start,
                slot.end,
                task.core.deadline,
                task.core.history.currentStreak,
                slot.score,
                slot.completed,
                slot.realStart != null && !slot.completed
        );
    }
}
