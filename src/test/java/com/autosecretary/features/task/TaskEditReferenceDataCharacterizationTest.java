package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Handler;
import android.os.Looper;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.budget.data.repository.BudgetRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRoomRepository;
import com.autosecretary.features.task.application.TaskDataService;
import com.autosecretary.features.task.application.edit.TaskEditReferenceDataUseCase;
import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.application.edit.TaskEditReferenceData;
import com.autosecretary.features.task.ui.edit.TaskEditViewModel;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.BudgetFixtures;
import com.autosecretary.testing.CallbackProbe;
import com.autosecretary.testing.RobolectricDrain;
import com.autosecretary.testing.SynchronousExecutorService;
import com.autosecretary.testing.TaskFixtures;
import com.autosecretary.testing.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;

public final class TaskEditReferenceDataCharacterizationTest extends AutoSecretaryRobolectricTest {
    private AppDatabase db;
    private TaskDao taskDao;
    private BudgetRoomRepository budgetRepository;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
        taskDao = db.taskDao();
        budgetRepository = BudgetFixtures.budgetRepository(db);
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void taskEditReferenceDataKeepsParentAndBudgetOptionInvariant() {
        LocalDate today = LocalDate.now();
        Task edited = TaskFixtures.taskWithSlot("Bearbeitet", today);
        Task parent = TaskFixtures.taskWithSlot("Elternaufgabe", today);
        taskDao.write(edited);
        taskDao.write(parent);
        budgetRepository.insertAccount(BudgetFixtures.account("Haushalt"));
        budgetRepository.insertCategory(BudgetFixtures.expenseCategory("Lebensmittel"));

        SynchronousExecutorService executor = new SynchronousExecutorService();
        TaskDataService taskDataService = new TaskDataService(
                taskDao,
                new TaskListItemMapper(),
                executor,
                new Handler(Looper.getMainLooper())::post,
                new MealRoomRepository(db.mealPlanDao(), db.mealConsumptionLogDao(),
                        db.mealHouseholdMemberDao(), db.mealCookingPreferencesDao(),
                        db.mealWeeklyFoodTargetDao()));
        TaskEditViewModel viewModel = new TaskEditViewModel(
                taskDataService,
                new TaskEditReferenceDataUseCase(taskDataService, budgetRepository),
                executor,
                executor);
        viewModel.beginEditTask(edited.core.id, () -> { });
        RobolectricDrain.mainLooper();

        CallbackProbe<TaskEditReferenceData> probe = new CallbackProbe<>();
        viewModel.loadReferenceData(probe.consumer());
        TaskEditReferenceData data = probe.value();

        assertEquals(1, data.parentTasks().size());
        assertEquals(parent.core.id, data.parentTasks().get(0).id());
        assertEquals("Elternaufgabe", data.parentTasks().get(0).label());
        assertEquals(1, data.budgetAccounts().size());
        assertEquals("Haushalt", data.budgetAccounts().get(0).label());
        assertEquals(1, data.budgetCategories().size());
        assertTrue(data.budgetCategories().get(0).label().contains("Lebensmittel"));
    }
}
