package com.autosecretary.testing;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.budget.data.repository.BudgetRoomRepository;
import com.autosecretary.features.meal.data.repository.MealPantryRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRecipeRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRoomRepository;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.internal.budget.BookTaskCompletionExpenseUseCase;
import com.autosecretary.features.task.application.internal.completion.TaskCompletionEffects;
import com.autosecretary.features.task.application.internal.completion.TaskTransitionRecorder;
import com.autosecretary.features.task.application.internal.meal.TaskMealCompletionFromMealPlanner;
import com.autosecretary.features.task.application.internal.mutations.TaskSlotToggleMutation;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.shared.WidgetRefreshNotifier;

/**
 * Wires a fully-backed {@link CheckOffTaskUseCase} (two-phase check-off plus budget booking
 * and meal-completion side effects) against an in-memory {@link AppDatabase}, on a synchronous
 * executor. Shared by the task check-off characterization tests so the graph is built in one place.
 */
public final class TaskCheckOffFixture {
    private TaskCheckOffFixture() {
    }

    public static CheckOffTaskUseCase create(AppDatabase db) {
        TaskDao taskDao = db.taskDao();
        SynchronousExecutorService executor = new SynchronousExecutorService();
        TaskSlotToggleMutation mutation = new TaskSlotToggleMutation(
                taskDao,
                new TaskCompletionService(),
                new TaskLifecycleManager(),
                new TaskTransitionRecorder(taskDao, db.taskTransitionStatDao()),
                executor);
        BudgetRoomRepository budgetRepository = BudgetFixtures.budgetRepository(db);
        TaskCompletionEffects effects = new TaskCompletionEffects(
                new BookTaskCompletionExpenseUseCase(budgetRepository),
                new TaskMealCompletionFromMealPlanner(
                        new MealRoomRepository(db.mealPlanDao(), db.mealConsumptionLogDao(),
                                db.mealHouseholdMemberDao(), db.mealCookingPreferencesDao(),
                                db.mealWeeklyFoodTargetDao()),
                        new MealRecipeRoomRepository(db.mealRecipeDao(), db.mealIngredientDao()),
                        new MealPantryRoomRepository(db.mealPantryDao())),
                taskDao,
                new WidgetRefreshNotifier() {
                    @Override
                    public void refreshTaskWidgets() {
                    }

                    @Override
                    public void refreshBudgetWidgets() {
                    }
                });
        return new CheckOffTaskUseCase(mutation, executor, effects);
    }
}
