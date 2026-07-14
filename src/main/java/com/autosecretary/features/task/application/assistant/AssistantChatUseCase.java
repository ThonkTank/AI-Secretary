package com.autosecretary.features.task.application.assistant;

import android.util.Log;

import com.autosecretary.BuildConfig;
import com.autosecretary.features.task.application.assistant.AssistantProposals.PendingProposal;
import com.autosecretary.features.task.application.assistant.internal.AssistantTool;
import com.autosecretary.features.task.application.assistant.internal.AssistantTool.ToolOutcome;
import com.autosecretary.features.task.application.assistant.internal.AssistantToolRegistry;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * The multi-turn chat engine behind the assistant tab. It drives one full Claude Messages exchange
 * per user message: build the user message (optionally with a PDF/txt/md attachment), send the whole
 * conversation with the {@link AssistantToolRegistry} catalogue, dispatch each tool call to its
 * handler (a {@code get_*} read returns data; a {@code propose_*} write parks a {@link PendingProposal}),
 * and loop until Claude stops. The tool handlers own all serialization and parsing; this class only
 * orchestrates the loop.
 *
 * <h2>Threading</h2>
 * The loop runs on the {@code ioExecutor} (network + file work). Tool handlers hop DB reads onto the
 * db executor themselves. Results are delivered on the {@code main} executor.
 *
 * <h2>Thinking ladder</h2>
 * Extended thinking format is model-dependent, so the first request for a model tries
 * {@link AssistantConversation.ThinkingMode#ADAPTIVE}; on an HTTP&nbsp;400 that mentions
 * thinking/budget it steps to {@link AssistantConversation.ThinkingMode#ENABLED} (budget tokens) and
 * finally to {@link AssistantConversation.ThinkingMode#NONE}. The surviving mode is cached per model.
 *
 * <h2>Failure rollback</h2>
 * Each turn appends a user message and then assistant/tool messages as it loops. On any failure the
 * conversation is truncated back to its pre-turn size, so a dangling assistant {@code tool_use}
 * (whose {@code tool_result} never arrived) cannot poison the next request with an HTTP&nbsp;400.
 */
public class AssistantChatUseCase {

    // Adaptive thinking shares this budget with the visible answer and the tool_use blocks. 8192 was
    // too small: on a tool-result round the model spent the whole budget inside the thinking block and
    // the turn was truncated (stop_reason=max_tokens) before it emitted any propose_* call — the
    // "announces then stops" bug. All curated models (Sonnet 5, Opus 4.8, Haiku 4.5) allow >=64K output.
    private static final int MAX_TOKENS = 32000;
    private static final int THINKING_BUDGET_TOKENS = 4096;
    private static final int MAX_ITERATIONS = 8;

    /** Logcat tag for temporary assistant API diagnostics (DEBUG builds only). */
    private static final String TAG = "AssistantApi";

    private static final String PROPOSAL_PARKED =
            "Vorschlag registriert — wartet auf Bestätigung des Nutzers.";
    private static final String TOO_MANY_STEPS =
            "Der Assistent hat zu viele Schritte gebraucht und wurde abgebrochen.";
    private static final String TRUNCATED_SUFFIX = "\n\n(Antwort gekürzt)";

    // Live-progress labels shown in the pending bubble while the tool loop runs.
    private static final String PROGRESS_THINKING = "Denkt nach…";
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
    private final AssistantToolRegistry tools;
    private final ClaudeApiKeyStore apiKeyStore;
    private final ClaudeEndpointStore endpointStore;
    private final ClaudeModelStore modelStore;
    private final ExecutorService ioExecutor;
    private final Executor main;

    public AssistantChatUseCase(ClaudeChatTransport transport,
                                AssistantConversation conversation,
                                AssistantToolRegistry tools,
                                ClaudeApiKeyStore apiKeyStore,
                                ClaudeEndpointStore endpointStore,
                                ClaudeModelStore modelStore,
                                ExecutorService ioExecutor,
                                Executor main) {
        this.transport = transport;
        this.conversation = conversation;
        this.tools = tools;
        this.apiKeyStore = apiKeyStore;
        this.endpointStore = endpointStore;
        this.modelStore = modelStore;
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
        JSONArray toolsJson = tools.toolsJson();

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
                    SYSTEM_PROMPT, conversation.messagesArray(), toolsJson, thinkingJson(mode));

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

    /** German progress label shown while a tool runs; unknown tools fall back to a generic label. */
    private String progressLabel(String toolName) {
        AssistantTool tool = tools.find(toolName);
        return tool != null ? tool.progressLabel() : PROGRESS_WORKING;
    }

    private ToolResult runTool(ToolUse toolUse, List<PendingProposal> sink) {
        AssistantTool tool = tools.find(toolUse.name());
        if (tool == null) {
            return error(toolUse, "Unbekanntes Tool: " + toolUse.name());
        }
        try {
            ToolOutcome outcome = tool.run().apply(toolUse.input());
            if (outcome instanceof ToolOutcome.Json json) {
                return new ToolResult(toolUse.id(), json.content(), false);
            }
            if (outcome instanceof ToolOutcome.Parked parked) {
                sink.add(parked.proposal());
                return new ToolResult(toolUse.id(), PROPOSAL_PARKED, false);
            }
            return error(toolUse, "Unbekanntes Tool-Ergebnis für " + toolUse.name());
        } catch (IllegalArgumentException e) {
            return error(toolUse, e.getMessage());
        } catch (RuntimeException e) {
            return error(toolUse, "Fehler bei " + toolUse.name() + ": " + messageOf(e));
        }
    }

    private static ToolResult error(ToolUse toolUse, String message) {
        return new ToolResult(toolUse.id(), message != null ? message : "Unbekannter Fehler", true);
    }

    // ---- Helpers -----------------------------------------------------------

    private static void append(StringBuilder builder, String part) {
        if (part != null && !part.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(part);
        }
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
