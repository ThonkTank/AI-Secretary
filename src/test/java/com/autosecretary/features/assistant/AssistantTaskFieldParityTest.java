package com.autosecretary.features.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.meal.data.repository.MealRecipeRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRoomRepository;
import com.autosecretary.features.task.application.ApplyTaskChangesUseCase;
import com.autosecretary.features.task.application.TaskChangeUndoHolder;
import com.autosecretary.features.assistant.application.AssistantChatUseCase;
import com.autosecretary.features.assistant.application.AssistantChatUseCase.AssistantTurn;
import com.autosecretary.features.assistant.application.AssistantConversation;
import com.autosecretary.features.assistant.application.internal.AssistantTool;
import com.autosecretary.features.assistant.application.internal.AssistantToolRegistry;
import com.autosecretary.features.assistant.application.internal.BudgetTools;
import com.autosecretary.features.assistant.application.internal.DbCalls;
import com.autosecretary.features.assistant.application.internal.MealTools;
import com.autosecretary.features.assistant.application.internal.TaskTools;
import com.autosecretary.features.assistant.application.AssistantProposals.PendingProposal;
import com.autosecretary.features.assistant.application.ConfirmAssistantProposalUseCase;
import com.autosecretary.features.assistant.application.internal.AssistantBudgetGateway;
import com.autosecretary.features.assistant.application.internal.AssistantTransactionImportExecutor;
import com.autosecretary.features.assistant.application.internal.AssistantMealGateway;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskCore;
import com.autosecretary.features.task.domain.model.TaskPrefSlot;
import com.autosecretary.shared.ClaudeApiKeyStore;
import com.autosecretary.shared.ClaudeChatRequest;
import com.autosecretary.shared.ClaudeChatResponse;
import com.autosecretary.shared.ClaudeChatResponse.ToolUse;
import com.autosecretary.shared.ClaudeChatTransport;
import com.autosecretary.shared.ClaudeEndpointStore;
import com.autosecretary.shared.ClaudeModelStore;
import com.autosecretary.shared.Period;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.BudgetFixtures;
import com.autosecretary.testing.ReplanCoordinators;
import com.autosecretary.testing.SynchronousExecutorService;
import com.autosecretary.testing.TestDatabases;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * End-to-end parity between the assistant's {@code propose_task_changes} tool and the manual task
 * editor: a scripted tool call carrying repetition, scheduling, dates, pref-slots, progress and budget
 * fields is parsed, parked, confirmed, and read back from Room with every field intact — plus the
 * repetition-counter reset rule and the TERMIN-needs-a-date guard.
 */
public final class AssistantTaskFieldParityTest extends AutoSecretaryRobolectricTest {

    private AppDatabase db;
    private SynchronousExecutorService exec;
    private AssistantMealGateway mealGateway;
    private ConfirmAssistantProposalUseCase confirmUseCase;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
        exec = new SynchronousExecutorService();
        mealGateway = new AssistantMealGateway(
                new MealRecipeRoomRepository(db.mealRecipeDao(), db.mealIngredientDao()),
                new MealRoomRepository(db.mealPlanDao(), db.mealConsumptionLogDao(),
                        db.mealHouseholdMemberDao(), db.mealCookingPreferencesDao(),
                        db.mealWeeklyFoodTargetDao()));
        AssistantTransactionImportExecutor importExecutor = new AssistantTransactionImportExecutor(
                BudgetFixtures.budgetImportRepository(db), BudgetFixtures.budgetRepository(db));
        ApplyTaskChangesUseCase applyUseCase = new ApplyTaskChangesUseCase(
                db, db.taskDao(), db.taskCategoryDao(), db.taskCategoryWindowDao(),
                new com.autosecretary.features.task.application.config.TaskCategoryWindowRepository(
                        db.taskCategoryWindowDao(), db.taskCategoryDao()),
                new TaskChangeUndoHolder(), ReplanCoordinators.inert(), exec, exec);
        confirmUseCase = new ConfirmAssistantProposalUseCase(applyUseCase, mealGateway, importExecutor, exec, exec);
    }

    @After
    public void tearDown() {
        db.close();
    }

    /** Invariant: a CREATE proposal round-trips every editor field onto TaskCore/prefSlots. */
    @Test
    public void createTaskPersistsEveryField() throws JSONException {
        JSONObject task = new JSONObject()
                .put("op", "CREATE").put("title", "Pflanzen gießen").put("description", "Alle Zimmerpflanzen")
                .put("priority", "HIGH").put("leisure", false).put("adaptive", true)
                .put("minDuration", 10).put("maxDuration", 30).put("cooldown", 2)
                .put("startDate", "2026-07-20").put("deadline", "2026-08-31").put("closeOnMiss", false)
                .put("budgetRequiredCents", 500).put("budgetAccountId", "acc1").put("budgetCategoryId", "cat1")
                .put("repetition", new JSONObject().put("reps", 3).put("perPeriod", 1)
                        .put("periodUnit", "WEEK").put("completeFirst", true))
                .put("progress", new JSONObject().put("unit", "Räume").put("target", 5).put("current", 1)
                        .put("resetPerRep", true).put("minPerRep", 1).put("maxPerRep", 3))
                .put("prefSlots", new JSONArray()
                        .put(new JSONObject().put("days", new JSONArray().put(1).put(3).put(5)).put("start", "07:30"))
                        .put(new JSONObject().put("days", new JSONArray().put(6)).put("start", "09:00")));

        Task saved = createAndConfirm(task);
        TaskCore core = saved.core;

        assertEquals("Pflanzen gießen", core.title);
        assertEquals("Alle Zimmerpflanzen", core.description);
        assertEquals(com.autosecretary.shared.Priority.HIGH, core.priority);
        assertTrue(core.adaptive);
        assertFalse(core.closeOnMiss);
        assertEquals(10, core.minDuration);
        assertEquals(30, core.maxDuration);
        assertEquals(2, core.cooldown);
        assertEquals(LocalDate.of(2026, 7, 20), core.startDate);
        assertEquals(LocalDate.of(2026, 8, 31), core.deadline);
        assertEquals(Integer.valueOf(500), core.budgetRequiredCents);
        assertEquals("acc1", core.budgetAccountId);
        assertEquals("cat1", core.budgetCategoryId);

        assertEquals(3, core.repetition.reps);
        assertEquals(1, core.repetition.perPeriod);
        assertEquals(Period.WEEK, core.repetition.periodUnit);
        assertTrue(core.repetition.completeFirst);
        assertNotNull("periodStart initialized on create", core.repetition.periodStart);
        assertEquals(0, core.repetition.periodCompletions);
        assertEquals(0, core.repetition.carryoverDebt);

        assertEquals("Räume", core.progress.unit);
        assertEquals(5, core.progress.target);
        assertEquals(1, core.progress.current);
        assertTrue(core.progress.resetPerRep);
        assertEquals(1, core.progress.minPerRep);
        assertEquals(3, core.progress.maxPerRep);

        assertEquals(2, saved.prefSlots.size());
        TaskPrefSlot weekday = prefSlotStartingAt(saved, LocalTime.of(7, 30));
        assertEquals(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), weekday.days);
        TaskPrefSlot saturday = prefSlotStartingAt(saved, LocalTime.of(9, 0));
        assertEquals(Set.of(DayOfWeek.SATURDAY), saturday.days);
    }

    /** Invariant: a recurring CREATE with no prefSlots seeds the editor's default slot (all days, 06:00). */
    @Test
    public void recurringCreateWithoutPrefSlotsSeedsDefaultSlot() throws JSONException {
        JSONObject task = new JSONObject().put("op", "CREATE").put("title", "Wäsche waschen")
                .put("repetition", new JSONObject().put("reps", 1).put("perPeriod", 1).put("periodUnit", "WEEK"));

        Task saved = createAndConfirm(task);

        assertEquals(1, saved.prefSlots.size());
        assertEquals(LocalTime.of(6, 0), saved.prefSlots.get(0).start);
        assertEquals(7, saved.prefSlots.get(0).days.size());
    }

    /** Invariant: an UPDATE touches only the given fields and, on a pattern change, resets the counters. */
    @Test
    public void updateChangesOnlyGivenFieldsAndResetsCountersOnPatternChange() throws JSONException {
        Task seed = new Task();
        seed.core.title = "Pflanzen gießen";
        seed.core.description = "unverändert";
        seed.core.deadline = LocalDate.of(2026, 12, 31);
        seed.core.repetition.reps = 2;
        seed.core.repetition.perPeriod = 1;
        seed.core.repetition.periodUnit = Period.WEEK;
        seed.core.repetition.periodStart = LocalDate.of(2026, 1, 1);
        seed.core.repetition.periodCompletions = 5;
        seed.core.repetition.carryoverDebt = 1;
        db.taskDao().write(seed);

        JSONObject task = new JSONObject().put("op", "UPDATE").put("id", seed.core.id)
                .put("repetition", new JSONObject().put("reps", 4));

        confirmAll(propose(task));
        Task updated = db.taskDao().read(seed.core.id);

        assertEquals("reps updated", 4, updated.core.repetition.reps);
        assertEquals("perPeriod untouched", 1, updated.core.repetition.perPeriod);
        assertEquals("periodUnit untouched", Period.WEEK, updated.core.repetition.periodUnit);
        assertEquals("counters reset on pattern change", 0, updated.core.repetition.periodCompletions);
        assertEquals(0, updated.core.repetition.carryoverDebt);
        assertEquals("description untouched", "unverändert", updated.core.description);
        assertEquals("deadline untouched", LocalDate.of(2026, 12, 31), updated.core.deadline);
    }

    /** Invariant: an UPDATE that repeats the same repetition pattern preserves the period counters. */
    @Test
    public void updateWithSameRepetitionPatternKeepsCounters() throws JSONException {
        Task seed = new Task();
        seed.core.title = "Zähneputzen";
        seed.core.repetition.reps = 2;
        seed.core.repetition.perPeriod = 1;
        seed.core.repetition.periodUnit = Period.WEEK;
        seed.core.repetition.periodStart = LocalDate.of(2026, 1, 1);
        seed.core.repetition.periodCompletions = 5;
        db.taskDao().write(seed);

        JSONObject task = new JSONObject().put("op", "UPDATE").put("id", seed.core.id)
                .put("repetition", new JSONObject().put("reps", 2).put("perPeriod", 1).put("periodUnit", "WEEK"));

        confirmAll(propose(task));
        Task updated = db.taskDao().read(seed.core.id);

        assertEquals(5, updated.core.repetition.periodCompletions);
        assertEquals(LocalDate.of(2026, 1, 1), updated.core.repetition.periodStart);
    }

    /** Invariant: a new TERMIN without a fixedDate is rejected as a tool error and parks nothing. */
    @Test
    public void newTerminWithoutFixedDateIsRejected() throws JSONException {
        JSONObject task = new JSONObject().put("op", "CREATE").put("title", "Zahnarzt")
                .put("schedulingType", "TERMIN");

        ScriptedTransport transport = new ScriptedTransport(
                toolUse("propose_task_changes", new JSONObject().put("tasks", new JSONArray().put(task))),
                end("Der Termin braucht ein Datum."));
        AtomicReference<AssistantTurn> turn = new AtomicReference<>();
        engine(transport).send("Leg einen Zahnarzttermin an", null, false,
                turn::set, e -> fail("unexpected error: " + e), p -> {});

        assertTrue("no proposal parked for an invalid termin", turn.get().proposals().isEmpty());
        assertTrue("validation error fed back to the model",
                transport.requests.get(1).messages().toString().contains("fixedDate"));
        assertEquals("nothing written", 0, db.taskDao().readAll().size());
    }

    // ---- helpers -----------------------------------------------------------

    /** Scripts a single-task CREATE proposal, sends it, confirms the parked proposal, returns the row. */
    private Task createAndConfirm(JSONObject task) throws JSONException {
        confirmAll(propose(task));
        List<Task> tasks = db.taskDao().readAll();
        assertEquals(1, tasks.size());
        return tasks.get(0);
    }

    /** Drives the engine with a scripted propose_task_changes call and returns the parked proposal. */
    private PendingProposal propose(JSONObject task) throws JSONException {
        ScriptedTransport transport = new ScriptedTransport(
                toolUse("propose_task_changes", new JSONObject().put("tasks", new JSONArray().put(task))),
                end("Vorschlag steht bereit."));
        AtomicReference<AssistantTurn> turn = new AtomicReference<>();
        engine(transport).send("Pflege diese Aufgabe ein", null, false,
                turn::set, e -> fail("unexpected error: " + e), p -> {});
        assertEquals("exactly one proposal parked", 1, turn.get().proposals().size());
        return turn.get().proposals().get(0);
    }

    private void confirmAll(PendingProposal proposal) {
        AtomicReference<String> error = new AtomicReference<>();
        confirmUseCase.confirm(proposal, s -> {}, error::set);
        assertNull("confirm failed: " + error.get(), error.get());
    }

    private static TaskPrefSlot prefSlotStartingAt(Task task, LocalTime start) {
        for (TaskPrefSlot slot : task.prefSlots) {
            if (start.equals(slot.start)) {
                return slot;
            }
        }
        fail("no pref slot starting at " + start);
        return null;
    }

    private AssistantChatUseCase engine(ClaudeChatTransport transport) {
        AssistantConversation conversation = new AssistantConversation();
        AssistantBudgetGateway budgetGateway = new AssistantBudgetGateway(BudgetFixtures.budgetRepository(db));
        AssistantTransactionImportExecutor importExecutor = new AssistantTransactionImportExecutor(
                BudgetFixtures.budgetImportRepository(db), BudgetFixtures.budgetRepository(db));
        DbCalls dbCalls = new DbCalls(exec);
        List<AssistantTool> tools = new ArrayList<>();
        tools.addAll(new TaskTools(
                db.taskDao(), db.taskCategoryDao(), db.taskCategoryWindowDao(), dbCalls).tools());
        tools.addAll(new MealTools(mealGateway, dbCalls).tools());
        tools.addAll(new BudgetTools(budgetGateway, importExecutor,
                conversation::currentStatement, dbCalls).tools());
        return new AssistantChatUseCase(transport, conversation, new AssistantToolRegistry(tools),
                new ClaudeApiKeyStore(ApplicationProvider.getApplicationContext()),
                new ClaudeEndpointStore(ApplicationProvider.getApplicationContext()),
                new ClaudeModelStore(ApplicationProvider.getApplicationContext()),
                exec, exec);
    }

    private static ClaudeChatResponse toolUse(String name, JSONObject input) throws JSONException {
        JSONObject block = new JSONObject().put("type", "tool_use")
                .put("id", "tu_1").put("name", name).put("input", input);
        return new ClaudeChatResponse("tool_use", new JSONArray().put(block), "", "",
                List.of(new ToolUse("tu_1", name, input)));
    }

    private static ClaudeChatResponse end(String text) throws JSONException {
        JSONArray raw = new JSONArray().put(new JSONObject().put("type", "text").put("text", text));
        return new ClaudeChatResponse("end_turn", raw, text, "", List.of());
    }

    /** Returns pre-scripted responses in order and records each request. */
    private static final class ScriptedTransport implements ClaudeChatTransport {
        private final List<ClaudeChatResponse> responses;
        private final List<ClaudeChatRequest> requests = new ArrayList<>();
        private int index;

        ScriptedTransport(ClaudeChatResponse... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public ClaudeChatResponse chat(ClaudeChatRequest request) {
            requests.add(request);
            ClaudeChatResponse response = responses.get(Math.min(index, responses.size() - 1));
            index++;
            return response;
        }
    }
}
