package com.autosecretary.features.task.application.internal.completion;

import com.autosecretary.app.WidgetRefreshNotifier;
import com.autosecretary.features.meal.application.TaskMealIntegrationService;
import com.autosecretary.features.task.application.internal.budget.BookTaskCompletionExpenseUseCase;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDao;

import java.time.LocalDate;

/**
 * Shared post-completion side effects for task completion paths.
 */
public final class TaskCompletionEffects {
    private final BookTaskCompletionExpenseUseCase bookTaskCompletionExpenseUseCase;
    private final TaskMealIntegrationService taskMealIntegrationService;
    private final TaskDao taskDao;
    private final WidgetRefreshNotifier widgetRefreshNotifier;

    public TaskCompletionEffects(BookTaskCompletionExpenseUseCase bookTaskCompletionExpenseUseCase,
                                 TaskMealIntegrationService taskMealIntegrationService,
                                 TaskDao taskDao,
                                 WidgetRefreshNotifier widgetRefreshNotifier) {
        this.bookTaskCompletionExpenseUseCase = bookTaskCompletionExpenseUseCase;
        this.taskMealIntegrationService = taskMealIntegrationService;
        this.taskDao = taskDao;
        this.widgetRefreshNotifier = widgetRefreshNotifier;
    }

    public void apply(Task task, LocalDate completionDay) {
        LocalDate effectiveCompletionDay = completionDay != null ? completionDay : LocalDate.now();
        boolean booked = bookTaskCompletionExpenseUseCase.execute(task, effectiveCompletionDay);
        if (booked) {
            widgetRefreshNotifier.refreshBudgetWidgets();
        }

        // actualServingsOverride=0: use the serving size defined on the task.
        boolean mealUpdated = taskMealIntegrationService.completeMealTask(task, effectiveCompletionDay, 0);
        if (mealUpdated) {
            taskDao.write(task);
        }
    }
}
