package com.autosecretary.features.task.application.assistant;

import android.util.Log;

import com.autosecretary.BuildConfig;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.task.application.assistant.AssistantProposals.IngredientsProposal;
import com.autosecretary.features.task.application.assistant.AssistantProposals.MealPlanDraft;
import com.autosecretary.features.task.application.assistant.AssistantProposals.MealPlansProposal;
import com.autosecretary.features.task.application.assistant.AssistantProposals.PendingProposal;
import com.autosecretary.features.task.application.assistant.AssistantProposals.RecipesProposal;
import com.autosecretary.features.task.application.assistant.AssistantProposals.TaskChangesProposal;
import com.autosecretary.features.task.application.assistant.AssistantProposals.TransactionDraft;
import com.autosecretary.features.task.application.assistant.AssistantProposals.TransactionImportProposal;
import com.autosecretary.features.task.application.internal.budget.AssistantBudgetGateway;
import com.autosecretary.features.task.application.internal.budget.AssistantTransactionImportExecutor;
import com.autosecretary.features.task.application.internal.meal.AssistantMealGateway;
import com.autosecretary.features.task.data.TaskCategoryDao;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.assistant.ChangeOp;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal;
import com.autosecretary.features.task.domain.assistant.TaskSnapshot;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskCategory;
import com.autosecretary.features.task.domain.model.TaskCore;
import com.autosecretary.features.task.domain.model.TaskPrefSlot;
import com.autosecretary.features.task.domain.model.TaskSlot;
import com.autosecretary.shared.ClaudeApiException;
import com.autosecretary.shared.ClaudeApiKeyStore;
import com.autosecretary.shared.ClaudeChatMessages;
import com.autosecretary.shared.ClaudeChatMessages.ToolResult;
import com.autosecretary.shared.ClaudeChatRequest;
import com.autosecretary.shared.ClaudeChatResponse;
import com.autosecretary.shared.ClaudeChatResponse.ToolUse;
import com.autosecretary.shared.ClaudeChatTransport;
import com.autosecretary.shared.ClaudeEndpointStore;
import com.autosecretary.shared.ClaudeModelStore;
import com.autosecretary.shared.MealType;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * The multi-turn chat engine behind the assistant tab. It drives one full Claude Messages exchange
 * per user message: build the user message (optionally with a PDF/txt/md attachment), send the whole
 * conversation with the {@link AssistantTools} catalogue, run any {@code get_*} tool calls against
 * the local DB and park any {@code propose_*} write-intents, and loop until Claude stops.
 *
 * <h2>Threading</h2>
 * The loop runs on the {@code ioExecutor} (network + file work). Every DB read happens on the
 * {@code dbExecutor} via {@link #onDb} ({@code Future.get()}). This does not deadlock: both executors
 * are single-threaded and DB tasks never dispatch back onto the io executor. Results are delivered on
 * the {@code main} executor.
 *
 * <h2>Thinking ladder</h2>
 * Extended thinking format is model-dependent, so the first request for a model tries
 * {@link AssistantConversation.ThinkingMode#ADAPTIVE}; on an HTTP&nbsp;400 that mentions
 * thinking/budget it steps to {@link AssistantConversation.ThinkingMode#ENABLED} (budget tokens) and
 * finally to {@link AssistantConversation.ThinkingMode#NONE}. The surviving mode is cached per model.
 */
public class AssistantChatUseCase {

    // Adaptive thinking shares this budget with the visible answer and the tool_use blocks. 8192 was
    // too small: on a tool-result round the model spent the whole budget inside the thinking block and
    // the turn was truncated (stop_reason=max_tokens) before it emitted any propose_* call — the
    // "announces then stops" bug. All curated models (Sonnet 5, Opus 4.8, Haiku 4.5) allow >=64K output.
    private static final int MAX_TOKENS = 32000;
    private static final int THINKING_BUDGET_TOKENS = 4096;
    private static final int MAX_ITERATIONS = 8;
    private static final int UPCOMING_SLOT_DAYS = 7;

    /** Logcat tag for temporary assistant API diagnostics (DEBUG builds only). */
    private static final String TAG = "AssistantApi";

    private static final String PROPOSAL_PARKED =
            "Vorschlag registriert — wartet auf Bestätigung des Nutzers.";
    private static final String TOO_MANY_STEPS =
            "Der Assistent hat zu viele Schritte gebraucht und wurde abgebrochen.";
    private static final String NO_STATEMENT =
            "Kein Kontoauszug angehängt. Hänge zuerst eine PDF-Datei an.";
    private static final String TRUNCATED_SUFFIX = "\n\n(Antwort gekürzt)";

    // Live-progress labels shown in the pending bubble while the tool loop runs.
    private static final String PROGRESS_THINKING = "Denkt nach…";
    private static final String PROGRESS_PROPOSING = "Erstelle Vorschläge…";
    private static final String PROGRESS_WORKING = "Arbeitet…";

    private static final String SYSTEM_PROMPT =
            "Du bist der Assistent der App AutoSecretary. Du hilfst bei Tasks (To-dos mit Wiederholung, "
            + "Streaks, Terminen), beim Kochen (Rezepte, Zutaten, Wochenpläne) und beim Budget "
            + "(Konten, Kategorien, Transaktionen, Kontoauszüge). "
            + "Nutze die get_*-Tools, um Fragen zu echten Nutzerdaten zu beantworten – rate nie. "
            + "Wenn du Daten prüfen musst, rufe das passende get_*-Tool SOFORT im selben Zug auf – "
            + "kündige es nicht bloß an. Beende deinen Zug NIE mit einer reinen Absichtserklärung "
            + "(z.B. \"ich prüfe zunächst…\"): führe erst die nötigen get_*-Aufrufe aus und erzeuge "
            + "dann die propose_*-Vorschläge. "
            + "Änderungen behauptest du NIE als erledigt: Für jede Änderung rufst du ein propose_*-Tool "
            + "auf; der Nutzer bestätigt oder verwirft den Vorschlag anschließend selbst. "
            + "Bei langen Listen verarbeitest du alle Einträge zusammen in einem einzigen propose_*-Aufruf. "
            + "Antworte immer auf Deutsch und fasse dich klar.";

    /** A file the user attached to a message (PDF, txt or md). */
    public record Attachment(String displayName, String mimeType, byte[] bytes) {
    }

    /** One completed assistant turn: its answer, optional thinking summary, and any parked proposals. */
    public record AssistantTurn(String answerText, String thinkingText, List<PendingProposal> proposals) {
    }

    private final ClaudeChatTransport transport;
    private final AssistantConversation conversation;
    private final TaskDao taskDao;
    private final TaskCategoryDao categoryDao;
    private final AssistantMealGateway mealGateway;
    private final AssistantBudgetGateway budgetGateway;
    private final AssistantTransactionImportExecutor importExecutor;
    private final ClaudeApiKeyStore apiKeyStore;
    private final ClaudeEndpointStore endpointStore;
    private final ClaudeModelStore modelStore;
    private final ExecutorService dbExecutor;
    private final ExecutorService ioExecutor;
    private final Executor main;

    public AssistantChatUseCase(ClaudeChatTransport transport,
                                AssistantConversation conversation,
                                TaskDao taskDao,
                                TaskCategoryDao categoryDao,
                                AssistantMealGateway mealGateway,
                                AssistantBudgetGateway budgetGateway,
                                AssistantTransactionImportExecutor importExecutor,
                                ClaudeApiKeyStore apiKeyStore,
                                ClaudeEndpointStore endpointStore,
                                ClaudeModelStore modelStore,
                                ExecutorService dbExecutor,
                                ExecutorService ioExecutor,
                                Executor main) {
        this.transport = transport;
        this.conversation = conversation;
        this.taskDao = taskDao;
        this.categoryDao = categoryDao;
        this.mealGateway = mealGateway;
        this.budgetGateway = budgetGateway;
        this.importExecutor = importExecutor;
        this.apiKeyStore = apiKeyStore;
        this.endpointStore = endpointStore;
        this.modelStore = modelStore;
        this.dbExecutor = dbExecutor;
        this.ioExecutor = ioExecutor;
        this.main = main;
    }

    public void send(String userText, Attachment attachment, boolean thinkingEnabled,
                     Consumer<AssistantTurn> onResult, Consumer<String> onError, Consumer<String> onProgress) {
        // Marshal every progress tick onto the main thread so the UI can render it directly.
        Consumer<String> progress = text -> main.execute(() -> onProgress.accept(text));
        ioExecutor.execute(() -> {
            try {
                AssistantTurn turn = runConversation(userText, attachment, thinkingEnabled, progress);
                main.execute(() -> onResult.accept(turn));
            } catch (RuntimeException e) {
                main.execute(() -> onError.accept(messageOf(e)));
            }
        });
    }

    public void clearConversation() {
        conversation.clear();
    }

    // ---- Conversation loop -------------------------------------------------

    private AssistantTurn runConversation(String userText, Attachment attachment, boolean thinkingEnabled,
                                          Consumer<String> progress) {
        // On failure, the wire history must return to its pre-turn state: a dangling assistant
        // tool_use without its tool_result makes every subsequent request fail with HTTP 400.
        int mark = conversation.size();
        try {
            return runTurn(userText, attachment, thinkingEnabled, progress);
        } catch (RuntimeException e) {
            conversation.rollbackTo(mark);
            throw e;
        }
    }

    private AssistantTurn runTurn(String userText, Attachment attachment, boolean thinkingEnabled,
                                  Consumer<String> progress) {
        appendUserMessage(userText, attachment);

        String model = modelStore.getModel();
        String apiKey = apiKeyStore.getApiKey();
        String baseUrl = endpointStore.getBaseUrl();
        JSONArray tools = AssistantTools.toolsJson();

        AssistantConversation.ThinkingMode mode = thinkingEnabled ? startingMode(model)
                : AssistantConversation.ThinkingMode.NONE;

        StringBuilder answer = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        List<PendingProposal> proposals = new ArrayList<>();

        int iterations = 0;
        while (true) {
            if (iterations >= MAX_ITERATIONS) {
                throw new ClaudeApiException(TOO_MANY_STEPS);
            }

            progress.accept(PROGRESS_THINKING);
            ClaudeChatRequest request = new ClaudeChatRequest(baseUrl, apiKey, model, MAX_TOKENS,
                    SYSTEM_PROMPT, conversation.messagesArray(), tools, thinkingJson(mode));

            ClaudeChatResponse response;
            try {
                // Stream the round's thinking straight into the progress channel so the pending bubble
                // shows the model's reasoning live instead of a static "Denkt nach…".
                response = transport.chat(request, progress);
            } catch (ClaudeApiException e) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "round " + (iterations + 1) + " model=" + model + " thinking=" + mode
                            + " ERROR status=" + e.statusCode() + " msg=" + e.getMessage());
                }
                if (thinkingEnabled && isThinkingError(e) && mode != AssistantConversation.ThinkingMode.NONE) {
                    mode = stepDown(mode);
                    continue; // retry with a downgraded thinking format; not counted as an iteration
                }
                throw e;
            }
            iterations++;
            if (thinkingEnabled) {
                conversation.cacheWorkingThinking(model, mode);
            }

            append(thinking, response.thinkingText());
            append(answer, response.text());
            conversation.appendMessage(ClaudeChatMessages.assistant(response.rawAssistantContent()));

            String stopReason = response.stopReason();
            logRound(model, mode, iterations, response);
            if ("tool_use".equals(stopReason)) {
                List<ToolResult> results = new ArrayList<>();
                for (ToolUse toolUse : response.toolUses()) {
                    progress.accept(progressLabel(toolUse.name()));
                    results.add(runTool(toolUse, proposals));
                }
                conversation.appendMessage(ClaudeChatMessages.toolResults(results));
                continue;
            }
            if ("pause_turn".equals(stopReason)) {
                continue; // server paused a long turn; re-send to resume
            }
            if ("max_tokens".equals(stopReason)) {
                answer.append(TRUNCATED_SUFFIX);
            }
            break;
        }

        return new AssistantTurn(answer.toString(), thinking.toString(), proposals);
    }

    /**
     * DEBUG-only ground-truth logging for diagnosing "announces intent then stops". Reports the real
     * {@code stop_reason}, the content-block {@code type}s Claude actually returned, the parsed tool
     * names, and the thinking mode sent — so a {@code tool_use} block the parser dropped (empty
     * {@code toolUses} while {@code blocks} contains {@code tool_use}) is distinguishable from the
     * model genuinely returning text only.
     */
    private static void logRound(String model, AssistantConversation.ThinkingMode mode, int round,
                                 ClaudeChatResponse response) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        List<String> toolNames = new ArrayList<>();
        for (ToolUse toolUse : response.toolUses()) {
            toolNames.add(toolUse.name());
        }
        Log.d(TAG, "round " + round + " model=" + model + " thinking=" + mode
                + " stop_reason=" + response.stopReason()
                + " blocks=" + blockTypes(response.rawAssistantContent())
                + " toolUses=" + toolNames
                + " textLen=" + response.text().length());
    }

    /** The ordered content-block {@code type} values of a raw assistant message, for diagnostics. */
    private static List<String> blockTypes(JSONArray content) {
        List<String> types = new ArrayList<>();
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                JSONObject block = content.optJSONObject(i);
                types.add(block != null ? block.optString("type", "?") : "null");
            }
        }
        return types;
    }

    private void appendUserMessage(String userText, Attachment attachment) {
        if (attachment == null) {
            conversation.appendMessage(ClaudeChatMessages.userText(userText));
            return;
        }
        if (isPdf(attachment)) {
            conversation.rememberStatement(attachment.displayName(), sha256Hex(attachment.bytes()));
        }
        conversation.appendMessage(ClaudeChatMessages.userWithAttachment(
                userText, attachment.displayName(), attachment.mimeType(), attachment.bytes()));
    }

    private static boolean isPdf(Attachment attachment) {
        String mime = attachment.mimeType();
        String name = attachment.displayName();
        return "application/pdf".equalsIgnoreCase(mime)
                || (name != null && name.toLowerCase(Locale.ROOT).endsWith(".pdf"));
    }

    // ---- Thinking ladder ---------------------------------------------------

    private AssistantConversation.ThinkingMode startingMode(String model) {
        AssistantConversation.ThinkingMode cached = conversation.workingThinking(model);
        return cached != null ? cached : AssistantConversation.ThinkingMode.ADAPTIVE;
    }

    private static AssistantConversation.ThinkingMode stepDown(AssistantConversation.ThinkingMode mode) {
        return switch (mode) {
            case ADAPTIVE -> AssistantConversation.ThinkingMode.ENABLED;
            case ENABLED, NONE -> AssistantConversation.ThinkingMode.NONE;
        };
    }

    private static JSONObject thinkingJson(AssistantConversation.ThinkingMode mode) {
        try {
            return switch (mode) {
                case ADAPTIVE -> new JSONObject().put("type", "adaptive").put("display", "summarized");
                case ENABLED -> new JSONObject().put("type", "enabled").put("budget_tokens", THINKING_BUDGET_TOKENS);
                case NONE -> null;
            };
        } catch (JSONException e) {
            throw new ClaudeApiException("Thinking-Konfiguration ungültig: " + e.getMessage(), e);
        }
    }

    private static boolean isThinkingError(ClaudeApiException e) {
        if (e.statusCode() != 400) {
            return false;
        }
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("thinking") || message.contains("budget") || message.contains("adaptive");
    }

    // ---- Tool dispatch -----------------------------------------------------

    /** German progress label shown while a tool runs, so the user sees what the agent is doing. */
    private static String progressLabel(String toolName) {
        return switch (toolName) {
            case AssistantTools.GET_TASKS -> "Prüfe vorhandene Tasks…";
            case AssistantTools.GET_RECIPES -> "Prüfe Rezepte…";
            case AssistantTools.GET_INGREDIENTS -> "Prüfe Zutaten…";
            case AssistantTools.GET_MEAL_PLANS -> "Prüfe Wochenpläne…";
            case AssistantTools.GET_BUDGET_CONTEXT -> "Prüfe Budget…";
            case AssistantTools.GET_TRANSACTIONS -> "Prüfe Transaktionen…";
            case AssistantTools.PROPOSE_TASK_CHANGES, AssistantTools.PROPOSE_RECIPES,
                 AssistantTools.PROPOSE_INGREDIENTS, AssistantTools.PROPOSE_MEAL_PLANS,
                 AssistantTools.PROPOSE_TRANSACTION_IMPORT -> PROGRESS_PROPOSING;
            default -> PROGRESS_WORKING;
        };
    }

    private ToolResult runTool(ToolUse toolUse, List<PendingProposal> sink) {
        try {
            JSONObject input = toolUse.input();
            return switch (toolUse.name()) {
                case AssistantTools.GET_TASKS -> ok(toolUse, toolGetTasks(input));
                case AssistantTools.GET_RECIPES -> ok(toolUse, toolGetRecipes());
                case AssistantTools.GET_INGREDIENTS -> ok(toolUse, toolGetIngredients());
                case AssistantTools.GET_MEAL_PLANS -> ok(toolUse, toolGetMealPlans(input));
                case AssistantTools.GET_BUDGET_CONTEXT -> ok(toolUse, toolGetBudgetContext());
                case AssistantTools.GET_TRANSACTIONS -> ok(toolUse, toolGetTransactions(input));
                case AssistantTools.PROPOSE_TASK_CHANGES -> park(toolUse, sink, proposeTaskChanges(input));
                case AssistantTools.PROPOSE_RECIPES -> park(toolUse, sink, proposeRecipes(input));
                case AssistantTools.PROPOSE_INGREDIENTS -> park(toolUse, sink, proposeIngredients(input));
                case AssistantTools.PROPOSE_MEAL_PLANS -> park(toolUse, sink, proposeMealPlans(input));
                case AssistantTools.PROPOSE_TRANSACTION_IMPORT -> park(toolUse, sink, proposeTransactionImport(input));
                default -> error(toolUse, "Unbekanntes Tool: " + toolUse.name());
            };
        } catch (IllegalArgumentException e) {
            return error(toolUse, e.getMessage());
        } catch (RuntimeException e) {
            return error(toolUse, "Fehler bei " + toolUse.name() + ": " + messageOf(e));
        }
    }

    private static ToolResult ok(ToolUse toolUse, String content) {
        return new ToolResult(toolUse.id(), content, false);
    }

    private static ToolResult park(ToolUse toolUse, List<PendingProposal> sink, PendingProposal proposal) {
        sink.add(proposal);
        return new ToolResult(toolUse.id(), PROPOSAL_PARKED, false);
    }

    private static ToolResult error(ToolUse toolUse, String message) {
        return new ToolResult(toolUse.id(), message != null ? message : "Unbekannter Fehler", true);
    }

    // ---- READ tools --------------------------------------------------------

    private String toolGetTasks(JSONObject input) {
        String titleContains = optString(input, "titleContains");
        String categoryName = optString(input, "categoryName");
        List<Task> tasks = onDb(taskDao::readAll);
        List<TaskCategory> categories = onDb(categoryDao::readAll);
        try {
            Map<String, String> categoryNameById = new HashMap<>();
            JSONArray categoriesJson = new JSONArray();
            for (TaskCategory category : categories) {
                categoryNameById.put(category.id, category.name);
                categoriesJson.put(new JSONObject().put("id", category.id).put("name", category.name));
            }
            String filterCategoryId = categoryName == null ? null
                    : categoryIdForName(categories, categoryName);
            String titleNeedle = titleContains == null ? null : titleContains.toLowerCase(Locale.ROOT);

            JSONArray tasksJson = new JSONArray();
            LocalDate today = LocalDate.now();
            for (Task task : tasks) {
                if (filterCategoryId != null && !filterCategoryId.equals(task.core.categoryId)) {
                    continue;
                }
                if (titleNeedle != null && (task.core.title == null
                        || !task.core.title.toLowerCase(Locale.ROOT).contains(titleNeedle))) {
                    continue;
                }
                tasksJson.put(taskJson(task, categoryNameById, today));
            }
            return new JSONObject().put("categories", categoriesJson).put("tasks", tasksJson).toString();
        } catch (JSONException e) {
            throw new ClaudeApiException("Task-Daten konnten nicht serialisiert werden: " + e.getMessage(), e);
        }
    }

    private static String categoryIdForName(List<TaskCategory> categories, String name) {
        String needle = name.trim().toLowerCase(Locale.ROOT);
        for (TaskCategory category : categories) {
            if (category.name != null && category.name.trim().toLowerCase(Locale.ROOT).equals(needle)) {
                return category.id;
            }
        }
        return " no-such-category"; // matches nothing → empty result rather than "all tasks"
    }

    private JSONObject taskJson(Task task, Map<String, String> categoryNameById, LocalDate today)
            throws JSONException {
        TaskCore core = task.core;
        JSONObject json = new JSONObject();
        json.put("id", core.id);
        json.put("title", core.title);
        json.put("description", core.description);
        json.put("categoryName", core.categoryId != null ? categoryNameById.get(core.categoryId) : null);
        json.put("priority", core.priority != null ? core.priority.name() : null);
        json.put("leisure", core.leisure);
        json.put("completed", core.completed);
        json.put("schedulingType", core.schedulingType != null ? core.schedulingType.name() : null);
        json.put("fixedDate", asString(core.fixedDate));
        json.put("fixedStart", asString(core.fixedStart));
        json.put("fixedEnd", asString(core.fixedEnd));
        json.put("fixedDuration", core.fixedDuration);
        json.put("startDate", asString(core.startDate));
        json.put("deadline", asString(core.deadline));
        json.put("cooldown", core.cooldown);
        json.put("minDuration", core.minDuration);
        json.put("maxDuration", core.maxDuration);
        json.put("closeOnMiss", core.closeOnMiss);
        json.put("adaptive", core.adaptive);
        json.put("budgetRequiredCents", core.budgetRequiredCents);
        json.put("budgetAccountId", core.budgetAccountId);
        json.put("budgetCategoryId", core.budgetCategoryId);

        TaskCore.Repetition rep = core.repetition;
        json.put("repetition", new JSONObject()
                .put("reps", rep.reps)
                .put("perPeriod", rep.perPeriod)
                .put("periodUnit", rep.periodUnit != null ? rep.periodUnit.name() : null)
                .put("periodCompletions", rep.periodCompletions)
                .put("carryoverDebt", rep.carryoverDebt)
                .put("completeFirst", rep.completeFirst)
                .put("periodStart", asString(rep.periodStart)));

        TaskCore.Progress progress = core.progress;
        json.put("progress", new JSONObject()
                .put("unit", progress.unit)
                .put("target", progress.target)
                .put("current", progress.current)
                .put("resetPerRep", progress.resetPerRep)
                .put("minPerRep", progress.minPerRep)
                .put("maxPerRep", progress.maxPerRep));

        TaskCore.History history = core.history;
        json.put("history", new JSONObject()
                .put("completions", history.completions)
                .put("currentStreak", history.currentStreak)
                .put("averageDurationMinutes", history.averageDuration()));

        JSONArray prefSlots = new JSONArray();
        for (TaskPrefSlot prefSlot : task.prefSlots) {
            prefSlots.put(new JSONObject()
                    .put("days", daysJson(prefSlot.days))
                    .put("start", asString(prefSlot.start)));
        }
        json.put("prefSlots", prefSlots);

        JSONArray upcoming = new JSONArray();
        LocalDate horizon = today.plusDays(UPCOMING_SLOT_DAYS);
        for (TaskSlot slot : task.slots) {
            if (slot.day == null || slot.day.isBefore(today) || slot.day.isAfter(horizon)) {
                continue;
            }
            upcoming.put(new JSONObject()
                    .put("day", asString(slot.day))
                    .put("start", asString(slot.start))
                    .put("end", asString(slot.end))
                    .put("completed", slot.completed));
        }
        json.put("upcomingSlots", upcoming);
        return json;
    }

    private static JSONArray daysJson(Set<DayOfWeek> days) {
        JSONArray array = new JSONArray();
        if (days != null) {
            for (DayOfWeek day : days) {
                array.put(day.name());
            }
        }
        return array;
    }

    private String toolGetRecipes() {
        List<Recipe> recipes = onDb(mealGateway::recipes);
        try {
            JSONArray array = new JSONArray();
            for (Recipe recipe : recipes) {
                JSONArray ingredients = new JSONArray();
                if (recipe.ingredients != null) {
                    for (Recipe.RecipeIngredient ingredient : recipe.ingredients) {
                        ingredients.put(new JSONObject()
                                .put("name", ingredient.ingredientName())
                                .put("amount", ingredient.amount())
                                .put("unit", ingredient.unit()));
                    }
                }
                array.put(new JSONObject()
                        .put("id", recipe.id)
                        .put("title", recipe.title)
                        .put("description", recipe.description)
                        .put("mealTypes", mealTypesJson(recipe.mealTypes))
                        .put("servings", recipe.servings)
                        .put("totalCalories", recipe.totalCalories)
                        .put("tags", recipe.tags)
                        .put("ingredients", ingredients));
            }
            return new JSONObject().put("recipes", array).toString();
        } catch (JSONException e) {
            throw new ClaudeApiException("Rezepte konnten nicht serialisiert werden: " + e.getMessage(), e);
        }
    }

    private static JSONArray mealTypesJson(Set<MealType> mealTypes) {
        JSONArray array = new JSONArray();
        if (mealTypes != null) {
            for (MealType mealType : mealTypes) {
                array.put(mealType.name());
            }
        }
        return array;
    }

    private String toolGetIngredients() {
        List<Ingredient> ingredients = onDb(mealGateway::ingredients);
        try {
            JSONArray array = new JSONArray();
            for (Ingredient ingredient : ingredients) {
                array.put(new JSONObject()
                        .put("id", ingredient.id)
                        .put("name", ingredient.name)
                        .put("foodGroup", ingredient.foodGroup != null ? ingredient.foodGroup.name() : null)
                        .put("unit", ingredient.defaultUnit)
                        .put("caloriesPer100", ingredient.caloriesPer100)
                        .put("proteinPer100", ingredient.proteinPer100)
                        .put("carbsPer100", ingredient.carbsPer100)
                        .put("fatPer100", ingredient.fatPer100)
                        .put("fiberPer100", ingredient.fiberPer100));
            }
            return new JSONObject().put("ingredients", array).toString();
        } catch (JSONException e) {
            throw new ClaudeApiException("Zutaten konnten nicht serialisiert werden: " + e.getMessage(), e);
        }
    }

    private String toolGetMealPlans(JSONObject input) {
        LocalDate from = requireDate(input, "from");
        LocalDate to = requireDate(input, "to");
        List<MealPlan> plans = onDb(() -> mealGateway.mealPlans(from, to));
        try {
            JSONArray array = new JSONArray();
            for (MealPlan plan : plans) {
                array.put(new JSONObject()
                        .put("date", asString(plan.date))
                        .put("mealType", plan.mealType != null ? plan.mealType.name() : null)
                        .put("recipeTitle", plan.recipeTitle)
                        .put("servings", plan.plannedServings)
                        .put("completed", plan.isCompleted)
                        .put("kcal", plan.estimatedCalories));
            }
            return new JSONObject().put("mealPlans", array).toString();
        } catch (JSONException e) {
            throw new ClaudeApiException("Wochenplan konnte nicht serialisiert werden: " + e.getMessage(), e);
        }
    }

    private String toolGetBudgetContext() {
        AssistantBudgetGateway.BudgetContext context = onDb(budgetGateway::accountsContext);
        try {
            JSONArray accounts = new JSONArray();
            for (AssistantBudgetGateway.AccountInfo account : context.accounts()) {
                accounts.put(new JSONObject()
                        .put("id", account.id())
                        .put("name", account.name())
                        .put("balanceCents", account.balanceCents()));
            }
            JSONArray categories = new JSONArray();
            for (AssistantBudgetGateway.CategoryInfo category : context.categories()) {
                categories.put(new JSONObject()
                        .put("id", category.id())
                        .put("name", category.name())
                        .put("direction", category.direction()));
            }
            return new JSONObject().put("accounts", accounts).put("categories", categories).toString();
        } catch (JSONException e) {
            throw new ClaudeApiException("Budget-Kontext konnte nicht serialisiert werden: " + e.getMessage(), e);
        }
    }

    private String toolGetTransactions(JSONObject input) {
        LocalDate from = requireDate(input, "from");
        LocalDate to = requireDate(input, "to");
        String accountId = optString(input, "accountId");
        List<AssistantBudgetGateway.TransactionInfo> transactions =
                onDb(() -> budgetGateway.transactions(from, to, accountId));
        try {
            JSONArray array = new JSONArray();
            for (AssistantBudgetGateway.TransactionInfo tx : transactions) {
                array.put(new JSONObject()
                        .put("date", asString(tx.date()))
                        .put("amountCents", tx.amountCents())
                        .put("direction", tx.direction())
                        .put("payee", tx.payee())
                        .put("note", tx.note())
                        .put("categoryName", tx.categoryName()));
            }
            return new JSONObject().put("transactions", array).toString();
        } catch (JSONException e) {
            throw new ClaudeApiException("Transaktionen konnten nicht serialisiert werden: " + e.getMessage(), e);
        }
    }

    // ---- PROPOSAL tools ----------------------------------------------------

    private PendingProposal proposeTaskChanges(JSONObject input) {
        TaskAssistantProposal proposal = new TaskAssistantProposal(
                parseCategoryChanges(input.optJSONArray("categories")),
                parseTaskChanges(input.optJSONArray("tasks")));

        List<Task> tasks = onDb(taskDao::readAll);
        List<TaskCategory> categories = onDb(categoryDao::readAll);
        Set<String> taskIds = new HashSet<>();
        List<TaskSnapshot.TaskInfo> taskInfos = new ArrayList<>();
        for (Task task : tasks) {
            taskIds.add(task.core.id);
            taskInfos.add(new TaskSnapshot.TaskInfo(task.core.id, task.core.title, task.core.categoryId,
                    task.core.priority != null ? task.core.priority.name() : null, task.core.leisure));
        }
        Set<String> categoryIds = new HashSet<>();
        List<TaskSnapshot.CategoryInfo> categoryInfos = new ArrayList<>();
        for (TaskCategory category : categories) {
            categoryIds.add(category.id);
            categoryInfos.add(new TaskSnapshot.CategoryInfo(
                    category.id, category.name, category.icon, category.colorHex));
        }
        validateTaskProposal(proposal, taskIds, categoryIds);
        return new TaskChangesProposal(new TaskSnapshot(categoryInfos, taskInfos), proposal);
    }

    private static void validateTaskProposal(TaskAssistantProposal proposal,
                                             Set<String> taskIds, Set<String> categoryIds) {
        for (TaskAssistantProposal.CategoryChange change : proposal.categoryChanges()) {
            if (change.op() == ChangeOp.CREATE) {
                if (change.name() == null) {
                    throw new IllegalArgumentException("Neue Kategorie ohne Namen im Vorschlag.");
                }
            } else if (change.id() == null || !categoryIds.contains(change.id())) {
                throw new IllegalArgumentException("Unbekannte Kategorie-id im Vorschlag: " + change.id());
            }
        }
        for (TaskAssistantProposal.TaskChange change : proposal.taskChanges()) {
            if (change.op() == ChangeOp.CREATE) {
                if (change.title() == null) {
                    throw new IllegalArgumentException("Neue Task ohne Titel im Vorschlag.");
                }
                if (change.schedulingType() == TaskCore.SchedulingType.TERMIN && change.fixedDate() == null) {
                    throw new IllegalArgumentException(
                            "Neuer Termin ohne fixedDate im Vorschlag: " + change.title());
                }
            } else if (change.id() == null || !taskIds.contains(change.id())) {
                throw new IllegalArgumentException("Unbekannte Task-id im Vorschlag: " + change.id());
            }
        }
    }

    private List<TaskAssistantProposal.CategoryChange> parseCategoryChanges(JSONArray array) {
        List<TaskAssistantProposal.CategoryChange> changes = new ArrayList<>();
        if (array == null) {
            return changes;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.optJSONObject(i);
            ChangeOp op = parseOp(entry);
            if (op == null) {
                continue;
            }
            changes.add(new TaskAssistantProposal.CategoryChange(op,
                    optString(entry, "id"), optString(entry, "name"),
                    optString(entry, "icon"), optString(entry, "colorHex")));
        }
        return changes;
    }

    private List<TaskAssistantProposal.TaskChange> parseTaskChanges(JSONArray array) {
        List<TaskAssistantProposal.TaskChange> changes = new ArrayList<>();
        if (array == null) {
            return changes;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.optJSONObject(i);
            ChangeOp op = parseOp(entry);
            if (op == null) {
                continue;
            }
            changes.add(new TaskAssistantProposal.TaskChange(op,
                    optString(entry, "id"), optString(entry, "title"), optString(entry, "description"),
                    optString(entry, "categoryId"), parsePriority(optString(entry, "priority")),
                    optBool(entry, "leisure"), optBool(entry, "adaptive"),
                    optInt(entry, "minDuration"), optInt(entry, "maxDuration"), optInt(entry, "cooldown"),
                    parseSchedulingType(optString(entry, "schedulingType")),
                    optDate(entry, "startDate"), optDate(entry, "deadline"), optBool(entry, "closeOnMiss"),
                    optDate(entry, "fixedDate"), optTime(entry, "fixedStart"), optTime(entry, "fixedEnd"),
                    optInt(entry, "fixedDuration"),
                    parseRepetition(entry.optJSONObject("repetition")),
                    parseProgress(entry.optJSONObject("progress")),
                    optInt(entry, "budgetRequiredCents"), optString(entry, "budgetAccountId"),
                    optString(entry, "budgetCategoryId"),
                    parsePrefSlots(entry.optJSONArray("prefSlots"))));
        }
        return changes;
    }

    private static TaskAssistantProposal.RepetitionChange parseRepetition(JSONObject obj) {
        if (obj == null) {
            return null;
        }
        return new TaskAssistantProposal.RepetitionChange(optInt(obj, "reps"), optInt(obj, "perPeriod"),
                parsePeriod(optString(obj, "periodUnit")), optBool(obj, "completeFirst"));
    }

    private static TaskAssistantProposal.ProgressChange parseProgress(JSONObject obj) {
        if (obj == null) {
            return null;
        }
        return new TaskAssistantProposal.ProgressChange(optInt(obj, "target"), optInt(obj, "current"),
                optString(obj, "unit"), optBool(obj, "resetPerRep"),
                optInt(obj, "minPerRep"), optInt(obj, "maxPerRep"));
    }

    private static List<TaskAssistantProposal.PrefSlotChange> parsePrefSlots(JSONArray array) {
        if (array == null) {
            return null;
        }
        List<TaskAssistantProposal.PrefSlotChange> slots = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            slots.add(new TaskAssistantProposal.PrefSlotChange(
                    parseDays(entry.optJSONArray("days")), optTime(entry, "start")));
        }
        return slots;
    }

    /** Accepts ISO day numbers (1–7, Mon=1) or day names ("MONDAY") — the read side emits names. */
    private static Set<DayOfWeek> parseDays(JSONArray array) {
        if (array == null) {
            return null;
        }
        Set<DayOfWeek> days = new LinkedHashSet<>();
        for (int i = 0; i < array.length(); i++) {
            DayOfWeek day = parseDay(array.opt(i));
            if (day != null) {
                days.add(day);
            }
        }
        return days;
    }

    private static DayOfWeek parseDay(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            int n = number.intValue();
            return (n >= 1 && n <= 7) ? DayOfWeek.of(n) : null;
        }
        String raw = value.toString().trim();
        try {
            return DayOfWeek.of(Integer.parseInt(raw)); // "1".."7"
        } catch (NumberFormatException notANumber) {
            try {
                return DayOfWeek.valueOf(raw.toUpperCase(Locale.ROOT)); // "MONDAY"
            } catch (IllegalArgumentException notADay) {
                return null;
            }
        }
    }

    private static Period parsePeriod(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Period.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static TaskCore.SchedulingType parseSchedulingType(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return TaskCore.SchedulingType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer optInt(JSONObject entry, String key) {
        if (entry == null || !entry.has(key) || entry.isNull(key)) {
            return null;
        }
        return entry.optInt(key);
    }

    private static Boolean optBool(JSONObject entry, String key) {
        if (entry == null || !entry.has(key) || entry.isNull(key)) {
            return null;
        }
        return entry.optBoolean(key);
    }

    private static LocalTime optTime(JSONObject entry, String key) {
        String raw = optString(entry, key);
        if (raw == null) {
            return null;
        }
        try {
            return LocalTime.parse(raw);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Ungültige Uhrzeit in \"" + key + "\": " + raw);
        }
    }

    private static ChangeOp parseOp(JSONObject entry) {
        String raw = optString(entry, "op");
        if (raw == null) {
            return null;
        }
        try {
            return ChangeOp.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Priority parsePriority(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Priority.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private PendingProposal proposeRecipes(JSONObject input) {
        JSONArray recipesJson = requireArray(input, "recipes");
        Map<String, String> ingredientIdsByName = onDb(() -> {
            Map<String, String> map = new HashMap<>();
            for (Ingredient ingredient : mealGateway.ingredients()) {
                if (ingredient.name != null) {
                    map.put(ingredient.name.trim().toLowerCase(Locale.ROOT), ingredient.id);
                }
            }
            return map;
        });

        List<Recipe> recipes = new ArrayList<>();
        for (int i = 0; i < recipesJson.length(); i++) {
            JSONObject entry = recipesJson.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            String title = optString(entry, "title");
            if (title == null) {
                throw new IllegalArgumentException("Rezept ohne Titel im Vorschlag.");
            }
            Recipe.Builder builder = new Recipe.Builder(title)
                    .description(optString(entry, "description"))
                    .instructions(optString(entry, "instructions"))
                    .prepTime(entry.optInt("prepTimeMinutes", 0))
                    .cookTime(entry.optInt("cookTimeMinutes", 0))
                    .tags(optString(entry, "tags"));
            if (entry.has("servings")) {
                builder.servings(entry.optInt("servings"));
            }
            for (MealType mealType : parseMealTypes(entry.optJSONArray("mealTypes"))) {
                builder.mealType(mealType);
            }
            JSONArray ingredientsJson = entry.optJSONArray("ingredients");
            if (ingredientsJson != null) {
                for (int j = 0; j < ingredientsJson.length(); j++) {
                    JSONObject ingredient = ingredientsJson.optJSONObject(j);
                    if (ingredient == null) {
                        continue;
                    }
                    String name = optString(ingredient, "name");
                    if (name == null) {
                        continue;
                    }
                    String ingredientId = ingredientIdsByName.get(name.trim().toLowerCase(Locale.ROOT));
                    builder.ingredient(ingredientId, name, ingredient.optDouble("amount", 0),
                            optString(ingredient, "unit"));
                }
            }
            recipes.add(builder.build());
        }
        if (recipes.isEmpty()) {
            throw new IllegalArgumentException("Der Vorschlag enthält keine Rezepte.");
        }
        return new RecipesProposal(recipes);
    }

    private PendingProposal proposeIngredients(JSONObject input) {
        JSONArray ingredientsJson = requireArray(input, "ingredients");
        List<Ingredient> ingredients = new ArrayList<>();
        for (int i = 0; i < ingredientsJson.length(); i++) {
            JSONObject entry = ingredientsJson.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            String name = optString(entry, "name");
            if (name == null) {
                throw new IllegalArgumentException("Zutat ohne Namen im Vorschlag.");
            }
            Ingredient.Builder builder = new Ingredient.Builder(name, parseFoodGroup(optString(entry, "foodGroup")))
                    .calories(entry.optInt("caloriesPer100", 0))
                    .protein(entry.optInt("proteinPer100", 0))
                    .carbs(entry.optInt("carbsPer100", 0))
                    .fat(entry.optInt("fatPer100", 0))
                    .fiber(entry.optInt("fiberPer100", 0))
                    .shelfLife(entry.optInt("shelfLifeDays", 0));
            String unit = optString(entry, "unit");
            if (unit != null) {
                builder.unit(unit, entry.optInt("gramsPerUnit", 1));
            }
            ingredients.add(builder.build());
        }
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("Der Vorschlag enthält keine Zutaten.");
        }
        return new IngredientsProposal(ingredients);
    }

    private static Ingredient.FoodGroup parseFoodGroup(String raw) {
        if (raw == null) {
            return Ingredient.FoodGroup.OTHER;
        }
        try {
            return Ingredient.FoodGroup.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unbekannte foodGroup: " + raw);
        }
    }

    private PendingProposal proposeMealPlans(JSONObject input) {
        JSONArray entriesJson = requireArray(input, "entries");
        List<MealPlanDraft> entries = new ArrayList<>();
        for (int i = 0; i < entriesJson.length(); i++) {
            JSONObject entry = entriesJson.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            LocalDate date = requireDate(entry, "date");
            MealType mealType = parseMealType(optString(entry, "mealType"));
            String recipeId = optString(entry, "recipeId");
            String recipeTitle = optString(entry, "recipeTitle");
            if (recipeId == null && recipeTitle == null) {
                throw new IllegalArgumentException("Wochenplan-Eintrag ohne recipeId/recipeTitle.");
            }
            entries.add(new MealPlanDraft(date, mealType, recipeId, recipeTitle, entry.optInt("servings", 0)));
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Der Vorschlag enthält keine Wochenplan-Einträge.");
        }
        return new MealPlansProposal(entries);
    }

    private PendingProposal proposeTransactionImport(JSONObject input) {
        AssistantConversation.Statement statement = conversation.currentStatement();
        if (statement == null) {
            throw new IllegalArgumentException(NO_STATEMENT);
        }
        JSONArray transactionsJson = requireArray(input, "transactions");
        List<TransactionDraft> drafts = new ArrayList<>();
        for (int i = 0; i < transactionsJson.length(); i++) {
            JSONObject entry = transactionsJson.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            drafts.add(new TransactionDraft(
                    requireDate(entry, "date"),
                    entry.optLong("amountCents"),
                    optString(entry, "payee"),
                    optString(entry, "description"),
                    optString(entry, "categoryId")));
        }
        if (drafts.isEmpty()) {
            throw new IllegalArgumentException("Der Kontoauszug enthält keine Transaktionen.");
        }
        String accountId = optString(input, "accountId");
        LocalDate periodStart = optDate(input, "periodStart");
        LocalDate periodEnd = optDate(input, "periodEnd");
        int duplicates = onDb(() -> importExecutor.duplicatePreview(accountId, drafts));
        return new TransactionImportProposal(accountId, statement.fileName(), statement.fileHash(),
                periodStart, periodEnd, drafts, duplicates);
    }

    private Set<MealType> parseMealTypes(JSONArray array) {
        Set<MealType> mealTypes = new HashSet<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String raw = array.optString(i, null);
                if (raw != null && !raw.isBlank()) {
                    mealTypes.add(parseMealType(raw));
                }
            }
        }
        return mealTypes;
    }

    private static MealType parseMealType(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("mealType fehlt.");
        }
        try {
            return MealType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unbekannter mealType: " + raw);
        }
    }

    // ---- Helpers -----------------------------------------------------------

    private <T> T onDb(Callable<T> task) {
        try {
            return dbExecutor.submit(task).get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ClaudeApiException("Datenbankfehler: " + messageOf(cause), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClaudeApiException("Datenbankzugriff unterbrochen", e);
        }
    }

    private static void append(StringBuilder builder, String part) {
        if (part != null && !part.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(part);
        }
    }

    private static String optString(JSONObject entry, String key) {
        if (entry == null || !entry.has(key) || entry.isNull(key)) {
            return null;
        }
        String value = entry.optString(key, null);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static JSONArray requireArray(JSONObject input, String key) {
        JSONArray array = input == null ? null : input.optJSONArray(key);
        if (array == null) {
            throw new IllegalArgumentException("Feld \"" + key + "\" fehlt oder ist kein Array.");
        }
        return array;
    }

    private static LocalDate requireDate(JSONObject input, String key) {
        LocalDate date = optDate(input, key);
        if (date == null) {
            throw new IllegalArgumentException("Feld \"" + key + "\" fehlt oder ist kein Datum (YYYY-MM-DD).");
        }
        return date;
    }

    private static LocalDate optDate(JSONObject input, String key) {
        String raw = optString(input, key);
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Ungültiges Datum in \"" + key + "\": " + raw);
        }
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static String messageOf(Throwable throwable) {
        if (throwable == null) {
            return "Unbekannter Fehler";
        }
        return throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }
}
