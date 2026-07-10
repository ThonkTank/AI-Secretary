package com.autosecretary.features.task.application.internal.completion;

import com.autosecretary.shared.WidgetRefreshNotifier;
import com.autosecretary.features.task.application.internal.budget.BookTaskCompletionExpenseUseCase;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.data.TaskPlannedMeal;
import com.autosecretary.features.task.domain.TaskMealCompletionRequest;
import com.autosecretary.features.task.domain.TaskMealCompletionService;

import java.time.LocalDate;

/**
 * Shared post-completion side effects for task completion paths.
 */
public final class TaskCompletionEffects {
    private final BookTaskCompletionExpenseUseCase bookTaskCompletionExpenseUseCase;
    private final TaskMealCompletionService taskMealCompletionService;
    private final TaskDao taskDao;
    private final WidgetRefreshNotifier widgetRefreshNotifier;

    public TaskCompletionEffects(BookTaskCompletionExpenseUseCase bookTaskCompletionExpenseUseCase,
                                 TaskMealCompletionService taskMealCompletionService,
                                 TaskDao taskDao,
                                 WidgetRefreshNotifier widgetRefreshNotifier) {
        this.bookTaskCompletionExpenseUseCase = bookTaskCompletionExpenseUseCase;
        this.taskMealCompletionService = taskMealCompletionService;
        this.taskDao = taskDao;
        this.widgetRefreshNotifier = widgetRefreshNotifier;
    }

    public void apply(Task task, LocalDate completionDay) {
        LocalDate effectiveCompletionDay = completionDay != null ? completionDay : LocalDate.now();
        boolean booked = bookTaskCompletionExpenseUseCase.execute(task, effectiveCompletionDay);
        if (booked) {
            widgetRefreshNotifier.refreshBudgetWidgets();
        }

        if (completePlannedMeal(task, effectiveCompletionDay)) {
            taskDao.write(task);
        }
    }

    private boolean completePlannedMeal(Task task, LocalDate completionDay) {
        if (task == null || task.core == null || task.core.mealType == null || completionDay == null) {
            return false;
        }
        TaskPlannedMeal plannedMeal = task.getPlannedMealForDate(completionDay);
        if (plannedMeal == null || plannedMeal.completed) {
            return false;
        }

        int servings = plannedMeal.plannedServings;
        if (!task.completePlannedMeal(completionDay, servings)) {
            return false;
        }

        taskMealCompletionService.completeMeal(new TaskMealCompletionRequest(
                task.core.mealType,
                plannedMeal.recipeId,
                completionDay,
                plannedMeal.plannedServings,
                servings));
        return true;
    }
}
