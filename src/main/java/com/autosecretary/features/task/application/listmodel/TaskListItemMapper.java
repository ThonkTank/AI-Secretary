package com.autosecretary.features.task.application.listmodel;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskSlot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TaskListItemMapper {

    private static final int MIN_PROGRESS_STEP = 1;

    public List<TaskListItem> map(List<Task> tasks) {
        List<TaskListItem> items = new ArrayList<>();
        for (Task task : tasks) {
            if (task.slots.isEmpty()) {
                items.add(toUnscheduledItem(task));
            } else {
                for (TaskSlot slot : task.slots) {
                    items.add(toItem(task, slot));
                }
            }
        }
        return items;
    }

    private static List<String> extractParentIds(Task task) {
        return task.parents.stream()
                .map(rel -> rel.parent)
                .toList();
    }

    private TaskListItem toItem(Task task, TaskSlot slot) {
        List<String> parentTaskIds = extractParentIds(task);
        boolean hasProgress = task.core.progress != null;

        return new TaskListItem(
                TaskListItem.ItemType.TASK,
                task.core.id,
                slot != null ? slot.id : null,
                slot != null ? slot.parent : null,
                parentTaskIds,
                task.core.title,
                task.core.description,
                slot != null ? slot.day : LocalDate.now(),
                slot != null ? slot.start : null,
                slot != null ? slot.end : null,
                task.core.deadline,
                task.core.history.currentStreak,
                slot != null ? slot.score : 0,
                slot != null && slot.completed,
                slot != null && slot.realStart != null && !slot.completed,
                hasProgress ? task.core.progress.current : 0,
                hasProgress ? task.core.progress.target : 0,
                hasProgress ? task.core.progress.unit : null,
                hasProgress ? Math.max(MIN_PROGRESS_STEP, task.core.progress.minPerRep) : MIN_PROGRESS_STEP,
                task.core.goalIcon,
                task.core.goalColorHex
        );
    }

    private TaskListItem toUnscheduledItem(Task task) {
        return toItem(task, null);
    }
}
