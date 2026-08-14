package com.autosecretary.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.domain.CompletionStats;
import com.autosecretary.domain.Step;
import com.autosecretary.domain.Task;
import com.autosecretary.domain.Routine;
import com.autosecretary.application.DayPlanDirective;
import com.autosecretary.application.ai.BulkChange;
import com.autosecretary.data.entity.CompletionEntity;
import com.autosecretary.data.entity.StepCompletionEntity;
import com.autosecretary.data.entity.DayPlanDirectiveEntity;
import com.autosecretary.data.entity.StepDayEntity;
import com.autosecretary.data.entity.WorkItemEntity;
import com.autosecretary.domain.WorkItem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public final class RoomWorkItemRepositoryTest {
    private static final String DATABASE = "repository-test.db";
    private Context context;
    private FocusDatabase database;
    private RoomWorkItemRepository repository;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE);
        open();
    }

    @After
    public void tearDown() {
        if (database != null) database.close();
        context.deleteDatabase(DATABASE);
    }

    @Test
    public void insertingStepBeforeExistingOnePreservesStableIdentityAndCompletion() {
        Task original = task("task", "Waschen", List.of(
                new Step(id("shower"), "Duschen", Set.of(), 0),
                new Step(id("hair"), "Haare", Set.of(), 1)), 0);
        repository.save(original);
        repository.setStepCompleted(id("task"), id("hair"), true,
                LocalDateTime.of(2026, 8, 11, 8, 0));

        Task stored = (Task) repository.find(id("task"));
        assertEquals(1, stored.revision());
        Task edited = new Task(stored.id(), stored.title(), stored.durationMinutes(),
                stored.deadlineAt(), stored.timePreference(), stored.flexible(), List.of(
                new Step(id("teeth"), "Zähne", Set.of(), 0),
                new Step(id("shower"), "Duschen", Set.of(), 1),
                new Step(id("hair"), "Haare", Set.of(), 2)), stored.createdAt(), false,
                stored.stats(), stored.revision());
        repository.save(edited);

        assertEquals(List.of(id("teeth"), id("shower"), id("hair")), repository.find(id("task")).steps()
                .stream().map(Step::id).toList());
        assertTrue(repository.loadSnapshot().stepCompletions().stream()
                .anyMatch(value -> value.stepId().equals(id("hair"))));
        assertFalse(repository.loadSnapshot().stepCompletions().stream()
                .anyMatch(value -> value.stepId().equals(id("teeth"))));
    }

    @Test
    public void confirmedHistoryCleanupDeletesOnlyTheSelectedCompletedTasks() {
        repository.save(task("old-one", "Alt eins", List.of(), 0));
        repository.save(task("old-two", "Alt zwei", List.of(), 0));
        repository.save(task("keep", "Bleibt", List.of(), 0));
        repository.complete(id("old-one"), LocalDateTime.of(2026, 6, 1, 9, 0));
        repository.complete(id("old-two"), LocalDateTime.of(2026, 6, 2, 9, 0));

        repository.deleteAll(List.of(id("old-one"), id("old-two")));

        assertNull(repository.find(id("old-one")));
        assertNull(repository.find(id("old-two")));
        assertNotNull(repository.find(id("keep")));
        assertTrue(repository.loadSnapshot().completions().isEmpty());
    }

    @Test
    public void staleMemberRollsBackEntireAiChangeSet() {
        repository.save(task("one", "Eins", List.of(), 0));
        repository.save(task("two", "Zwei", List.of(), 0));
        Task one = (Task) repository.find(id("one"));
        Task two = (Task) repository.find(id("two"));
        Task changedOne = renamed(one, "Eins neu", one.revision());
        Task staleTwo = renamed(two, "Zwei neu", two.revision() + 1);

        assertThrows(IllegalStateException.class, () -> repository.applyChangeSet(
                List.of(update("change-one", changedOne),
                        update("change-two", staleTwo)), "KI rückgängig",
                LocalDateTime.of(2026, 8, 11, 11, 0)));

        assertEquals("Eins", repository.find(id("one")).title());
        assertEquals("Zwei", repository.find(id("two")).title());
        assertNull(repository.latestUndoLabel());
    }

    @Test
    public void overlappingDeleteAndUpdateRollsBackWithoutCreatingUndo() {
        repository.save(task("overlap", "Original", List.of(), 0));
        Task original = (Task) repository.find(id("overlap"));
        Task update = renamed(original, "Geändert", original.revision());

        assertThrows(IllegalStateException.class, () -> repository.applyChangeSet(
                List.of(update("overlap-update", update),
                        delete("overlap-delete", original)),
                "ungültig", LocalDateTime.of(2026, 8, 11, 12, 0)));

        assertEquals("Original", repository.find(original.id()).title());
        assertEquals(0, database.focusDao().countUndoJournal());
    }

    @Test
    public void stepIdentityCannotBeMovedToAnotherWorkItem() {
        repository.save(task("owner", "Besitzer", List.of(
                new Step(id("owned-step"), "Bleibt hier", Set.of(), 0)), 0));
        Task intruder = task("intruder", "Fremd", List.of(
                new Step(id("owned-step"), "Stehlen", Set.of(), 0)), 0);

        assertThrows(IllegalStateException.class, () -> repository.save(intruder));

        assertNull(repository.find(id("intruder")));
        assertEquals(id("owner"), database.focusDao().readStep(id("owned-step")).workItemId);
    }

    @Test
    public void corruptDayOnOneStepDoesNotHideItsParentOrUnrelatedItems() {
        repository.save(task("affected", "Mit kaputtem Tag", List.of(
                new Step(id("affected-step"), "Einzelschritt", Set.of(), 0)), 0));
        repository.save(task("unrelated", "Unabhängig", List.of(), 0));
        StepDayEntity corrupt = new StepDayEntity();
        corrupt.stepId = id("affected-step");
        corrupt.dayOfWeek = "FUNDAY";
        database.focusDao().upsertStepDays(List.of(corrupt));

        var snapshot = repository.loadSnapshot();

        assertEquals(2, snapshot.workItems().size());
        assertTrue(snapshot.workItems().stream()
                .filter(item -> item.id().equals(id("affected")))
                .findFirst().orElseThrow().steps().isEmpty());
        assertNotNull(snapshot.workItems().stream()
                .filter(item -> item.id().equals(id("unrelated")))
                .findFirst().orElse(null));
    }

    @Test
    public void veryOverdueRoutineAdvancesInConstantTimeToTheNextOccurrence() {
        Routine overdue = new Routine(id("overdue"), "Lange überfällig", 15, null, null,
                true, List.of(), LocalDateTime.of(2000, 1, 1, 8, 0), 1,
                LocalDate.of(2000, 1, 1), CompletionStats.empty(), 0);
        repository.save(overdue);

        Routine completed = (Routine) repository.complete(
                overdue.id(), LocalDateTime.of(2026, 8, 11, 12, 0));

        assertEquals(LocalDate.of(2026, 8, 12), completed.nextDueDate());
        assertEquals(1, completed.stats().totalCompletions());
    }

    @Test
    public void corruptCompletionEvidenceDoesNotBreakOtherSnapshotData() {
        repository.save(task("readable", "Lesbar", List.of(
                new Step(id("readable-step"), "Schritt", Set.of(), 0)), 0));
        CompletionEntity completion = new CompletionEntity();
        completion.id = id("corrupt-completion");
        completion.workItemId = id("readable");
        completion.occurrenceKey = "TASK";
        completion.completedAt = "kein-zeitpunkt";
        database.focusDao().upsertCompletion(completion);
        StepCompletionEntity stepCompletion = new StepCompletionEntity();
        stepCompletion.id = id("corrupt-step-completion");
        stepCompletion.stepId = id("readable-step");
        stepCompletion.occurrenceKey = "TASK";
        stepCompletion.completedAt = "auch-kein-zeitpunkt";
        database.focusDao().upsertStepCompletion(stepCompletion);

        var snapshot = repository.loadSnapshot();

        assertEquals(1, snapshot.workItems().size());
        assertTrue(snapshot.completions().isEmpty());
        assertTrue(snapshot.stepCompletions().isEmpty());
    }

    @Test
    public void confirmedTypedChangeSetPersistsIdsAndCanBeUndoneAfterDatabaseReopen()
            throws Exception {
        repository.save(task("existing", "Alt", List.of(), 0));
        Task existing = (Task) repository.find(id("existing"));
        repository.applyChangeSet(List.of(
                        update("existing-update",
                                renamed(existing, "Neu", existing.revision())),
                        add("created-add", task("created", "Neu angelegt", List.of(), 0))),
                "KI-Block rückgängig machen",
                LocalDateTime.of(2026, 8, 11, 12, 0));
        assertEquals("Neu", repository.find(id("existing")).title());
        assertNotNull(repository.find(id("created")));
        org.json.JSONArray storedChangeIds = new org.json.JSONObject(
                database.focusDao().readLatestUndo().payloadJson).getJSONArray("changeIds");
        assertEquals(Set.of(id("existing-update"), id("created-add")),
                Set.of(storedChangeIds.getString(0), storedChangeIds.getString(1)));

        database.close();
        open();
        assertEquals("KI-Block rückgängig machen", repository.latestUndoLabel());
        assertTrue(repository.undoLatest(LocalDateTime.of(2026, 8, 11, 12, 0)));
        assertEquals("Alt", repository.find(id("existing")).title());
        assertNull(repository.find(id("created")));
        assertFalse(repository.undoLatest(LocalDateTime.of(2026, 8, 11, 12, 1)));
    }

    @Test
    public void currentSchemaPreservesTasksRoutinesAndStepsAfterReopen() {
        repository.save(task("persisted-task", "Bleibende Aufgabe", List.of(
                new Step(id("persisted-step"), "Bleibender Schritt", Set.of(), 0)), 0));
        repository.save(new Routine(id("persisted-routine"), "Bleibende Routine", 15,
                null, null, false, List.of(), LocalDateTime.of(2026, 8, 1, 10, 0),
                7, LocalDate.of(2026, 8, 14), CompletionStats.empty(), 0));

        database.close();
        open();

        assertEquals("Bleibende Aufgabe", repository.find(id("persisted-task")).title());
        assertEquals("Bleibender Schritt",
                repository.find(id("persisted-task")).steps().get(0).title());
        assertEquals("Bleibende Routine", repository.find(id("persisted-routine")).title());
    }

    @Test
    public void typedAddCannotOverwriteAnItemThatAppearedAfterPreview() {
        repository.save(task("collision", "Schon vorhanden", List.of(), 0));
        Task replacement = task("collision", "Darf nicht überschreiben", List.of(), 0);

        assertThrows(IllegalStateException.class, () -> repository.applyChangeSet(
                List.of(add("collision-add", replacement)), "KI rückgängig",
                LocalDateTime.of(2026, 8, 11, 12, 0)));

        assertEquals("Schon vorhanden", repository.find(id("collision")).title());
        assertNull(repository.latestUndoLabel());
    }

    @Test
    public void typedAddCannotOverwriteAQuarantinedCorruptRowWithTheSameId() {
        WorkItemEntity corrupt = new WorkItemEntity();
        corrupt.id = id("corrupt-collision");
        corrupt.kind = "TASK";
        corrupt.title = "";
        corrupt.durationMinutes = 30;
        corrupt.flexible = true;
        corrupt.createdAt = "2026-08-01T10:00:00";
        corrupt.revision = 0;
        database.focusDao().upsertWorkItem(corrupt);
        assertNull(repository.find(corrupt.id));

        Task replacement = task("corrupt-collision", "Darf nicht überschreiben", List.of(), 0);
        assertThrows(IllegalStateException.class, () -> repository.applyChangeSet(
                List.of(add("corrupt-collision-add", replacement)), "KI rückgängig",
                LocalDateTime.of(2026, 8, 11, 12, 0)));

        assertEquals("", database.focusDao().readWorkItem(corrupt.id).title);
        assertNull(repository.latestUndoLabel());
    }

    @Test
    public void undoDeletedItemRestoresWorkAndStepCompletionHistory() {
        Task original = task("history", "Mit Historie", List.of(
                new Step(id("history-step"), "Fertig", Set.of(), 0)), 0);
        repository.save(original);
        repository.setStepCompleted(id("history"), id("history-step"), true,
                LocalDateTime.of(2026, 8, 11, 9, 0));
        Task completed = (Task) repository.find(id("history"));
        assertTrue(completed.completed());
        assertEquals(1, repository.loadSnapshot().completions().size());
        assertEquals(1, repository.loadSnapshot().stepCompletions().size());

        repository.applyChangeSet(List.of(delete("history-delete", completed)),
                "Löschen rückgängig",
                LocalDateTime.of(2026, 8, 11, 10, 0));
        assertNull(repository.find(completed.id()));
        assertTrue(repository.loadSnapshot().completions().isEmpty());
        assertTrue(repository.loadSnapshot().stepCompletions().isEmpty());

        database.close();
        open();
        assertTrue(repository.undoLatest(LocalDateTime.of(2026, 8, 11, 10, 1)));
        assertNotNull(repository.find(completed.id()));
        assertEquals(1, repository.loadSnapshot().completions().size());
        assertEquals(1, repository.loadSnapshot().stepCompletions().size());
    }

    @Test
    public void undoRefusesRevisionConflictWithoutPartialRollback() {
        repository.save(task("conflict", "Alt", List.of(), 0));
        Task original = (Task) repository.find(id("conflict"));
        repository.applyChangeSet(List.of(update("conflict-update",
                        renamed(original, "KI", original.revision()))),
                "KI rückgängig",
                LocalDateTime.of(2026, 8, 11, 12, 30));
        Task aiVersion = (Task) repository.find(id("conflict"));
        repository.save(renamed(aiVersion, "Manuell danach", aiVersion.revision()));

        assertThrows(IllegalStateException.class, () -> repository.undoLatest(
                LocalDateTime.of(2026, 8, 11, 13, 0)));

        assertEquals("Manuell danach", repository.find(id("conflict")).title());
        assertEquals("KI rückgängig", repository.latestUndoLabel());
    }

    @Test
    public void undoJournalKeepsOnlyLatestTwentyEntries() {
        repository.save(task("ordered", "Sortiert", List.of(), 0));
        for (int index = 0; index < 25; index++) {
            repository.saveDirective(new DayPlanDirective(id("directive-" + index),
                    java.time.LocalDate.of(2026, 8, 11), id("ordered"),
                    index % 2 == 0 ? DayPlanDirective.Relation.FIRST
                            : DayPlanDirective.Relation.LAST,
                    null, LocalDateTime.of(2026, 8, 11, 8, 0).plusSeconds(index)),
                    "Reihenfolge " + index);
        }

        assertEquals(20, database.focusDao().countUndoJournal());
        assertEquals("Reihenfolge 24", repository.latestUndoLabel());
    }

    @Test
    public void dayPlanDirectiveCanBeUndoneAfterDatabaseReopen() {
        repository.save(task("move", "Verschoben", List.of(), 0));
        repository.saveDirective(new DayPlanDirective(id("move-directive"),
                java.time.LocalDate.of(2026, 8, 11), id("move"),
                DayPlanDirective.Relation.FIRST, null,
                LocalDateTime.of(2026, 8, 11, 8, 0)), "Reihenfolge rückgängig");
        assertEquals(1, repository.directives(java.time.LocalDate.of(2026, 8, 11)).size());

        database.close();
        open();
        assertTrue(repository.undoLatest(LocalDateTime.of(2026, 8, 11, 8, 1)));
        assertTrue(repository.directives(java.time.LocalDate.of(2026, 8, 11)).isEmpty());
    }

    @Test
    public void missingRelativeAnchorIsIgnoredAndCleaned() {
        repository.save(task("ordered", "Sortiert", List.of(), 0));
        repository.saveDirective(new DayPlanDirective(id("bad-directive"),
                java.time.LocalDate.of(2026, 8, 11), id("ordered"),
                DayPlanDirective.Relation.AFTER, id("missing"),
                LocalDateTime.of(2026, 8, 11, 8, 0)), "ungültig");

        assertTrue(repository.directives(java.time.LocalDate.of(2026, 8, 11)).isEmpty());
        assertTrue(database.focusDao().readDirectives("2026-08-11").isEmpty());
    }

    @Test
    public void completedRelativeAnchorIsIgnoredAndCleaned() {
        repository.save(task("ordered", "Sortiert", List.of(), 0));
        repository.save(task("completed-anchor", "Erledigt", List.of(), 0));
        repository.complete(id("completed-anchor"), LocalDateTime.of(2026, 8, 11, 7, 30));
        repository.saveDirective(new DayPlanDirective(id("completed-anchor-directive"),
                java.time.LocalDate.of(2026, 8, 11), id("ordered"),
                DayPlanDirective.Relation.AFTER, id("completed-anchor"),
                LocalDateTime.of(2026, 8, 11, 8, 0)), "ungültig");

        assertTrue(repository.directives(java.time.LocalDate.of(2026, 8, 11)).isEmpty());
        assertTrue(database.focusDao().readDirectives("2026-08-11").isEmpty());
    }

    @Test
    public void corruptDirectiveIsIgnoredAndCleaned() {
        repository.save(task("ordered", "Sortiert", List.of(), 0));
        DayPlanDirectiveEntity corrupt = new DayPlanDirectiveEntity();
        corrupt.id = id("corrupt-directive");
        corrupt.day = "2026-08-11";
        corrupt.workItemId = id("ordered");
        corrupt.relation = "IRGENDWO";
        corrupt.updatedAt = "kein-zeitpunkt";
        database.focusDao().upsertDirective(corrupt);

        assertTrue(repository.directives(java.time.LocalDate.of(2026, 8, 11)).isEmpty());
        assertTrue(database.focusDao().readDirectives("2026-08-11").isEmpty());
    }

    private void open() {
        database = Room.databaseBuilder(context, FocusDatabase.class, DATABASE)
                .allowMainThreadQueries().build();
        repository = new RoomWorkItemRepository(database);
    }

    private static Task task(String id, String title, List<Step> steps, long revision) {
        return new Task(id(id), title, 30, null, null, true, steps,
                LocalDateTime.of(2026, 8, 1, 10, 0), false, CompletionStats.empty(), revision);
    }

    private static String id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static Task renamed(Task source, String title, long revision) {
        return new Task(source.id(), title, source.durationMinutes(), source.deadlineAt(),
                source.timePreference(), source.flexible(), source.steps(), source.createdAt(),
                source.completed(), source.stats(), revision);
    }

    private static BulkChange add(String changeId, WorkItem item) {
        return new BulkChange(id(changeId), BulkChange.Type.ADD, item.id(), 0, item,
                "Neu: " + item.title());
    }

    private static BulkChange update(String changeId, WorkItem item) {
        return new BulkChange(id(changeId), BulkChange.Type.UPDATE, item.id(), item.revision(),
                item, "Ändern: " + item.title());
    }

    private static BulkChange delete(String changeId, WorkItem item) {
        return new BulkChange(id(changeId), BulkChange.Type.DELETE, item.id(), item.revision(),
                null, "Löschen: " + item.title());
    }
}
