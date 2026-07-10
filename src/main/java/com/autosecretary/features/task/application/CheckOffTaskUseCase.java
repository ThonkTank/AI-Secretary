package com.autosecretary.features.task.application;

import com.autosecretary.features.task.application.internal.completion.TaskCompletionEffects;
import com.autosecretary.features.task.application.internal.mutations.TaskSlotToggleMutation;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.domain.model.TaskSlot;
import java.util.concurrent.ExecutorService;

/**
 * Orchestrates task completion: delegates the two-phase check-off logic to
 * {@link TaskSlotToggleMutation} and handles post-completion side effects
 * (budget booking, meal integration).
 *
 * Contract: mutation runs on {@code executor}; callbacks are dispatched by the mutation.
 */
public class CheckOffTaskUseCase {
    private final TaskSlotToggleMutation mutation;
    private final ExecutorService workerExecutor;
    private final TaskCompletionEffects completionEffects;

    public CheckOffTaskUseCase(TaskSlotToggleMutation mutation,
                               ExecutorService workerExecutor,
                               TaskCompletionEffects completionEffects) {
        this.mutation = mutation;
        this.workerExecutor = workerExecutor;
        this.completionEffects = completionEffects;
    }

    /**
     * Drives the two-phase slot check-off for the given list item.
     *
     * <p>First call: transitions the slot to STARTED (records {@code realStart}).
     * Second call: transitions to COMPLETED (records {@code realEnd}), then runs
     * post-completion side effects — budget expense booking and meal integration.
     *
     * @param listItem  the task list item containing the task and slot IDs to toggle
     * @param onChanged callback dispatched on the main thread after all writes succeed
     */
    public void execute(TaskListItem listItem, Runnable onChanged) {
        workerExecutor.execute(() -> mutation.execute(
                listItem.taskId,
                listItem.slotId,
                onChanged,
                task -> {
                    TaskSlot slot = task.findSlot(listItem.slotId);
                    completionEffects.apply(task, slot != null ? slot.day : null);
                }
        ));
    }
}
