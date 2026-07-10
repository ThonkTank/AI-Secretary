package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.autosecretary.shared.WidgetRefreshNotifier;
import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.budget.data.repository.BudgetRoomRepository;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.internal.budget.BookTaskCompletionExpenseUseCase;
import com.autosecretary.features.task.application.internal.completion.TaskCompletionEffects;
import com.autosecretary.features.task.application.internal.completion.TaskTransitionRecorder;
import com.autosecretary.features.task.application.internal.mutations.TaskSlotToggleMutation;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.application.internal.meal.TaskMealCompletionFromMealPlanner;
import com.autosecretary.features.meal.data.repository.MealPantryRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRecipeRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRoomRepository;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.BudgetFixtures;
import com.autosecretary.testing.CallbackProbe;
import com.autosecretary.testing.SynchronousExecutorService;
import com.autosecretary.testing.TaskFixtures;
import com.autosecretary.testing.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class TaskCompletionCharacterizationTest extends AutoSecretaryRobolectricTest {
    private AppDatabase db;
    private TaskDao taskDao;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
        taskDao = db.taskDao();
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void twoPhaseCompletionKeepsStreakHistoryTimingAndAdaptivePrefSlotInvariant() {
        LocalDate today = LocalDate.now();
        Task task = TaskFixtures.taskWithSlot("Adaptive Task", today);
        task.core.adaptive = true;
        task.core.progress.totalTime = 10;
        task.core.progress.totalProgress = 1;
        TaskPrefSlot prefSlot = TaskFixtures.prefSlot(task, today, LocalTime.of(10, 0));
        taskDao.write(task);

        CheckOffTaskUseCase useCase = createCheckOffUseCase();
        TaskListItem item = currentItem(task.core.id);

        CallbackProbe<Void> startProbe = new CallbackProbe<>();
        useCase.execute(item, startProbe.runnable());
        startProbe.assertCalled();

        Task afterStart = taskDao.read(task.core.id);
        TaskSlot startedSlot = afterStart.findSlot(item.slotId);
        assertNotNull(startedSlot.realStart);
        assertEquals(false, startedSlot.completed);
        assertEquals(0, afterStart.core.history.completions);

        startedSlot.realStart = LocalTime.now().minusMinutes(10);
        taskDao.writeSlot(startedSlot);

        CallbackProbe<Void> completeProbe = new CallbackProbe<>();
        useCase.execute(currentItem(task.core.id), completeProbe.runnable());
        completeProbe.assertCalled();

        Task completed = taskDao.read(task.core.id);
        TaskSlot completedSlot = completed.findSlot(item.slotId);
        assertTrue(completedSlot.completed);
        assertNotNull(completedSlot.realEnd);
        assertEquals(1, completed.core.history.completions);
        assertEquals(1, completed.core.history.trackedCompletions);
        assertEquals(1, completed.core.history.currentStreak);
        assertTrue(completed.core.history.totalDuration >= 10);
        assertTrue(completed.core.progress.totalProgress > 1);

        TaskPrefSlot adapted = completed.prefSlots.stream()
                .filter(slot -> slot.id.equals(prefSlot.id))
                .findFirst()
                .orElseThrow();
        assertNotEquals(LocalTime.of(10, 0), adapted.start);
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
                new TaskMealCompletionFromMealPlanner(
                        new MealRoomRepository(db.mealPlanDao(), db.mealConsumptionLogDao(),
                                db.mealHouseholdMemberDao(), db.mealCookingPreferencesDao(),
                                db.mealWeeklyFoodTargetDao()),
                        new MealRecipeRoomRepository(db.mealRecipeDao(), db.mealIngredientDao()),
                        new MealPantryRoomRepository(db.mealPantryDao())),
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
