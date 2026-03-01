package com.autosecretary.features.task.application;

import android.content.Context;

import com.autosecretary.features.budget.ui.widget.BudgetWidgetProvider;
import com.autosecretary.features.meal.application.TaskMealIntegrationService;
import com.autosecretary.features.task.application.internal.mutations.TaskSlotToggleMutation;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.application.internal.budget.BookTaskCompletionExpenseUseCase;
import com.autosecretary.features.task.data.TaskDao;

import java.time.LocalDate;
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
    private final TaskDao taskDao;
    private final ExecutorService workerExecutor;
    private final BookTaskCompletionExpenseUseCase bookTaskCompletionExpenseUseCase;
    private final Context appContext;
    private final TaskMealIntegrationService taskMealIntegrationService;

    public CheckOffTaskUseCase(TaskSlotToggleMutation mutation,
                               TaskDao taskDao,
                               ExecutorService workerExecutor,
                               BookTaskCompletionExpenseUseCase bookTaskCompletionExpenseUseCase,
                               Context appContext,
                               TaskMealIntegrationService taskMealIntegrationService) {
        this.mutation = mutation;
        this.taskDao = taskDao;
        this.workerExecutor = workerExecutor;
        this.bookTaskCompletionExpenseUseCase = bookTaskCompletionExpenseUseCase;
        this.appContext = appContext;
        this.taskMealIntegrationService = taskMealIntegrationService;
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
                    boolean booked = bookTaskCompletionExpenseUseCase.execute(task, LocalDate.now());
                    if (booked) {
                        BudgetWidgetProvider.notifyWidgetUpdate(appContext);
                    }
                    // actualServingsOverride=0: use the serving size defined on the task, not a manual override.
                    boolean mealUpdated = taskMealIntegrationService.completeMealTask(task, LocalDate.now(), 0);
                    if (mealUpdated) {
                        taskDao.write(task);
                    }
                }
        ));
    }
}
