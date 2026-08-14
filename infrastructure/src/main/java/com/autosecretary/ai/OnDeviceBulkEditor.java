package com.autosecretary.ai;

import android.content.Context;
import com.autosecretary.application.ai.AiProposalGateway;
import com.autosecretary.application.TimeProvider;
import com.autosecretary.application.model.ModelStatus;
import com.autosecretary.application.ai.BulkChangeProposal;
import com.autosecretary.platform.model.LocalModelManager;
import com.autosecretary.domain.Routine;
import com.autosecretary.domain.Step;
import com.autosecretary.domain.WorkItem;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Orchestrates one serialized on-device model lane; parsing and model bytes are delegated. */
public final class OnDeviceBulkEditor implements AiProposalGateway {
    static final int MAX_PROMPT_CHARACTERS = 6_000;
    private static final int MAX_MODEL_TOKENS = 2_048;
    private static final int RESERVED_OUTPUT_TOKENS = 256;
    private static final int MAX_INSTRUCTION_CHARACTERS = 1_000;
    private final Context context;
    private final ExecutorService ioExecutor;
    private final ExecutorService aiExecutor;
    private final LocalModelManager models;
    private final TimeProvider clock;
    private final AiProposalParser parser = new AiProposalParser();
    private final GermanCommandCompiler commands = new GermanCommandCompiler();

    public OnDeviceBulkEditor(
            Context context,
            ExecutorService ioExecutor,
            ExecutorService aiExecutor,
            TimeProvider clock,
            LocalModelManager models) {
        this.context = context.getApplicationContext();
        this.ioExecutor = ioExecutor;
        this.aiExecutor = aiExecutor;
        this.clock = clock;
        this.models = models;
    }

    public Future<?> propose(
            String instruction,
            List<WorkItem> current,
            Consumer<BulkChangeProposal> onSuccess,
            Consumer<Throwable> onError) {
        return aiExecutor.submit(() -> {
            try {
                BulkChangeProposal compiled = commands.compile(instruction, current, localNow());
                if (compiled != null) {
                    if (!Thread.currentThread().isInterrupted()) onSuccess.accept(compiled);
                    return;
                }
                if (!(models.status() instanceof ModelStatus.Ready ready)) {
                    throw new IllegalStateException("Das lokale Modell ist noch nicht bereit");
                }
                LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(ready.path().toString())
                        .setMaxTokens(MAX_MODEL_TOKENS).setMaxTopK(20).build();
                String response;
                try (LlmInference inference = LlmInference.createFromOptions(context, options)) {
                    String prompt = prompt(instruction, current);
                    int inputTokens = inference.sizeInTokens(prompt);
                    if (inputTokens > MAX_MODEL_TOKENS - RESERVED_OUTPUT_TOKENS) {
                        throw new IllegalArgumentException("KI-Kontext enthält zu viele Tokens");
                    }
                    response = inference.generateResponse(prompt);
                }
                BulkChangeProposal proposal = parser.parse(response, current, localNow());
                if (!Thread.currentThread().isInterrupted()) onSuccess.accept(proposal);
            } catch (Throwable error) {
                if (!Thread.currentThread().isInterrupted()) onError.accept(error);
            }
        });
    }

    private java.time.LocalDateTime localNow() {
        return java.time.LocalDateTime.ofInstant(clock.now(), clock.zone());
    }

    private static void run(
            ThrowingRunnable work,
            Runnable onSuccess,
            Consumer<Throwable> onError) {
        try {
            work.run();
            if (!Thread.currentThread().isInterrupted()) onSuccess.run();
        } catch (Throwable error) {
            if (!Thread.currentThread().isInterrupted()) onError.accept(error);
        }
    }

    static String prompt(String instruction, List<WorkItem> current) throws Exception {
        String request = instruction == null ? "" : instruction.trim();
        if (request.isEmpty()) throw new IllegalArgumentException("KI-Anweisung fehlt");
        if (request.length() > MAX_INSTRUCTION_CHARACTERS) {
            throw new IllegalArgumentException("KI-Anweisung ist zu lang");
        }

        JSONArray state = new JSONArray();
        List<WorkItem> candidates = new ArrayList<>(current);
        String normalizedRequest = request.toLowerCase(Locale.GERMAN);
        candidates.sort(Comparator.comparing((WorkItem item) -> !normalizedRequest.contains(
                item.title().toLowerCase(Locale.GERMAN))));
        for (WorkItem item : candidates) {
            JSONObject value = itemJson(item);
            state.put(value);
            if (renderPrompt(request, state).length() > MAX_PROMPT_CHARACTERS) {
                state.remove(state.length() - 1);
            }
        }
        String result = renderPrompt(request, state);
        if (result.length() > MAX_PROMPT_CHARACTERS) {
            throw new IllegalArgumentException("KI-Kontext ist zu groß");
        }
        return result;
    }

    private static JSONObject itemJson(WorkItem item) throws Exception {
        JSONObject value = new JSONObject();
        value.put("id", item.id());
        value.put("kind", item instanceof Routine ? "ROUTINE" : "TASK");
        value.put("title", item.title());
        value.put("durationMinutes", item.durationMinutes());
        value.put("deadline", item.deadlineAt() == null
                ? JSONObject.NULL : item.deadlineAt().toString());
        value.put("cadenceDays", item instanceof Routine routine ? routine.cadenceDays() : 0);
        value.put("nextDueDate", item instanceof Routine routine
                ? routine.nextDueDate().toString() : JSONObject.NULL);
        value.put("timePreference", item.timePreference() == null
                ? JSONObject.NULL : item.timePreference().name());
        value.put("flexible", item.flexible());
        JSONArray steps = new JSONArray();
        for (Step step : item.steps()) {
            JSONArray days = new JSONArray();
            for (DayOfWeek day : step.days()) days.put(day.name());
            steps.put(new JSONObject().put("id", step.id()).put("title", step.title())
                    .put("days", days));
        }
        value.put("steps", steps);
        return value;
    }

    private static String renderPrompt(String instruction, JSONArray state) {
        return String.format(Locale.ROOT, """
                Convert the German REQUEST into one valid JSON object. Output JSON only, no Markdown.
                ROOT fields: summary (short string), actions (array of objects, never strings).
                Example request: Lege eine Aufgabe Müll rausbringen mit 10 Minuten an.
                Example output: {"summary":"Müll rausbringen","actions":[{"type":"add",
                "kind":"TASK","title":"Müll rausbringen","durationMinutes":10}]}
                Now solve REQUEST below; never copy the example values.
                Every action object has type.
                ADD uses type="add", kind="TASK" or "ROUTINE", title, durationMinutes.
                UPDATE uses type="update", the exact existing id, and only requested changed fields.
                DELETE uses only type="delete" and the exact existing id.
                Optional fields: deadline (ISO date-time or null), timePreference
                (MORNING, MIDDAY, EVENING or null), flexible, and steps.
                A ROUTINE also requires cadenceDays >= 1, nextDueDate (ISO date), deadline=null.
                A TASK never uses cadenceDays or nextDueDate.
                A step has title, days (English weekday names), and an existing id only when editing it.
                Preserve existing step ids. Never invent an id. Do exactly the request, no extra actions.
                If unclear, return an empty actions array.
                CURRENT=%s
                REQUEST=%s
                """, state, JSONObject.quote(instruction));
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
}
