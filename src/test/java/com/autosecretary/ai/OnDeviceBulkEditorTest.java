package com.autosecretary.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.autosecretary.domain.CompletionStats;
import com.autosecretary.application.ai.BulkChange;
import com.autosecretary.domain.Step;
import com.autosecretary.domain.Task;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public final class OnDeviceBulkEditorTest {
    private static final String TASK_ID = "00000000-0000-0000-0000-000000000001";
    private static final String STEP_ID = "00000000-0000-0000-0000-000000000101";
    private static final String INVENTED_STEP_ID = "00000000-0000-0000-0000-000000000102";
    @Test
    public void extractsJsonFromModelPreambleAndTrailingText() {
        String result = AiProposalParser.extractJsonObject(
                "Hier ist die Vorschau:\n{\"summary\":\"ok\",\"actions\":[]}\nFertig.");

        assertEquals("{\"summary\":\"ok\",\"actions\":[]}", result);
    }

    @Test
    public void bracesInsideStringsDoNotEndJsonEarly() {
        String result = AiProposalParser.extractJsonObject(
                "```json\n{\"summary\":\"Nutze {A}\",\"actions\":[]}\n```");

        assertEquals("{\"summary\":\"Nutze {A}\",\"actions\":[]}", result);
    }

    @Test
    public void typedUpdateKeepsReferencedStepIdAndRevision() throws Exception {
        Task current = new Task(TASK_ID, "Alt", 30, null, null, true,
                List.of(new Step(STEP_ID, "Lesen", Set.of(), 0)),
                LocalDateTime.of(2026, 8, 1, 9, 0), false, CompletionStats.empty(), 7);
        String response = """
                {"summary":"Titel ändern","actions":[
                  {"type":"update","id":"%s","title":"Neu",
                   "steps":[{"id":"%s","title":"Genau lesen","days":[]}]}
                ]}
                """.formatted(TASK_ID, STEP_ID);

        BulkChange change = new AiProposalParser().parse(response, List.of(current),
                LocalDateTime.of(2026, 8, 11, 8, 0)).changes().get(0);

        assertEquals(BulkChange.Type.UPDATE, change.type());
        assertEquals(7, change.expectedRevision());
        assertEquals("Neu", change.upsert().title());
        assertEquals(STEP_ID, change.upsert().steps().get(0).id());
    }

    @Test
    public void inventedStepIdRejectsWholeProposal() {
        Task current = new Task(TASK_ID, "Alt", 30, null, null, true,
                List.of(new Step(STEP_ID, "Lesen", Set.of(), 0)),
                LocalDateTime.of(2026, 8, 1, 9, 0), false, CompletionStats.empty(), 1);
        String response = """
                {"summary":"falsch","actions":[
                  {"type":"update","id":"%s",
                   "steps":[{"id":"%s","title":"Lesen","days":[]}]}
                ]}
                """.formatted(TASK_ID, INVENTED_STEP_ID);

        assertThrows(IllegalArgumentException.class,
                () -> new AiProposalParser().parse(response, List.of(current),
                        LocalDateTime.of(2026, 8, 11, 8, 0)));
    }

    @Test
    public void invalidModelOutputsAreAlwaysRejected() {
        Task current = new Task(TASK_ID, "Alt", 30, null, null, true,
                List.of(new Step(STEP_ID, "Lesen", Set.of(), 0)),
                LocalDateTime.of(2026, 8, 1, 9, 0), false, CompletionStats.empty(), 1);
        List<String> invalid = List.of(
                "kein json",
                "{",
                "{}",
                "{\"actions\":\"keine\"}",
                "{\"actions\":[{\"type\":\"magic\"}]}",
                "{\"actions\":[{\"type\":\"add\",\"kind\":\"MAGIC\",\"title\":\"X\"}]}",
                "{\"actions\":[{\"type\":\"delete\",\"id\":\"unbekannt\"}]}",
                "{\"actions\":[{\"type\":\"update\",\"id\":\"unbekannt\",\"title\":\"X\"}]}",
                "{\"actions\":[{\"type\":\"update\",\"id\":\"" + TASK_ID
                        + "\",\"durationMinutes\":-1}]}",
                "{\"actions\":[{\"type\":\"update\",\"id\":\"" + TASK_ID
                        + "\",\"timePreference\":\"NACHT\"}]}",
                "{\"actions\":[{\"type\":\"update\",\"id\":\"" + TASK_ID
                        + "\",\"steps\":[{\"id\":\"" + INVENTED_STEP_ID
                        + "\",\"title\":\"X\"}]}]}",
                "{\"actions\":[{\"type\":\"update\",\"id\":\"" + TASK_ID
                        + "\",\"steps\":[{\"id\":\"" + STEP_ID + "\",\"title\":\"A\"},"
                        + "{\"id\":\"" + STEP_ID + "\",\"title\":\"B\"}]}]}",
                "{\"actions\":[{\"type\":\"update\",\"id\":\"" + TASK_ID
                        + "\",\"title\":\"A\"},{\"type\":\"delete\",\"id\":\""
                        + TASK_ID + "\"}]}",
                "{\"actions\":[{\"type\":\"add\",\"kind\":\"ROUTINE\","
                        + "\"title\":\"Ohne Fälligkeit\",\"cadenceDays\":7}]}",
                "{\"actions\":[{\"type\":\"add\",\"kind\":\"TASK\","
                        + "\"title\":\"Widerspruch\",\"cadenceDays\":7,"
                        + "\"nextDueDate\":\"2026-08-12\"}]}"
        );

        for (String response : invalid) {
            assertThrows(Exception.class, () -> new AiProposalParser().parse(
                    response, List.of(current), LocalDateTime.of(2026, 8, 11, 8, 0)));
        }
    }

    @Test
    public void promptIsBoundedAndKeepsTheExplicitlyReferencedItemFirst() throws Exception {
        List<com.autosecretary.domain.WorkItem> current = new ArrayList<>();
        for (int index = 0; index < 200; index++) {
            current.add(new Task("00000000-0000-0000-0001-%012d".formatted(index),
                    index == 199 ? "Zielaufgabe" : "Aufgabe " + index,
                    30, null, null, true, List.of(),
                    LocalDateTime.of(2026, 8, 1, 9, 0), false,
                    CompletionStats.empty(), 1));
        }

        String prompt = OnDeviceBulkEditor.prompt(
                "Setze die Dauer der vorhandenen Aufgabe Zielaufgabe auf 50 Minuten.", current);

        org.junit.Assert.assertTrue(prompt.length()
                <= OnDeviceBulkEditor.MAX_PROMPT_CHARACTERS);
        org.junit.Assert.assertTrue(prompt.indexOf("Zielaufgabe")
                < prompt.indexOf("Aufgabe 0"));
    }

    @Test
    public void oversizedInstructionIsRejectedBeforeNativeInference() {
        assertThrows(IllegalArgumentException.class, () -> OnDeviceBulkEditor.prompt(
                "x".repeat(1_001), List.of()));
    }
}
