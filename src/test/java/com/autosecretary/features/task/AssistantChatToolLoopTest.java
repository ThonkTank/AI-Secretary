package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.meal.data.repository.MealRecipeRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRoomRepository;
import com.autosecretary.features.task.application.assistant.AssistantChatUseCase;
import com.autosecretary.features.task.application.assistant.AssistantChatUseCase.AssistantTurn;
import com.autosecretary.features.task.application.assistant.AssistantConversation;
import com.autosecretary.features.task.application.assistant.AssistantProposals.RecipesProposal;
import com.autosecretary.features.task.application.assistant.internal.AssistantTool;
import com.autosecretary.features.task.application.assistant.internal.AssistantToolRegistry;
import com.autosecretary.features.task.application.assistant.internal.BudgetTools;
import com.autosecretary.features.task.application.assistant.internal.DbCalls;
import com.autosecretary.features.task.application.assistant.internal.MealTools;
import com.autosecretary.features.task.application.assistant.internal.TaskTools;
import com.autosecretary.features.task.application.internal.budget.AssistantBudgetGateway;
import com.autosecretary.features.task.application.internal.budget.AssistantTransactionImportExecutor;
import com.autosecretary.features.task.application.internal.meal.AssistantMealGateway;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.shared.ClaudeApiException;
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
import com.autosecretary.testing.SynchronousExecutorService;
import com.autosecretary.testing.TestDatabases;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Drives the chat engine with a scripted transport to protect two invariants: the {@code get_tasks}
 * tool result carries the repetition/streak fields (so DB questions like "how often does my morning
 * routine repeat?" can be answered), and a {@code propose_*} tool parks a proposal without writing.
 */
public final class AssistantChatToolLoopTest extends AutoSecretaryRobolectricTest {

    private AppDatabase db;
    private AssistantMealGateway mealGateway;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
        mealGateway = new AssistantMealGateway(
                new MealRecipeRoomRepository(db.mealRecipeDao(), db.mealIngredientDao()),
                new MealRoomRepository(db.mealPlanDao(), db.mealConsumptionLogDao(),
                        db.mealHouseholdMemberDao(), db.mealCookingPreferencesDao(),
                        db.mealWeeklyFoodTargetDao()));
    }

    @After
    public void tearDown() {
        db.close();
    }

    /** Invariant: get_tasks returns reps/perPeriod/periodUnit/currentStreak for a repeating task. */
    @Test
    public void getTasksToolResultCarriesRepetitionAndStreak() throws JSONException {
        Task task = new Task();
        task.core.title = "Zähneputzen";
        task.core.repetition.reps = 3;
        task.core.repetition.perPeriod = 1;
        task.core.repetition.periodUnit = Period.WEEK;
        task.core.history.currentStreak = 2;
        db.taskDao().write(task);

        ScriptedTransport transport = new ScriptedTransport(
                toolUse("get_tasks", new JSONObject()),
                end("Deine Morgenroutine wiederholt sich wöchentlich."));

        AtomicReference<AssistantTurn> result = new AtomicReference<>();
        engine(transport).send("Welche Tasks habe ich?", null, false, result::set, e -> {}, p -> {});

        assertEquals("engine loops once through the tool call", 2, transport.requests.size());
        // The tool result is embedded as an (escaped) JSON string in the follow-up request; assert on
        // the field names and the enum value, which survive the escaping.
        String secondRequest = transport.requests.get(1).messages().toString();
        assertTrue("reps present", secondRequest.contains("reps"));
        assertTrue("perPeriod present", secondRequest.contains("perPeriod"));
        assertTrue("periodUnit value present", secondRequest.contains("WEEK"));
        assertTrue("streak present", secondRequest.contains("currentStreak"));
        assertEquals("Deine Morgenroutine wiederholt sich wöchentlich.", result.get().answerText());
    }

    /** Invariant: get_tasks with a category name that matches nothing returns an empty task list. */
    @Test
    public void getTasksWithUnknownCategoryReturnsNoTasks() throws JSONException {
        Task task = new Task();
        task.core.title = "Zähneputzen";
        db.taskDao().write(task);

        ScriptedTransport transport = new ScriptedTransport(
                toolUse("get_tasks", new JSONObject().put("categoryName", "Gibtsnicht")),
                end("Keine Tasks in dieser Kategorie."));

        engine(transport).send("Tasks in Kategorie Gibtsnicht?", null, false, t -> {}, e -> {}, p -> {});

        String toolResult = transport.requests.get(1).messages().toString();
        assertTrue("empty task array in the tool result", toolResult.contains("\\\"tasks\\\":[]"));
        assertFalse("the real task is not leaked", toolResult.contains("Zähneputzen"));
    }

    /** Invariant: propose_recipes parks a proposal but writes nothing until confirmed. */
    @Test
    public void proposeRecipesParksProposalWithoutWriting() throws JSONException {
        JSONObject input = new JSONObject().put("recipes", new JSONArray().put(new JSONObject()
                .put("title", "Toast")
                .put("ingredients", new JSONArray().put(new JSONObject()
                        .put("name", "Brot").put("amount", 2).put("unit", "Scheibe")))));

        ScriptedTransport transport = new ScriptedTransport(
                toolUse("propose_recipes", input),
                end("Vorschlag steht bereit."));

        AtomicReference<AssistantTurn> result = new AtomicReference<>();
        engine(transport).send("Leg ein Toast-Rezept an", null, false, result::set, e -> {}, p -> {});

        assertEquals(1, result.get().proposals().size());
        assertTrue(result.get().proposals().get(0) instanceof RecipesProposal);
        assertTrue("nothing persisted before confirm", mealGateway.recipes().isEmpty());
    }

    // ---- Loop behaviour matrix ------------------------------------------------
    // The regression net asserts correct handling of well-formed responses. The CHARACTERIZATION
    // tests pin down the current "announces intent then stops" behaviour and are expected to change
    // once the root cause is fixed — flip them then.

    /**
     * CHARACTERIZATION: a lone text-only end_turn returns the announcement verbatim and stops —
     * exactly the "prüfe ich zunächst…" failure. One request, no tools, no proposals.
     */
    @Test
    public void textOnlyEndTurnReturnsAnnouncementAndStops() throws JSONException {
        ScriptedTransport transport = new ScriptedTransport(
                end("Bevor ich Vorschläge erstelle, prüfe ich zunächst deine Tasks…"));

        AtomicReference<AssistantTurn> result = new AtomicReference<>();
        engine(transport).send("Pflege diese Aufgaben ein", null, false, result::set, e -> {}, p -> {});

        assertEquals("no tool round — engine stops on the first end_turn", 1, transport.requests.size());
        assertEquals("Bevor ich Vorschläge erstelle, prüfe ich zunächst deine Tasks…",
                result.get().answerText());
        assertTrue("no proposals parked", result.get().proposals().isEmpty());
    }

    /** Invariant: two tool rounds both run before the final answer (3 requests). */
    @Test
    public void twoToolRoundsBothRunBeforeAnswer() throws JSONException {
        ScriptedTransport transport = new ScriptedTransport(
                toolUse("get_tasks", new JSONObject()),
                toolUse("get_recipes", new JSONObject()),
                end("Beide Quellen geprüft."));

        AtomicReference<AssistantTurn> result = new AtomicReference<>();
        engine(transport).send("Prüfe Tasks und Rezepte", null, false, result::set, e -> {}, p -> {});

        assertEquals(3, transport.requests.size());
        assertEquals("Beide Quellen geprüft.", result.get().answerText());
    }

    /** Invariant: max_tokens appends the truncation suffix to the partial answer. */
    @Test
    public void maxTokensAppendsTruncationSuffix() throws JSONException {
        ScriptedTransport transport = new ScriptedTransport(maxTokens("Teilantwort"));

        AtomicReference<AssistantTurn> result = new AtomicReference<>();
        engine(transport).send("Erklär mir alles", null, false, result::set, e -> {}, p -> {});

        assertEquals(1, transport.requests.size());
        assertTrue("partial text kept", result.get().answerText().startsWith("Teilantwort"));
        assertTrue("truncation marker appended", result.get().answerText().contains("(Antwort gekürzt)"));
    }

    /** Invariant: pause_turn re-sends and resumes to the eventual answer. */
    @Test
    public void pauseTurnResumesToFinalAnswer() throws JSONException {
        ScriptedTransport transport = new ScriptedTransport(pauseTurn(), end("Fertig."));

        AtomicReference<AssistantTurn> result = new AtomicReference<>();
        engine(transport).send("Lange Aufgabe", null, false, result::set, e -> {}, p -> {});

        assertEquals("paused turn re-sent once", 2, transport.requests.size());
        assertEquals("Fertig.", result.get().answerText());
    }

    /** Invariant: an unbounded tool loop is capped at MAX_ITERATIONS and surfaced as an error. */
    @Test
    public void unboundedToolLoopAbortsAtMaxIterations() throws JSONException {
        ScriptedTransport transport = new ScriptedTransport(toolUse("get_tasks", new JSONObject()));

        AtomicReference<String> error = new AtomicReference<>();
        AtomicReference<AssistantTurn> result = new AtomicReference<>();
        engine(transport).send("Dreh dich im Kreis", null, false, result::set, error::set, p -> {});

        assertNull("no successful turn", result.get());
        assertTrue("aborted after too many steps", error.get().contains("zu viele Schritte"));
    }

    /**
     * CHARACTERIZATION: a tool_use stop_reason whose tool blocks the parser did not surface (empty
     * toolUses) makes the loop spin with empty tool-result turns until MAX_ITERATIONS aborts — the
     * parse-mismatch failure mode, indistinguishable to the user from "announced then stopped".
     */
    @Test
    public void toolUseWithNoParsedToolsSpinsToAbort() throws JSONException {
        ScriptedTransport transport = new ScriptedTransport(
                toolUseButNoParsedTools("Ich rufe jetzt get_tasks auf."));

        AtomicReference<String> error = new AtomicReference<>();
        engine(transport).send("Pflege ein", null, false, t -> {}, error::set, p -> {});

        assertTrue("unrecognised tool_use spins to abort", error.get().contains("zu viele Schritte"));
    }

    /** Invariant: an unknown tool yields an is_error result and the loop continues to the answer. */
    @Test
    public void unknownToolYieldsErrorResultAndContinues() throws JSONException {
        ScriptedTransport transport = new ScriptedTransport(
                toolUse("get_bogus", new JSONObject()),
                end("Trotzdem fertig."));

        AtomicReference<AssistantTurn> result = new AtomicReference<>();
        engine(transport).send("Nutze ein Tool", null, false, result::set, e -> {}, p -> {});

        assertEquals(2, transport.requests.size());
        assertEquals("Trotzdem fertig.", result.get().answerText());
        assertTrue("error tool_result fed back into round 2",
                transport.requests.get(1).messages().toString().contains("Unbekanntes Tool"));
    }

    /** Invariant: multiple tool_use blocks in one response all run in that single round. */
    @Test
    public void multipleToolBlocksAllRunInOneRound() throws JSONException {
        ScriptedTransport transport = new ScriptedTransport(
                twoToolUses("get_tasks", new JSONObject(), "get_recipes", new JSONObject()),
                end("Alles geprüft."));

        List<String> progress = new ArrayList<>();
        AtomicReference<AssistantTurn> result = new AtomicReference<>();
        engine(transport).send("Prüfe beides", null, false, result::set, e -> {}, progress::add);

        assertEquals("one tool round then the answer", 2, transport.requests.size());
        assertTrue("first block ran", progress.contains("Prüfe vorhandene Tasks…"));
        assertTrue("second block ran", progress.contains("Prüfe Rezepte…"));
        assertEquals("Alles geprüft.", result.get().answerText());
    }

    /** Invariant: thinking blocks accumulate into AssistantTurn.thinkingText(). */
    @Test
    public void thinkingTextAccumulatesIntoTurn() throws JSONException {
        ScriptedTransport transport = new ScriptedTransport(
                endWithThinking("Antwort.", "Ich überlege kurz."));

        AtomicReference<AssistantTurn> result = new AtomicReference<>();
        engine(transport).send("Denk nach", null, false, result::set, e -> {}, p -> {});

        assertEquals("Ich überlege kurz.", result.get().thinkingText());
        assertEquals("Antwort.", result.get().answerText());
    }

    /** Invariant: a thinking-related HTTP 400 steps the ladder ADAPTIVE→ENABLED and retries. */
    @Test
    public void thinkingError400StepsDownAdaptiveToEnabled() throws JSONException {
        ThrowThenRespond transport = new ThrowThenRespond(
                new ClaudeApiException("thinking is not supported for this model", 400),
                end("Fertig ohne adaptive."));

        AtomicReference<AssistantTurn> result = new AtomicReference<>();
        engine(transport).send("Hallo", null, true, result::set, e -> {}, p -> {});

        assertEquals("adaptive attempt then enabled retry", 2, transport.requests.size());
        assertEquals("adaptive", transport.requests.get(0).thinking().getString("type"));
        assertEquals("enabled", transport.requests.get(1).thinking().getString("type"));
        assertEquals("Fertig ohne adaptive.", result.get().answerText());
    }

    /** CHARACTERIZATION: a refusal stop_reason is surfaced as a normal answer and stops. */
    @Test
    public void refusalStopReasonSurfacesTextAndStops() throws JSONException {
        ScriptedTransport transport = new ScriptedTransport(refusal("Das kann ich nicht tun."));

        AtomicReference<AssistantTurn> result = new AtomicReference<>();
        engine(transport).send("Tu etwas Verbotenes", null, false, result::set, e -> {}, p -> {});

        assertEquals(1, transport.requests.size());
        assertEquals("Das kann ich nicht tun.", result.get().answerText());
        assertTrue(result.get().proposals().isEmpty());
    }

    /** CHARACTERIZATION: a missing/null stop_reason ends the turn on the accumulated text. */
    @Test
    public void nullStopReasonStopsOnAccumulatedText() throws JSONException {
        ScriptedTransport transport = new ScriptedTransport(new ClaudeChatResponse(null,
                new JSONArray().put(new JSONObject().put("type", "text").put("text", "Text ohne Stopgrund.")),
                "Text ohne Stopgrund.", "", List.of()));

        AtomicReference<AssistantTurn> result = new AtomicReference<>();
        engine(transport).send("Frag was", null, false, result::set, e -> {}, p -> {});

        assertEquals(1, transport.requests.size());
        assertEquals("Text ohne Stopgrund.", result.get().answerText());
    }

    private AssistantChatUseCase engine(ClaudeChatTransport transport) {
        SynchronousExecutorService exec = new SynchronousExecutorService();
        AssistantConversation conversation = new AssistantConversation();
        AssistantBudgetGateway budgetGateway = new AssistantBudgetGateway(BudgetFixtures.budgetRepository(db));
        AssistantTransactionImportExecutor importExecutor = new AssistantTransactionImportExecutor(
                BudgetFixtures.budgetImportRepository(db), BudgetFixtures.budgetRepository(db));
        DbCalls dbCalls = new DbCalls(exec);
        List<AssistantTool> tools = new ArrayList<>();
        tools.addAll(new TaskTools(db.taskDao(), db.taskCategoryDao(), dbCalls).tools());
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

    private static ClaudeChatResponse endWithThinking(String text, String thinking) throws JSONException {
        JSONArray raw = new JSONArray()
                .put(new JSONObject().put("type", "thinking").put("thinking", thinking))
                .put(new JSONObject().put("type", "text").put("text", text));
        return new ClaudeChatResponse("end_turn", raw, text, thinking, List.of());
    }

    private static ClaudeChatResponse maxTokens(String text) throws JSONException {
        JSONArray raw = new JSONArray().put(new JSONObject().put("type", "text").put("text", text));
        return new ClaudeChatResponse("max_tokens", raw, text, "", List.of());
    }

    private static ClaudeChatResponse pauseTurn() {
        return new ClaudeChatResponse("pause_turn", new JSONArray(), "", "", List.of());
    }

    private static ClaudeChatResponse refusal(String text) throws JSONException {
        JSONArray raw = new JSONArray().put(new JSONObject().put("type", "text").put("text", text));
        return new ClaudeChatResponse("refusal", raw, text, "", List.of());
    }

    /** stop_reason "tool_use" but a raw text block and an EMPTY toolUses list — the parse-mismatch trap. */
    private static ClaudeChatResponse toolUseButNoParsedTools(String text) throws JSONException {
        JSONArray raw = new JSONArray().put(new JSONObject().put("type", "text").put("text", text));
        return new ClaudeChatResponse("tool_use", raw, text, "", List.of());
    }

    private static ClaudeChatResponse twoToolUses(String name1, JSONObject input1,
                                                  String name2, JSONObject input2) throws JSONException {
        JSONArray raw = new JSONArray()
                .put(new JSONObject().put("type", "tool_use").put("id", "tu_1").put("name", name1).put("input", input1))
                .put(new JSONObject().put("type", "tool_use").put("id", "tu_2").put("name", name2).put("input", input2));
        return new ClaudeChatResponse("tool_use", raw, "", "",
                List.of(new ToolUse("tu_1", name1, input1), new ToolUse("tu_2", name2, input2)));
    }

    /** Returns pre-scripted responses in order and records each request for assertions. */
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
            // Bounds-safe: repeat the last scripted response once exhausted, so spin-until-abort
            // scenarios (MAX_ITERATIONS, empty-toolUses parse trap) don't throw IndexOutOfBounds.
            ClaudeChatResponse response = responses.get(Math.min(index, responses.size() - 1));
            index++;
            return response;
        }
    }

    /** Throws a thinking-related 400 on the first call, then serves the scripted response. */
    private static final class ThrowThenRespond implements ClaudeChatTransport {
        private final ClaudeApiException error;
        private final ClaudeChatResponse then;
        private final List<ClaudeChatRequest> requests = new ArrayList<>();
        private boolean thrown;

        ThrowThenRespond(ClaudeApiException error, ClaudeChatResponse then) {
            this.error = error;
            this.then = then;
        }

        @Override
        public ClaudeChatResponse chat(ClaudeChatRequest request) {
            requests.add(request);
            if (!thrown) {
                thrown = true;
                throw error;
            }
            return then;
        }
    }
}
