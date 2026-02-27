package com.autosecretary.features.task.application;

import android.content.Context;

import com.autosecretary.features.budget.ui.widget.BudgetWidgetProvider;
import com.autosecretary.features.meal.application.TaskMealIntegrationService;
import com.autosecretary.features.task.application.internal.mutations.TaskSlotToggleMutation;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.application.internal.budget.BookTaskCompletionExpenseUseCase;
import com.autosecretary.features.task.data.TaskDAO;

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
    private final TaskDAO taskDao;
    private final ExecutorService executor;
    private final BookTaskCompletionExpenseUseCase bookTaskCompletionExpenseUseCase;
    private final Context appContext;
    private final TaskMealIntegrationService taskMealIntegrationService;

    public CheckOffTaskUseCase(TaskSlotToggleMutation mutation,
                               TaskDAO taskDao,
                               ExecutorService executor,
                               BookTaskCompletionExpenseUseCase bookTaskCompletionExpenseUseCase,
                               Context appContext,
                               TaskMealIntegrationService taskMealIntegrationService) {
        this.mutation = mutation;
        this.taskDao = taskDao;
        this.executor = executor;
        this.bookTaskCompletionExpenseUseCase = bookTaskCompletionExpenseUseCase;
        this.appContext = appContext;
        this.taskMealIntegrationService = taskMealIntegrationService;
    }

    public void execute(TaskListItem listItem, Runnable onChanged) {
        executor.execute(() -> mutation.execute(
                listItem.taskId,
                listItem.slotId,
                onChanged,
                task -> {
                    boolean booked = bookTaskCompletionExpenseUseCase.execute(task, LocalDate.now());
                    if (booked) {
                        BudgetWidgetProvider.notifyWidgetUpdate(appContext);
                    }
                    boolean mealUpdated = taskMealIntegrationService.completeMealTask(task, LocalDate.now(), 0);
                    if (mealUpdated) {
                        taskDao.write(task);
                    }
                }
        ));
    }
}
