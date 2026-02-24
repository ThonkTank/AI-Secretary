package com.autosecretary.application.task.mapper;

import com.autosecretary.application.task.model.TaskListItem;
import com.autosecretary.database.task.Task;
import com.autosecretary.database.task.TaskRelation;
import com.autosecretary.database.task.TaskSlot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TaskListItemMapper {

    public List<TaskListItem> map(List<Task> tasks) {
        List<TaskListItem> items = new ArrayList<>();
        for (Task task : tasks) {
            int nrSlots = 0;
            for (TaskSlot slot : task.slots) {
                items.add(toItem(task, slot));
                nrSlots++;
            }
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
