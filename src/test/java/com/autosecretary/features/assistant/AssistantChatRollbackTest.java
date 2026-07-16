package com.autosecretary.features.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.meal.data.repository.MealRecipeRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRoomRepository;
import com.autosecretary.features.assistant.application.AssistantChatUseCase;
import com.autosecretary.features.assistant.application.AssistantChatUseCase.AssistantTurn;
import com.autosecretary.features.assistant.application.AssistantConversation;
import com.autosecretary.features.assistant.application.internal.AssistantTool;
import com.autosecretary.features.assistant.application.internal.AssistantToolRegistry;
import com.autosecretary.features.assistant.application.internal.BudgetTools;
import com.autosecretary.features.assistant.application.internal.DbCalls;
import com.autosecretary.features.assistant.application.internal.MealTools;
import com.autosecretary.features.assistant.application.internal.TaskTools;
import com.autosecretary.features.assistant.application.internal.AssistantBudgetGateway;
import com.autosecretary.features.assistant.application.internal.AssistantTransactionImportExecutor;
import com.autosecretary.features.assistant.application.internal.AssistantMealGateway;
import com.autosecretary.shared.ClaudeApiException;
import com.autosecretary.shared.ClaudeApiKeyStore;
import com.autosecretary.shared.ClaudeChatRequest;
import com.autosecretary.shared.ClaudeChatResponse;
import com.autosecretary.shared.ClaudeChatResponse.ToolUse;
import com.autosecretary.shared.ClaudeChatTransport;
import com.autosecretary.shared.ClaudeEndpointStore;
import com.autosecretary.shared.ClaudeModelStore;
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
 * Pins the failed-turn rollback invariant: when a round throws mid-loop, the conversation must
 * return to its pre-turn state. Otherwise a dangling assistant {@code tool_use} (whose
 * {@code tool_result} never arrived) is left in the history and every subsequent request fails
 * with HTTP 400 — a chat that breaks permanently after one transient error.
 */
public final class AssistantChatRollbackTest extends AutoSecretaryRobolectricTest {

    private AppDatabase db;
    private AssistantMealGateway mealGateway;
    private AssistantConversation conversation;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
        mealGateway = new AssistantMealGateway(
                new MealRecipeRoomRepository(db.mealRecipeDao(), db.mealIngredientDao()),
                new MealRoomRepository(db.mealPlanDao(), db.mealConsumptionLogDao(),
                        db.mealHouseholdMemberDao(), db.mealCookingPreferencesDao(),
                        db.mealWeeklyFoodTargetDao()));
        conversation = new AssistantConversation();
    }

    @After
    public void tearDown() {
        db.close();
    }

    /** Invariant: an error after an appended tool_use turn rolls the conversation back to empty. */
    @Test
    public void failedTurnRollsBackConversation() throws JSONException {
        // Round 1 asks for a tool; the tool-result round then fails at the transport.
        FailAfterToolUse transport = new FailAfterToolUse(
                new ClaudeApiException("upstream is down", 500));

        AtomicReference<String> error = new AtomicReference<>();
        AtomicReference<AssistantTurn> result = new AtomicReference<>();
        engine(transport).send("Welche Tasks habe ich?", null, false, result::set, error::set, p -> {});

        assertNotNull("the failure is surfaced to the UI", error.get());
        assertTrue("conversation rolled back to its pre-turn (empty) state", conversation.isEmpty());
    }

    /** Invariant: after a failed turn, the retry sends a clean history — one user message, no tool_use. */
    @Test
    public void retryAfterFailureStartsFromCleanHistory() throws JSONException {
        FailAfterToolUse failing = new FailAfterToolUse(new ClaudeApiException("upstream is down", 500));
        AssistantChatUseCase engine = engine(failing);
        engine.send("Welche Tasks habe ich?", null, false, t -> {}, e -> {}, p -> {});
        assertTrue("precondition: first turn rolled back", conversation.isEmpty());

        // Reuse the same conversation for the retry — the bug manifests across sends.
        RecordingTransport retry = new RecordingTransport(end("Du hast keine offenen Tasks."));
        engineWith(retry).send("Nochmal bitte", null, false, t -> {}, e -> {}, p -> {});

        assertEquals("exactly one request on the clean retry", 1, retry.requests.size());
        JSONArray messages = retry.requests.get(0).messages();
        assertEquals("history holds only the single retry user message", 1, messages.length());
        assertEquals("user", messages.getJSONObject(0).getString("role"));
        assertFalse("no dangling tool_use from the failed turn",
                messages.toString().contains("tool_use"));
    }

    // ---- Wiring ---------------------------------------------------------------

    private AssistantChatUseCase engine(ClaudeChatTransport transport) {
        return engineWith(transport);
    }

    private AssistantChatUseCase engineWith(ClaudeChatTransport transport) {
        SynchronousExecutorService exec = new SynchronousExecutorService();
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

    /** Serves a tool_use on the first call, then throws on the tool-result round. */
    private static final class FailAfterToolUse implements ClaudeChatTransport {
        private final ClaudeApiException error;
        private int calls;

        FailAfterToolUse(ClaudeApiException error) {
            this.error = error;
        }

        @Override
        public ClaudeChatResponse chat(ClaudeChatRequest request) {
            calls++;
            if (calls == 1) {
                try {
                    return toolUse("get_tasks", new JSONObject());
                } catch (JSONException e) {
                    throw new IllegalStateException(e);
                }
            }
            throw error;
        }
    }

    /** Records each request and returns a single scripted response. */
    private static final class RecordingTransport implements ClaudeChatTransport {
        private final ClaudeChatResponse response;
        private final List<ClaudeChatRequest> requests = new ArrayList<>();

        RecordingTransport(ClaudeChatResponse response) {
            this.response = response;
        }

        @Override
        public ClaudeChatResponse chat(ClaudeChatRequest request) {
            requests.add(request);
            return response;
        }
    }
}
