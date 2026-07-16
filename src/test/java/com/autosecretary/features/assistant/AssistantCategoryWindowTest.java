package com.autosecretary.features.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.assistant.application.AssistantProposals.PendingProposal;
import com.autosecretary.features.assistant.application.ConfirmAssistantProposalUseCase;
import com.autosecretary.features.assistant.application.internal.AssistantMealGateway;
import com.autosecretary.features.assistant.application.internal.AssistantTool;
import com.autosecretary.features.assistant.application.internal.AssistantTransactionImportExecutor;
import com.autosecretary.features.assistant.application.internal.DbCalls;
import com.autosecretary.features.assistant.application.internal.TaskTools;
import com.autosecretary.features.meal.data.repository.MealRecipeRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRoomRepository;
import com.autosecretary.features.task.application.ApplyTaskChangesUseCase;
import com.autosecretary.features.task.application.ScheduleReplanCoordinator;
import com.autosecretary.features.task.application.TaskChangeUndoHolder;
import com.autosecretary.features.task.application.UndoTaskChangesUseCase;
import com.autosecretary.features.task.application.config.TaskCategoryWindowRepository;
import com.autosecretary.features.task.data.TaskCategoryWindowDao;
import com.autosecretary.features.task.domain.model.TaskCategory;
import com.autosecretary.features.task.domain.model.TaskCategoryWindow;
import com.autosecretary.features.task.domain.scheduling.CategoryWindowProvider.CategoryWindow;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.BudgetFixtures;
import com.autosecretary.testing.ReplanCoordinators;
import com.autosecretary.testing.SynchronousExecutorService;
import com.autosecretary.testing.TestDatabases;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * End-to-end protection for the assistant's reserved category-window tools. Invariants:
 * {@code get_category_windows} exposes each window's id and fields; {@code propose_category_window_changes}
 * only parks a proposal (no DB write) and validates its input; confirming a proposal applies it so the
 * window is both persisted and visible to the scheduler ({@code windowsForDay}, i.e. the shared cache was
 * invalidated); and undo reverses create/move/delete precisely.
 */
public final class AssistantCategoryWindowTest extends AutoSecretaryRobolectricTest {

    private static final LocalDate A_MONDAY =
            LocalDate.of(2026, 1, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

    private AppDatabase db;
    private TaskCategoryWindowDao windowDao;
    private TaskCategoryWindowRepository windowRepository;
    private TaskChangeUndoHolder undoHolder;
    private ConfirmAssistantProposalUseCase confirmUseCase;
    private UndoTaskChangesUseCase undoUseCase;
    private List<AssistantTool> tools;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
        windowDao = db.taskCategoryWindowDao();
        SynchronousExecutorService exec = new SynchronousExecutorService();
        windowRepository = new TaskCategoryWindowRepository(windowDao, db.taskCategoryDao());
        undoHolder = new TaskChangeUndoHolder();
        ScheduleReplanCoordinator replan = ReplanCoordinators.inert();
        ApplyTaskChangesUseCase applyUseCase = new ApplyTaskChangesUseCase(
                db, db.taskDao(), db.taskCategoryDao(), windowDao, windowRepository, undoHolder, replan, exec, exec);
        undoUseCase = new UndoTaskChangesUseCase(
                db, db.taskDao(), db.taskCategoryDao(), windowDao, windowRepository, undoHolder, replan, exec, exec);
        AssistantMealGateway mealGateway = new AssistantMealGateway(
                new MealRecipeRoomRepository(db.mealRecipeDao(), db.mealIngredientDao()),
                new MealRoomRepository(db.mealPlanDao(), db.mealConsumptionLogDao(),
                        db.mealHouseholdMemberDao(), db.mealCookingPreferencesDao(),
                        db.mealWeeklyFoodTargetDao()));
        AssistantTransactionImportExecutor importExecutor = new AssistantTransactionImportExecutor(
                BudgetFixtures.budgetImportRepository(db), BudgetFixtures.budgetRepository(db));
        confirmUseCase = new ConfirmAssistantProposalUseCase(applyUseCase, mealGateway, importExecutor, exec, exec);
        tools = new TaskTools(db.taskDao(), db.taskCategoryDao(), windowDao, new DbCalls(exec)).tools();
    }

    @After
    public void tearDown() {
        db.close();
    }

    /** Invariant: the read tool exposes each window with its id and resolved category name. */
    @Test
    public void getCategoryWindowsExposesIdAndFields() throws Exception {
        seedCategory("c1", "Morgenroutine");
        windowDao.write(new TaskCategoryWindow(DayOfWeek.MONDAY, "c1",
                LocalTime.of(6, 0), LocalTime.of(7, 0)));

        JSONObject result = new JSONObject(read("get_category_windows"));
        JSONObject window = result.getJSONArray("windows").getJSONObject(0);
        assertNotNull("window id is exposed so the model can target it", window.getString("id"));
        assertEquals("MONDAY", window.getString("dayOfWeek"));
        assertEquals("Morgenroutine", window.getString("categoryName"));
        assertEquals("06:00", window.getString("startTime"));
        assertEquals("07:00", window.getString("endTime"));
    }

    /** Invariant: proposing a CREATE parks it without writing; confirming persists it and the scheduler sees it. */
    @Test
    public void proposeCreateParksThenConfirmPersistsAndSchedulerSees() {
        seedCategory("c1", "Morgenroutine");

        PendingProposal proposal = propose("{\"windows\":[{\"op\":\"CREATE\",\"dayOfWeek\":\"MONDAY\","
                + "\"categoryName\":\"Morgenroutine\",\"startTime\":\"06:00\",\"endTime\":\"07:00\"}]}");
        assertTrue("propose must not write", windowDao.readAll().isEmpty());
        assertTrue("scheduler sees nothing before confirm", windowRepository.windowsForDay(A_MONDAY).isEmpty());

        confirm(proposal);

        List<TaskCategoryWindow> windows = windowDao.readAll();
        assertEquals(1, windows.size());
        assertEquals("c1", windows.get(0).categoryId);
        assertEquals(LocalTime.of(6, 0), windows.get(0).startTime);
        List<CategoryWindow> forDay = windowRepository.windowsForDay(A_MONDAY);
        assertEquals("cache invalidated → scheduler sees the new reservation", 1, forDay.size());
        assertEquals("c1", forDay.get(0).categoryId());

        undoOk();
        assertTrue("undo removes the created window", windowDao.readAll().isEmpty());
        assertTrue("scheduler no longer sees it after undo", windowRepository.windowsForDay(A_MONDAY).isEmpty());
    }

    /** Invariant: an UPDATE moves the window's times; undo restores the prior times exactly. */
    @Test
    public void proposeUpdateMovesWindowAndUndoRestores() {
        seedCategory("c1", "Morgenroutine");
        TaskCategoryWindow window = new TaskCategoryWindow(DayOfWeek.MONDAY, "c1",
                LocalTime.of(6, 0), LocalTime.of(7, 0));
        windowDao.write(window);

        confirm(propose("{\"windows\":[{\"op\":\"UPDATE\",\"id\":\"" + window.id
                + "\",\"startTime\":\"06:30\",\"endTime\":\"07:30\"}]}"));
        assertEquals(LocalTime.of(6, 30), windowDao.readAll().get(0).startTime);
        assertEquals(LocalTime.of(7, 30), windowDao.readAll().get(0).endTime);

        undoOk();
        assertEquals(LocalTime.of(6, 0), windowDao.readAll().get(0).startTime);
        assertEquals(LocalTime.of(7, 0), windowDao.readAll().get(0).endTime);
    }

    /** Invariant: a DELETE removes the window; undo re-inserts it with its prior fields. */
    @Test
    public void proposeDeleteRemovesWindowAndUndoReinserts() {
        seedCategory("c1", "Morgenroutine");
        TaskCategoryWindow window = new TaskCategoryWindow(DayOfWeek.MONDAY, "c1",
                LocalTime.of(6, 0), LocalTime.of(7, 0));
        windowDao.write(window);

        confirm(propose("{\"windows\":[{\"op\":\"DELETE\",\"id\":\"" + window.id + "\"}]}"));
        assertTrue(windowDao.readAll().isEmpty());

        undoOk();
        assertEquals(1, windowDao.readAll().size());
        assertEquals(LocalTime.of(6, 0), windowDao.readAll().get(0).startTime);
    }

    /** Invariant: invalid proposals are rejected at parse/validate time (before any write). */
    @Test
    public void invalidProposalsAreRejected() {
        seedCategory("c1", "Morgenroutine");

        // endTime not after startTime.
        assertThrows(IllegalArgumentException.class, () -> propose(
                "{\"windows\":[{\"op\":\"CREATE\",\"dayOfWeek\":\"MONDAY\",\"categoryName\":\"Morgenroutine\","
                + "\"startTime\":\"07:00\",\"endTime\":\"06:00\"}]}"));

        // unknown category name.
        assertThrows(IllegalArgumentException.class, () -> propose(
                "{\"windows\":[{\"op\":\"CREATE\",\"dayOfWeek\":\"MONDAY\",\"categoryName\":\"Gibtsnicht\","
                + "\"startTime\":\"06:00\",\"endTime\":\"07:00\"}]}"));

        // unknown window id on UPDATE.
        assertThrows(IllegalArgumentException.class, () -> propose(
                "{\"windows\":[{\"op\":\"UPDATE\",\"id\":\"missing\",\"startTime\":\"06:00\",\"endTime\":\"07:00\"}]}"));

        assertTrue("no write on any rejected proposal", windowDao.readAll().isEmpty());
    }

    private String read(String name) {
        AssistantTool.ToolOutcome outcome = tool(name).run().apply(new JSONObject());
        return ((AssistantTool.ToolOutcome.Json) outcome).content();
    }

    private PendingProposal propose(String inputJson) {
        try {
            AssistantTool.ToolOutcome outcome =
                    tool("propose_category_window_changes").run().apply(new JSONObject(inputJson));
            return ((AssistantTool.ToolOutcome.Parked) outcome).proposal();
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private void confirm(PendingProposal proposal) {
        AtomicReference<String> error = new AtomicReference<>();
        confirmUseCase.confirm(proposal, s -> {}, error::set);
        assertTrue("confirm error: " + error.get(), error.get() == null);
    }

    private void undoOk() {
        AtomicReference<Boolean> result = new AtomicReference<>();
        undoUseCase.undoLast(result::set);
        assertEquals(Boolean.TRUE, result.get());
    }

    private AssistantTool tool(String name) {
        for (AssistantTool tool : tools) {
            if (tool.name().equals(name)) {
                return tool;
            }
        }
        throw new IllegalStateException("tool not found: " + name);
    }

    private void seedCategory(String id, String name) {
        TaskCategory category = new TaskCategory();
        category.id = id;
        category.name = name;
        db.taskCategoryDao().write(category);
    }
}
