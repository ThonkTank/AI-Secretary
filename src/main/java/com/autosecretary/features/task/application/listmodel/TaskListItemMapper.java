package com.autosecretary.features.task.application.listmodel;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskSlot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TaskListItemMapper {

    public List<TaskListItem> map(List<Task> tasks) {
        List<TaskListItem> items = new ArrayList<>();
        for (Task task : tasks) {
            if (task.slots.isEmpty()) {
                TaskSlot placeholder = new TaskSlot();
                placeholder.id = null;
                placeholder.day = LocalDate.now();
                items.add(toItem(task, placeholder));
            } else {
                for (TaskSlot slot : task.slots) {
                    items.add(toItem(task, slot));
                }
            }
        }
        return items;
    }

    private TaskListItem toItem(Task task, TaskSlot slot) {
        List<String> parentTaskIds = task.parents.stream()
                .map(rel -> rel.parent)
                .toList();

        return new TaskListItem(
                TaskListItem.ItemType.TASK,
                task.core.id,
                slot.id,
                slot.parent,
                parentTaskIds,
                task.core.title,
                task.core.description,
                slot.day,
                slot.start,
                slot.end,
                task.core.deadline,
                task.core.history.currentStreak,
                slot.score,
                slot.completed,
                slot.realStart != null && !slot.completed,
                task.core.progress.current,
                task.core.progress.target,
                task.core.progress.unit,
                Math.max(1, task.core.progress.minPerRep),
                task.core.progress.target > 0,
                task.core.goalIcon,
                task.core.goalColorHex
        );
    }
}
