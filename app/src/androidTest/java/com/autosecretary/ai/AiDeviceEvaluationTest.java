package com.autosecretary.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.autosecretary.application.ai.BulkChangeProposal;
import com.autosecretary.application.ai.BulkChange;
import com.autosecretary.domain.CompletionStats;
import com.autosecretary.domain.Routine;
import com.autosecretary.domain.Step;
import com.autosecretary.domain.Task;
import com.autosecretary.domain.WorkItem;
import com.autosecretary.platform.model.LocalModelManager;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;

import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import com.autosecretary.platform.model.LocalModelManager;
import com.autosecretary.application.TimeProvider;
import java.time.Instant;
import java.time.ZoneId;

/** Optional device evaluation of the separately provisioned production model. */
@RunWith(AndroidJUnit4.class)
public final class AiDeviceEvaluationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 8, 0);

    @Test
    @LargeTest
    public void twentyGermanCommandsYieldAtLeastNinetyPercentValidTypedProposals()
            throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var executor = Executors.newSingleThreadExecutor();
        try {
            LocalModelManager models = new LocalModelManager(app);
            models.install();
            TimeProvider time = new TimeProvider() {
                @Override public Instant now() { return NOW.toInstant(java.time.ZoneOffset.UTC); }
                @Override public ZoneId zone() { return java.time.ZoneOffset.UTC; }
            };
            OnDeviceBulkEditor editor = new OnDeviceBulkEditor(
                    app, executor, executor, time, models);
            assertTrue(models.hasModel());

            List<WorkItem> current = currentItems();
            List<WorkItem> before = List.copyOf(current);
            JSONArray cases;
            try (var input = InstrumentationRegistry.getInstrumentation().getContext()
                    .getAssets().open("ai-german-cases.json")) {
                cases = new JSONArray(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertEquals(20, cases.length());
            int valid = 0;
            for (int index = 0; index < cases.length(); index++) {
                var evaluationCase = cases.getJSONObject(index);
                String instruction = evaluationCase.getString("instruction");
                AtomicReference<BulkChangeProposal> proposal = new AtomicReference<>();
                AtomicReference<Throwable> error = new AtomicReference<>();
                editor.propose(instruction, current, proposal::set, error::set)
                        .get(3, TimeUnit.MINUTES);
                if (error.get() == null && proposal.get() != null) {
                    assertNotNull(proposal.get().changes());
                    int size = proposal.get().changes().size();
                    boolean expectedSize = size >= evaluationCase.getInt("minChanges")
                            && size <= evaluationCase.getInt("maxChanges");
                    String type = evaluationCase.optString("type", "");
                    boolean expectedType = type.isEmpty() || proposal.get().changes().stream()
                            .allMatch(change -> change.type() == BulkChange.Type.valueOf(type));
                    if (expectedSize && expectedType) valid++;
                }
                // The gateway has no persistence port: preview generation cannot mutate source state.
                assertEquals(before, current);
            }
            assertTrue("Mindestens 18/20 valide Changesets erwartet, erhalten: " + valid,
                    valid >= 18);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @LargeTest
    public void productionModelLoadsAndPerformsRealInference() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocalModelManager models = new LocalModelManager(app);
        models.install();
        var options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(models.file().getAbsolutePath())
                .setMaxTokens(256)
                .setMaxTopK(20)
                .build();

        try (LlmInference inference = LlmInference.createFromOptions(app, options)) {
            String prompt = LocalModelManager.VALIDATION_PROMPT;
            assertTrue(inference.sizeInTokens(prompt) < 128);
            assertTrue(inference.generateResponse(prompt).trim().length() > 0);
        }
    }

    @Test
    public void invalidModelOutputsAreRejectedOnDeviceBeforeAnyMutation() {
        Task current = (Task) currentItems().get(0);
        List<String> invalid = List.of(
                "kein json",
                "{",
                "{}",
                "{\"actions\":\"keine\"}",
                "{\"actions\":[{\"type\":\"magic\"}]}",
                "{\"actions\":[{\"type\":\"add\",\"kind\":\"MAGIC\",\"title\":\"X\"}]}",
                "{\"actions\":[{\"type\":\"delete\",\"id\":\"unbekannt\"}]}",
                "{\"actions\":[{\"type\":\"update\",\"id\":\"unbekannt\"}]}",
                "{\"actions\":[{\"type\":\"update\",\"id\":\"" + current.id()
                        + "\",\"durationMinutes\":-1}]}",
                "{\"actions\":[{\"type\":\"update\",\"id\":\"" + current.id()
                        + "\",\"timePreference\":\"NACHT\"}]}",
                "{\"actions\":[{\"type\":\"update\",\"id\":\"" + current.id()
                        + "\",\"steps\":[{\"id\":\"unbekannt\",\"title\":\"X\"}]}]}",
                "{\"actions\":[{\"type\":\"update\",\"id\":\"" + current.id()
                        + "\"},{\"type\":\"delete\",\"id\":\"" + current.id() + "\"}]}",
                "{\"actions\":[{\"type\":\"add\",\"kind\":\"ROUTINE\","
                        + "\"title\":\"Kaputt\",\"cadenceDays\":0}]}",
                "{\"actions\":[{\"type\":\"add\",\"kind\":\"ROUTINE\","
                        + "\"title\":\"Ohne Fälligkeit\",\"cadenceDays\":7}]}",
                "{\"actions\":[{\"type\":\"add\",\"kind\":\"TASK\","
                        + "\"title\":\"Widerspruch\",\"cadenceDays\":7,"
                        + "\"nextDueDate\":\"2026-08-12\"}]}"
        );

        for (String output : invalid) {
            org.junit.Assert.assertThrows(Exception.class,
                    () -> new AiProposalParser().parse(output, List.of(current), NOW));
        }
    }

    private static List<WorkItem> currentItems() {
        Task task = new Task("40000000-0000-0000-0000-000000000001", "Einkauf", 30,
                null, null, true,
                List.of(new Step("40000000-0000-0000-0000-000000000101",
                        "Milch kaufen", Set.of(), 0)), NOW.minusDays(5), false,
                CompletionStats.empty(), 3);
        Routine routine = new Routine("40000000-0000-0000-0000-000000000002", "Lüften", 10,
                null, null, true, List.of(), NOW.minusDays(5), 1,
                LocalDate.of(2026, 8, 11), CompletionStats.empty(), 2);
        return List.of(task, routine);
    }
}
