package com.autosecretary.features.task.application;

import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskCategory;
import com.autosecretary.features.task.domain.model.TaskCategoryWindow;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * In-memory, multi-level undo stack for assistant-applied changes. Each successful
 * {@link ApplyTaskChangesUseCase} pushes exactly one {@link Snapshot} (one assistant apply = one
 * atomic undo unit); {@link UndoTaskChangesUseCase} pops and restores the top. Capped to the most
 * recent {@link #MAX_DEPTH} applies.
 *
 * <p><b>Process-volatile:</b> the stack lives only in memory and is cleared on process death or
 * data reload — there is no persisted redo/undo history.
 */
public class TaskChangeUndoHolder {
    private static final int MAX_DEPTH = 20;

    /**
     * The prior state needed to reverse one apply: full prior rows of every updated/deleted task
     * (plus tasks whose category was deleted, so their cleared {@code categoryId} is restored) and
     * category, the ids the apply created (to delete on undo), and the prior rows of every
     * updated/deleted or category-cascade-deleted window (to re-insert/revert on undo) alongside the
     * window ids the apply created (to delete on undo).
     */
    public record Snapshot(List<Task> priorTasks,
                           List<String> createdTaskIds,
                           List<TaskCategory> priorCategories,
                           List<String> createdCategoryIds,
                           List<TaskCategoryWindow> priorWindows,
                           List<String> createdWindowIds) {}

    private final Deque<Snapshot> stack = new ArrayDeque<>();

    public synchronized void push(Snapshot snapshot) {
        stack.push(snapshot);
        while (stack.size() > MAX_DEPTH) {
            stack.removeLast();
        }
    }

    /** Removes and returns the most recent snapshot, or {@code null} if the stack is empty. */
    public synchronized Snapshot pop() {
        return stack.poll();
    }

    public synchronized boolean isEmpty() {
        return stack.isEmpty();
    }

    public synchronized int size() {
        return stack.size();
    }

    public synchronized void clear() {
        stack.clear();
    }
}
