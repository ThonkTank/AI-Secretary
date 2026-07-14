package com.autosecretary.features.task.application.listmodel;

import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskCategory;
import com.autosecretary.features.task.domain.model.TaskSlot;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transforms domain Task objects into flat TaskListItem presentation models.
 *
 * <p>This mapper decouples the UI layer from domain changes by producing immutable,
 * list-rendering-optimized DTOs. It handles both scheduled tasks (with slots) and
 * unscheduled tasks by producing a flattened list of items, one per slot or one per
 * unscheduled task.
 *
 * <p><strong>Transformation Logic:</strong>
 * <ul>
 *   <li>For each Task, if it has no slots, a single TaskListItem is created with scheduling
 *       fields nulled.
 *   <li>For each Task, if it has slots, one TaskListItem is created per slot, with all
 *       fields populated from the slot and task metadata.
 *   <li>Progress fields are populated only if the task has a non-null progress tracker.
 *   <li>MinPerRep is clamped to at least MIN_PROGRESS_STEP to ensure the UI can always
 *       render a meaningful progress increment.
 * </ul>
 *
 * <p>This mapper is called by the task list UI layer to prepare data for RecyclerView rendering.
 *
 * @see TaskListItem for field semantics and null handling.
 */
public class TaskListItemMapper {

    /**
     * Minimum progress step size for UI rendering.
     *
     * <p>When a task has a progress target, the task's minPerRep value is clamped to at least
     * this value. This ensures the UI can always render a sensible progress increment button
     * or visual indicator, preventing zero or negative step values that would be unusable.
     */
    private static final int MIN_PROGRESS_STEP = 1;

    /**
     * Transforms a list of domain tasks into a flat list of presentation items.
     *
     * <p>For each task, generates:
     * <ul>
     *   <li>One TaskListItem with scheduling fields nulled if the task has no slots (unscheduled).
     *   <li>One TaskListItem per slot if the task has slots (scheduled).
     * </ul>
     *
     * <p>The resulting list is flat and ordered by task, then by slot. It is suitable for
     * direct rendering in a list adapter.
     *
     * @param tasks the domain task list to transform
     * @return a flat list of TaskListItem presentation models (never null, may be empty)
     */
    /** Overload for callers without category metadata (produces items with empty category fields). */
    public List<TaskListItem> map(List<Task> tasks) {
        return map(tasks, Collections.emptyList());
    }

    public List<TaskListItem> map(List<Task> tasks, List<TaskCategory> categories) {
        Map<String, TaskCategory> categoriesById = new HashMap<>();
        for (TaskCategory category : categories) {
            categoriesById.put(category.id, category);
        }
        List<TaskListItem> items = new ArrayList<>();
        for (Task task : tasks) {
            if (task.slots.isEmpty()) {
                items.add(toItem(task, null, categoriesById));
            } else {
                for (TaskSlot slot : task.slots) {
                    items.add(toItem(task, slot, categoriesById));
                }
            }
        }
        return items;
    }

    /**
     * Converts a single task (with optional slot) into a TaskListItem.
     *
     * <p>Handles both scheduled tasks (slot != null) and unscheduled tasks (slot == null).
     * When slot is null, scheduling fields are null and score defaults to 0.
     *
     * <p>Progress fields are populated only if the task has a non-null progress tracker. The
     * minPerRep value is clamped to MIN_PROGRESS_STEP to ensure the UI can always render a
     * meaningful progress step.
     *
     * @param task the domain task
     * @param slot the slot for this occurrence, or null for unscheduled tasks
     * @return a TaskListItem with all fields populated appropriately
     */
    private TaskListItem toItem(Task task, TaskSlot slot, Map<String, TaskCategory> categoriesById) {
        boolean hasProgress = task.core.progress != null;
        boolean completed   = slot != null && slot.completed;
        boolean inProgress  = slot != null && slot.realStart != null && !completed;
        String  slotId       = slot != null ? slot.id     : null;
        String  slotParentId = slot != null ? slot.parent : null;
        LocalDate day        = slot != null ? slot.day    : LocalDate.now();
        LocalTime start      = slot != null ? slot.start  : null;
        LocalTime end        = slot != null ? slot.end    : null;
        int       score      = slot != null ? slot.score  : 0;

        TaskCategory category = task.core.categoryId != null ? categoriesById.get(task.core.categoryId) : null;

        return new TaskListItem(
                TaskListItem.ItemType.TASK,
                task.core.id,
                slotId,
                slotParentId,
                task.core.categoryId,
                category != null ? category.name : null,
                category != null ? category.icon : null,
                category != null ? category.colorHex : null,
                task.core.title,
                task.core.description,
                day,
                start,
                end,
                task.core.deadline,
                task.core.history.currentStreak,
                score,
                task.core.priority.scoringWeight,
                completed,
                inProgress,
                task.core.leisure,
                hasProgress ? task.core.progress.current : 0,
                hasProgress ? task.core.progress.target : 0,
                hasProgress ? task.core.progress.unit : null,
                hasProgress ? Math.max(MIN_PROGRESS_STEP, task.core.progress.minPerRep) : MIN_PROGRESS_STEP,
                task.core.goalIcon,
                task.core.goalColorHex
        );
    }

}
