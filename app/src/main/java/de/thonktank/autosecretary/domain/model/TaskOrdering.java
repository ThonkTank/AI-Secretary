package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Stable global ordering changed only by explicit create, move and defer commands. */
public final class TaskOrdering {
    private static final long STEP = 1_024L;

    public List<Task> insertAtEndOfSlot(List<Task> current, Task inserted) {
        List<Task> ordered = sorted(current);
        int insertion = -1;
        for (int i = 0; i < ordered.size(); i++)
            if (ordered.get(i).slot == inserted.slot) insertion = i + 1;
        if (insertion < 0) {
            insertion = 0;
            while (insertion < ordered.size() && ordered.get(insertion).slot.rank < inserted.slot.rank)
                insertion++;
        }
        ordered.add(insertion, inserted);
        return resequence(ordered);
    }

    public List<Task> moveToEndOfSlot(List<Task> current, TaskId taskId, TaskSlot slot, String title) {
        List<Task> remaining = new ArrayList<>();
        Task moving = null;
        for (Task task : current) {
            if (task.id.equals(taskId)) moving = task.edit(title, slot, 0);
            else remaining.add(task);
        }
        return moving == null ? sorted(current) : insertAtEndOfSlot(remaining, moving);
    }

    public List<Task> swap(List<Task> current, TaskId firstId, TaskId secondId) {
        List<Task> ordered = sorted(current);
        int first = indexOf(ordered, firstId);
        int second = indexOf(ordered, secondId);
        if (first < 0 || second < 0) return ordered;
        Task value = ordered.get(first);
        ordered.set(first, ordered.get(second));
        ordered.set(second, value);
        return resequence(ordered);
    }

    public List<Task> sorted(List<Task> tasks) {
        List<Task> result = new ArrayList<>(tasks);
        result.sort(Comparator.comparingLong(task -> task.displayOrder));
        return result;
    }

    private List<Task> resequence(List<Task> ordered) {
        List<Task> result = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++)
            result.add(ordered.get(i).withDisplayOrder((i + 1L) * STEP));
        return result;
    }

    private static int indexOf(List<Task> tasks, TaskId id) {
        for (int i = 0; i < tasks.size(); i++) if (tasks.get(i).id.equals(id)) return i;
        return -1;
    }
}
