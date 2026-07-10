package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.autosecretary.app.WidgetRefreshNotifier;
import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.budget.data.repository.BudgetRoomRepository;
import com.autosecretary.features.meal.application.TaskMealIntegrationService;
import com.autosecretary.features.meal.data.repository.MealPantryRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRecipeRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRoomRepository;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealType;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.internal.budget.BookTaskCompletionExpenseUseCase;
import com.autosecretary.features.task.application.internal.completion.TaskCompletionEffects;
import com.autosecretary.features.task.application.internal.completion.TaskTransitionRecorder;
import com.autosecretary.features.task.application.internal.mutations.TaskSlotToggleMutation;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.BudgetFixtures;
import com.autosecretary.testing.CallbackProbe;
import com.autosecretary.testing.MealFixtures;
import com.autosecretary.testing.SynchronousExecutorService;
import com.autosecretary.testing.TaskFixtures;
import com.autosecretary.testing.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class TaskMealCompletionCharacterizationTest extends AutoSecretaryRobolectricTest {
    private AppDatabase db;
    private TaskDao taskDao;
    private MealRoomRepository mealRepository;
    private MealRecipeRoomRepository recipeRepository;
    private MealPantryRoomRepository pantryRepository;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
        taskDao = db.taskDao();
        mealRepository = new MealRoomRepository(db.mealPlanDao(), db.mealConsumptionLogDao(),
                db.mealHouseholdMemberDao(), db.mealCookingPreferencesDao(), db.mealWeeklyFoodTargetDao());
        recipeRepository = new MealRecipeRoomRepository(db.mealRecipeDao(), db.mealIngredientDao());
        pantryRepository = new MealPantryRoomRepository(db.mealPantryDao());
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void taskMealCompletionKeepsConsumptionPantryMealPlanAndPlannedMealInvariant() {
        LocalDate today = LocalDate.now();
        String ingredientId = "ingredient-rice";
        Recipe recipe = MealFixtures.recipe("Reis Bowl", ingredientId);
        recipeRepository.saveRecipe(recipe);
        PantryItem pantryItem = MealFixtures.pantryItem(ingredientId, 250.0);
        pantryRepository.savePantryItem(pantryItem);
        MealPlan mealPlan = MealFixtures.mealPlan(today, MealType.LUNCH, recipe, 2);
        mealRepository.saveMealPlan(mealPlan);

        Task task = TaskFixtures.taskWithSlot("Mittagessen", today);
        task.core.mealType = MealType.LUNCH;
        TaskFixtures.plannedMeal(task, today, recipe.id, 2);
        taskDao.write(task);

        CheckOffTaskUseCase useCase = createCheckOffUseCase();
        TaskListItem item = currentItem(task.core.id);
        useCase.execute(item, new CallbackProbe<Void>().runnable());

        TaskSlot startedSlot = taskDao.read(task.core.id).findSlot(item.slotId);
        startedSlot.realStart = LocalTime.now().minusMinutes(10);
        taskDao.writeSlot(startedSlot);

        CallbackProbe<Void> completeProbe = new CallbackProbe<>();
        useCase.execute(currentItem(task.core.id), completeProbe.runnable());
        completeProbe.assertCalled();

        Task completedTask = taskDao.read(task.core.id);
        assertTrue(completedTask.findSlot(item.slotId).completed);
        assertTrue(completedTask.getPlannedMealForDate(today).completed);
        assertEquals(2, completedTask.getPlannedMealForDate(today).actualServings);

        assertEquals(1, mealRepository.getConsumptionLogs(today, today).size());
        assertEquals(600, mealRepository.getConsumptionLogs(today, today).get(0).calories);

        List<PantryItem> pantryItems = pantryRepository.getPantryItems();
        assertEquals(1, pantryItems.size());
        assertEquals(150.0, pantryItems.get(0).amount, 0.001);

        MealPlan completedPlan = mealRepository.getMealPlans(today, today).get(0);
        assertTrue(completedPlan.isCompleted);
        assertEquals(2, completedPlan.actualServings);
        assertNotNull(completedPlan.completedAt);
    }

    private CheckOffTaskUseCase createCheckOffUseCase() {
        SynchronousExecutorService executor = new SynchronousExecutorService();
        TaskLifecycleManager lifecycleManager = new TaskLifecycleManager();
        TaskSlotToggleMutation mutation = new TaskSlotToggleMutation(
                taskDao,
                new TaskCompletionService(),
                lifecycleManager,
                new TaskTransitionRecorder(taskDao, db.taskTransitionStatDao()),
                executor);
        BudgetRoomRepository budgetRepository = BudgetFixtures.budgetRepository(db);
        TaskCompletionEffects effects = new TaskCompletionEffects(
                new BookTaskCompletionExpenseUseCase(budgetRepository),
                new TaskMealIntegrationService(mealRepository, recipeRepository, pantryRepository),
                taskDao,
                new NoOpWidgetRefreshNotifier());
        return new CheckOffTaskUseCase(mutation, executor, effects);
    }

    private TaskListItem currentItem(String taskId) {
        return new TaskListItemMapper().map(List.of(taskDao.read(taskId))).get(0);
    }

    private static final class NoOpWidgetRefreshNotifier implements WidgetRefreshNotifier {
        @Override
        public void refreshTaskWidgets() {
        }

        @Override
        public void refreshBudgetWidgets() {
        }
    }
}
