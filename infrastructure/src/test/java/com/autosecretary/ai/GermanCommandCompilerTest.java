package com.autosecretary.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.autosecretary.application.ai.BulkChange;
import com.autosecretary.domain.CompletionStats;
import com.autosecretary.domain.Routine;
import com.autosecretary.domain.Step;
import com.autosecretary.domain.Task;
import com.autosecretary.domain.TimePreference;
import com.autosecretary.domain.WorkItem;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class GermanCommandCompilerTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 8, 0);

    @Test
    public void compilesAllTwentyDeviceGateCommandsWithoutGuessing() {
        List<Case> cases = List.of(
                add("Lege eine Aufgabe Steuerunterlagen sortieren mit 45 Minuten an.", 1),
                add("Lege eine Aufgabe Bericht abgeben mit 30 Minuten und Deadline "
                        + "2026-08-20T18:00:00 an.", 1),
                add("Lege eine flexible Aufgabe Meditation mit 15 Minuten am Morgen an.", 1),
                add("Lege eine Aufgabe Küche aufräumen mit 25 Minuten am Abend an.", 1),
                add("Lege eine Routine Wasserpflanzen alle 1 Tage mit 10 Minuten an, "
                        + "nächste Fälligkeit 2026-08-12.", 1),
                add("Lege eine Routine Wochenrückblick alle 7 Tage mit 40 Minuten an, "
                        + "nächste Fälligkeit 2026-08-14.", 1),
                add("Lege eine Routine Papiermüll alle 14 Tage mit 20 Minuten an, "
                        + "nächste Fälligkeit 2026-08-18.", 1),
                update("Benenne die vorhandene Aufgabe Einkauf in Wochenendeinkauf um."),
                update("Setze die Dauer der vorhandenen Aufgabe Einkauf auf 50 Minuten."),
                update("Plane die vorhandene Aufgabe Einkauf bevorzugt am Abend."),
                update("Schalte bei der vorhandenen Aufgabe Einkauf flexibles Lernen aus."),
                update("Ergänze bei Einkauf einen neuen Schritt Einkaufsliste schreiben."),
                update("Benenne den vorhandenen Schritt Milch kaufen bei Einkauf in "
                        + "Hafermilch kaufen um und behalte seine ID."),
                update("Setze bei Einkauf den vorhandenen Schritt Milch kaufen an die erste "
                        + "Position und behalte alle Schritt-IDs."),
                delete("Schlage vor, die vorhandene Aufgabe Einkauf zu löschen."),
                update("Ändere die vorhandene Routine Lüften auf alle 2 Tage."),
                update("Setze die nächste Fälligkeit der vorhandenen Routine Lüften auf "
                        + "2026-08-13."),
                add("Lege zwei Aufgaben an: Post holen mit 10 Minuten und Rechnung prüfen mit "
                        + "20 Minuten.", 2),
                update("Benenne Einkauf in Großeinkauf um und setze die Dauer auf 60 Minuten."),
                new Case("Ändere nur dann etwas, wenn du sicher bist: Die Aufgabe soll irgendwie "
                        + "besser werden.", null, 0)
        );

        GermanCommandCompiler compiler = new GermanCommandCompiler();
        for (Case value : cases) {
            var proposal = compiler.compile(value.instruction(), currentItems(), NOW);
            assertNotNull(value.instruction(), proposal);
            assertEquals(value.instruction(), value.count(), proposal.changes().size());
            if (value.type() != null) assertTrue(value.instruction(), proposal.changes().stream()
                    .allMatch(change -> change.type() == value.type()));
        }
    }

    @Test
    public void compiledValuesRetainIdentityAndRequestedSemantics() {
        GermanCommandCompiler compiler = new GermanCommandCompiler();
        List<WorkItem> current = currentItems();

        WorkItem renamed = compiler.compile(
                "Benenne den vorhandenen Schritt Milch kaufen bei Einkauf in Hafermilch kaufen "
                        + "um und behalte seine ID.", current, NOW)
                .changes().get(0).upsert();
        assertEquals("40000000-0000-0000-0000-000000000101", renamed.steps().get(0).id());
        assertEquals("Hafermilch kaufen", renamed.steps().get(0).title());

        WorkItem edited = compiler.compile(
                "Benenne Einkauf in Großeinkauf um und setze die Dauer auf 60 Minuten.",
                current, NOW).changes().get(0).upsert();
        assertEquals("40000000-0000-0000-0000-000000000001", edited.id());
        assertEquals(3, edited.revision());
        assertEquals("Großeinkauf", edited.title());
        assertEquals(60, edited.durationMinutes());

        Routine routine = (Routine) compiler.compile(
                "Ändere die vorhandene Routine Lüften auf alle 2 Tage.", current, NOW)
                .changes().get(0).upsert();
        assertEquals(2, routine.cadenceDays());
        assertEquals(LocalDate.of(2026, 8, 11), routine.nextDueDate());

        Task morning = (Task) compiler.compile(
                "Lege eine flexible Aufgabe Meditation mit 15 Minuten am Morgen an.",
                current, NOW).changes().get(0).upsert();
        assertEquals(TimePreference.MORNING, morning.timePreference());
        assertTrue(morning.flexible());
        assertFalse(morning.completed());
    }

    @Test
    public void unsupportedOrAmbiguousTargetsAreNeverGuessed() {
        GermanCommandCompiler compiler = new GermanCommandCompiler();
        assertEquals(null, compiler.compile("Mach meinen Tag besser", currentItems(), NOW));
        org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> compiler.compile(
                "Setze die Dauer der vorhandenen Aufgabe Unbekannt auf 50 Minuten.",
                currentItems(), NOW));
    }

    private static Case add(String instruction, int count) {
        return new Case(instruction, BulkChange.Type.ADD, count);
    }

    private static Case update(String instruction) {
        return new Case(instruction, BulkChange.Type.UPDATE, 1);
    }

    private static Case delete(String instruction) {
        return new Case(instruction, BulkChange.Type.DELETE, 1);
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

    private record Case(String instruction, BulkChange.Type type, int count) { }
}
