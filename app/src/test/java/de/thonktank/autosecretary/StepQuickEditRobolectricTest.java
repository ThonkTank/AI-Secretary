package de.thonktank.autosecretary;

import static org.junit.Assert.*;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Looper;
import androidx.lifecycle.SavedStateHandle;
import de.thonktank.autosecretary.calendar.*;
import de.thonktank.autosecretary.data.observable.*;
import de.thonktank.autosecretary.data.preferences.UiPreferences;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.*;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidationSource;
import de.thonktank.autosecretary.presentation.today.*;
import java.util.concurrent.*;
import org.robolectric.shadows.ShadowLooper;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import de.thonktank.autosecretary.data.local.*;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.usecase.*;
import de.thonktank.autosecretary.presentation.today.StepEditDraft;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public final class StepQuickEditRobolectricTest {
    private AppDatabase db;
    private ApplicationUseCaseComposition cases;
    private RoomRepositoryFixture repository;
    private int sequence;
    private TodayViewModel model;
    private PresentationInvalidationSource invalidations;
    private String execution, source;
    private final Clock clock = new Clock() {
        public LocalDate today() { return LocalDate.of(2026, 10, 15); }
        public LocalTime time() { return LocalTime.NOON; }
    };

    @Before public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        repository = new RoomRepositoryFixture(db);
        cases = new ApplicationUseCaseComposition(db, clock, () -> "quick-" + (++sequence), ComboPolicySource.defaults());
    }
    @After public void close() {
        if (model != null) model.onCleared();
        if (invalidations != null) invalidations.close();
        db.close();
    }

    @Test public void unbegunEditChangesEveryFieldWithoutChangingTaskOtherExecutionOrHistory() {
        TaskId task = seed();
        OccurrenceStep first = repository.steps.findOccurrenceStep(execution);
        repository.steps.insertOccurrenceSteps(List.of(OccurrenceStep.rehydrate("other", first.occurrenceId,
                1, "Andere Ausführung", false, first.prescription, "Unverändert", List.of(), source,
                "other-combo", null, CarryForwardReason.NONE)));
        List<String> before = snapshot("tasks", "task_schedule_entries", "stats", "reward_bookings", "timer_sessions");
        EditableStep opened = cases.today.editStep.load(execution);
        TaskStepDefinition desired = definition(opened.definition(), "Neu", StepAmount.setsReps(4, 9),
                RestTimerPolicy.custom(90), "", 0, 3);
        assertFalse(cases.today.editStep.save(execution, opened.definition(), desired));
        TaskStepTemplate template = repository.steps.findTemplate(source);
        assertEquals("Neu", template.text); assertEquals("", template.note);
        assertEquals(3, template.intervalDays); assertEquals(0, template.weekdayMask);
        assertEquals(desired.prescription, template.prescription);
        OccurrenceStep changed = repository.steps.findOccurrenceStep(execution);
        assertEquals("Neu", changed.text); assertEquals("", changed.note);
        assertEquals(desired.prescription, changed.prescription); assertFalse(changed.done);
        assertEquals("Andere Ausführung", repository.steps.findOccurrenceStep("other").text);
        assertEquals(first.prescription, repository.steps.findOccurrenceStep("other").prescription);
        assertEquals(before, snapshot("tasks", "task_schedule_entries", "stats", "reward_bookings", "timer_sessions"));
    }

    @Test public void progressArrivingAfterOpenDefersPrescriptionAndKeepsEarnedHistory() {
        seed();
        EditableStep opened = cases.today.editStep.load(execution);
        assertFalse(opened.prescriptionProtected);
        cases.today.recordSetResult.execute(execution, new SetResult(10));
        List<String> before = snapshot("repetition_results", "reward_bookings", "reward_assignments", "stats", "combo_progress");
        assertTrue(cases.today.editStep.save(execution, opened.definition(),
                definition(opened.definition(), "Neu", StepAmount.duration(75), RestTimerPolicy.off(), "Neue Notiz", 0, 0)));
        OccurrenceStep changed = repository.steps.findOccurrenceStep(execution);
        assertEquals(StepAmount.setsReps(3, 12), changed.prescription.amount);
        assertEquals("Neu", changed.text); assertEquals("Neue Notiz", changed.note);
        assertEquals(List.of(10), changed.repetitionProgress.repetitions());
        assertEquals(StepAmount.duration(75), repository.steps.findTemplate(source).prescription.amount);
        assertEquals(before, snapshot("repetition_results", "reward_bookings", "reward_assignments", "stats", "combo_progress"));
    }

    @Test public void completedExecutionKeepsItsCompletionAndPrescription() {
        seed(); cases.today.finishStepForToday.execute(execution);
        EditableStep opened = cases.today.editStep.load(execution);
        assertTrue(opened.prescriptionProtected);
        assertTrue(cases.today.editStep.save(execution, opened.definition(),
                definition(opened.definition(), "Erledigt", StepAmount.none(), RestTimerPolicy.off(), "", 0, 0)));
        OccurrenceStep changed = repository.steps.findOccurrenceStep(execution);
        assertTrue(changed.done); assertEquals(StepAmount.setsReps(3, 12), changed.prescription.amount);
    }

    @Test public void runningPausedAndFinishedTimersAreNeverRewritten() {
        seed();
        for (String state : List.of("RUNNING", "PAUSED", "FINISHED")) {
            db.timers().put(new TimerSessionEntity("timer", execution, "Rudern", "REST", state,
                    60, 32000, 90000, 120000, 1234, false));
            EditableStep opened = cases.today.editStep.load(execution);
            List<String> before = snapshot("timer_sessions");
            assertTrue(opened.prescriptionProtected);
            TaskStepDefinition desired = definition(opened.definition(), "Timer-Schritt", StepAmount.setsReps(4, 8),
                    RestTimerPolicy.custom(90), state, 0, 0);
            // Ensure a new prescription change on every iteration.
            desired = definition(desired, desired.text, StepAmount.setsReps(4 + state.length(), 8),
                    RestTimerPolicy.custom(90 + state.length()), state, 0, 0);
            assertTrue(cases.today.editStep.save(execution, opened.definition(), desired));
            assertEquals(before, snapshot("timer_sessions"));
            assertEquals(StepAmount.setsReps(3, 12), repository.steps.findOccurrenceStep(execution).prescription.amount);
        }
    }

    @Test public void flowCandidateEditsTemplateWithoutCreatingRunOrOccurrence() {
        TaskId task = seedFlow();
        EditableStep opened = cases.today.editStep.load(source);
        assertFalse(cases.today.editStep.save(source, opened.definition(),
                definition(opened.definition(), "Start neu", StepAmount.duration(30), RestTimerPolicy.off(), "", 0, 0)));
        assertEquals("Start neu", repository.steps.findTemplate(source).text);
        assertEquals(0, count("step_flow_runs")); assertEquals(0, count("occurrence_steps"));
        repository.transactions.inTransaction(() -> {
            assertEquals("Start neu", repository.graphDefinitions.find(task).nodes.get(source).title);
            return null;
        });
    }

    @Test public void availableFlowStepChangesLinkedSnapshotAndExecutionWithoutMovingRuntime() {
        TaskId task = seedFlow();
        cases.today.materializeDue.execute();
        String candidate = value("SELECT id FROM flow_candidates WHERE taskId='" + task.value + "'");
        cases.flows.startFlowCandidate.execute(candidate, null);
        String runtime = value("SELECT id FROM flow_run_steps WHERE state='AVAILABLE'");
        assertNotNull(runtime);
        String target = value("SELECT id FROM occurrence_steps WHERE flowRunStepId='" + runtime + "'");
        EditableStep opened = cases.today.editStep.load(target == null ? runtime : target);
        List<String> before = runtimeState();
        assertFalse(opened.prescriptionProtected);
        assertFalse(cases.today.editStep.save(opened.id, opened.definition(),
                definition(opened.definition(), "Folge neu", StepAmount.duration(45), RestTimerPolicy.off(), "Neu", 0, 0)));
        assertEquals("Folge neu", value("SELECT text FROM flow_run_steps WHERE id='" + runtime + "'"));
        assertEquals("45", value("SELECT plannedDurationSeconds FROM flow_run_steps WHERE id='" + runtime + "'"));
        if (target != null) assertEquals(StepAmount.duration(45), repository.steps.findOccurrenceStep(target).prescription.amount);
        assertEquals(before, runtimeState());
    }

    @Test public void editingActedFlowStepPreservesItsFrozenRuntimePrescription() {
        seedFlow(); cases.today.materializeDue.execute();
        String candidate = value("SELECT id FROM flow_candidates LIMIT 1");
        cases.flows.startFlowCandidate.execute(candidate, null);
        String runtime = value("SELECT id FROM flow_run_steps WHERE state='DONE' LIMIT 1");
        EditableStep opened = cases.today.editStep.load(runtime);
        List<String> before = runtimeState();
        assertTrue(cases.today.editStep.save(runtime, opened.definition(),
                definition(opened.definition(), "Start neu", StepAmount.duration(20), RestTimerPolicy.off(), "Notiz", 0, 0)));
        assertEquals("NONE", value("SELECT amountKind FROM flow_run_steps WHERE id='" + runtime + "'"));
        assertEquals("Start neu", value("SELECT text FROM flow_run_steps WHERE id='" + runtime + "'"));
        assertEquals(before, runtimeState());
    }

    @Test public void missingSourceIsNotRecreatedAndNoteShortcutStillWorks() {
        seed(); db.steps().deleteTemplate(source);
        assertThrows(IllegalArgumentException.class, () -> cases.today.editStep.load(execution));
        cases.today.editStepNote.save(execution, "Nur Notiz");
        assertEquals(0, count("task_steps"));
        assertEquals("Nur Notiz", repository.steps.findOccurrenceStep(execution).note);
    }

    @Test public void disappearedTargetFailsWithoutChangingItsSource() {
        seed(); EditableStep opened = cases.today.editStep.load(execution);
        db.steps().deleteOccurrenceStep(execution);
        List<String> before = snapshot("task_steps");
        assertThrows(IllegalArgumentException.class, () -> cases.today.editStep.save(execution, opened.definition(),
                definition(opened.definition(), "Verloren", StepAmount.none(), RestTimerPolicy.off(), "", 0, 0)));
        assertEquals(before, snapshot("task_steps"));
    }

    @Test public void executionWriteFailureRollsBackTemplateEdit() {
        seed(); EditableStep opened = cases.today.editStep.load(execution);
        List<String> before = snapshot("task_steps", "occurrence_steps");
        db.getOpenHelper().getWritableDatabase().execSQL("CREATE TRIGGER reject_edit BEFORE UPDATE OF text ON occurrence_steps BEGIN SELECT RAISE(ABORT,'injected'); END");
        assertThrows(RuntimeException.class, () -> cases.today.editStep.save(execution, opened.definition(),
                definition(opened.definition(), "Nicht speichern", StepAmount.none(), RestTimerPolicy.off(), "", 0, 0)));
        assertEquals(before, snapshot("task_steps", "occurrence_steps"));
    }

    @Test public void untouchedFieldsRetainConcurrentChanges() {
        seed(); EditableStep opened = cases.today.editStep.load(execution);
        cases.today.editStepNote.save(execution, "Zwischenzeitliche Notiz");
        TaskStepDefinition desired = definition(opened.definition(), "Neuer Name", opened.source.prescription.amount,
                opened.source.prescription.rest, opened.note, 0, 0);
        cases.today.editStep.save(execution, opened.definition(), desired);
        assertEquals("Zwischenzeitliche Notiz", repository.steps.findOccurrenceStep(execution).note);
        assertEquals("Zwischenzeitliche Notiz", repository.steps.findTemplate(source).note);
    }

    @Test public void savedDraftKeepsBaselineInvalidInputsAndNeverRestoresSaving() {
        seed(); StepEditDraft opened = StepEditDraft.from(cases.today.editStep.load(execution));
        EditorStepState invalid = opened.step.withText("").withIntervalDays(null).withAmount(StepAmount.duration(0));
        StepEditDraft changed = opened.change(invalid).status("Fehler", true);
        Bundle bundle = changed.saved();
        StepEditDraft restored = StepEditDraft.restore(bundle);
        assertEquals(opened.original, restored.original); assertEquals(invalid, restored.step);
        assertFalse(restored.saving); assertTrue(restored.validated);
        assertEquals(3, StepEditorValidator.issues(restored.step, true).size());
        assertEquals(2, StepEditorValidator.issues(restored.step, false).size());
        assertEquals("Fehler", restored.error);
    }

    @Test public void todayOwnerValidatesSavesAndKeepsDraftAcrossOtherScreenUpdates() {
        seed(); mount(new SavedStateHandle());
        model.dispatch(TodayAction.editStep(execution)); idle();
        StepEditDraft draft = model.state().getValue().stepEditDraft;
        assertNotNull(draft);
        model.changeStepEdit(draft.step.withText(""));
        model.saveStepEdit(); idle();
        assertTrue(model.state().getValue().stepEditDraft.validated);
        assertFalse(model.state().getValue().stepEditDraft.saving);
        assertEquals("Rudern", repository.steps.findTemplate(source).text);
        model.changeStepEdit(draft.step.withText("Owner gespeichert"));
        model.saveStepEdit(); idle();
        assertNull(model.state().getValue().stepEditDraft);
        assertEquals("Owner gespeichert", repository.steps.findTemplate(source).text);
        assertEquals("Owner gespeichert", repository.steps.findOccurrenceStep(execution).text);
    }

    @Test public void restoredOwnerKeepsUnsavedDraftAndCancelWritesNothing() {
        seed(); SavedStateHandle handle = new SavedStateHandle(); mount(handle);
        model.dispatch(TodayAction.editStep(execution)); idle();
        model.changeStepEdit(model.state().getValue().stepEditDraft.step.withText("Entwurf"));
        Map<String, Object> persisted = new HashMap<>();
        for (String key : handle.keys()) persisted.put(key, handle.get(key));
        model.onCleared(); invalidations.close();
        mount(new SavedStateHandle(persisted));
        assertEquals("Entwurf", model.state().getValue().stepEditDraft.step.text);
        model.cancelStepEdit();
        assertNull(model.state().getValue().stepEditDraft);
        assertEquals("Rudern", repository.steps.findTemplate(source).text);
    }

    @Test public void failedSaveLeavesEditableDraftOpen() {
        seed(); mount(new SavedStateHandle());
        model.dispatch(TodayAction.editStep(execution)); idle();
        model.changeStepEdit(model.state().getValue().stepEditDraft.step.withText("Noch offen"));
        db.steps().deleteTemplate(source);
        model.saveStepEdit(); idle();
        StepEditDraft draft = model.state().getValue().stepEditDraft;
        assertNotNull(draft); assertFalse(draft.saving);
        assertFalse(draft.error.isEmpty()); assertEquals("Noch offen", draft.step.text);
        model.cancelStepEdit();
    }

    @Test public void invalidDraftCannotCrossTheDomainSaveBoundary() {
        seed(); EditorStepState step = StepEditDraft.from(cases.today.editStep.load(execution)).step;
        EditorStepState invalid = step.withAmount(StepAmount.setsReps(0, 12));
        assertThrows(IllegalArgumentException.class, () -> invalid.definition(0, false));
        assertThrows(IllegalArgumentException.class, () -> new TaskStepDefinition(source, 0, "Invalid", 0, 0,
                invalid.prescription, "", StepActivationKind.SCHEDULED));
        assertEquals(invalid, EditorStepState.fromBundle(invalid.toBundle()));
    }

    private void mount(SavedStateHandle handle) {
        Context context = ApplicationProvider.getApplicationContext();
        AppLogger logger = new AppLogger() {
            public void info(String tag, String message) { }
            public void error(String tag, String message, Throwable error) { }
        };
        CalendarDataSource calendar = new CalendarDataSource() {
            public CalendarResult loadToday() { return new CalendarResult.Success(List.of()); }
            public Subscription observeChanges(Runnable observer) { return () -> { }; }
        };
        UiPreferences preferences = new UiPreferences(context, logger);
        invalidations = new PresentationInvalidationSource(new DatabaseInvalidationSource(db),
                new CalendarInvalidationSource(calendar), new PreferenceInvalidationSource(preferences),
                new ClockInvalidationSource(clock, observer -> () -> { }), Runnable::run);
        AndroidUiTextProvider texts = new AndroidUiTextProvider(context);
        DashboardPresenter presenter = new DashboardPresenter(clock, cases.today.loadDashboard,
                cases.today.materializeDue, new DashboardUiMapper(texts));
        model = new TodayViewModel(cases.today, cases.catalog, presenter, calendar, preferences, clock,
                logger, texts, invalidations, handle, new DirectExecutor(), Runnable::run);
    }
    private void idle() { ShadowLooper.idleMainLooper(); }
    private static final class DirectExecutor extends AbstractExecutorService {
        private boolean stopped;
        public void shutdown() { stopped = true; }
        public List<Runnable> shutdownNow() { stopped = true; return List.of(); }
        public boolean isShutdown() { return stopped; }
        public boolean isTerminated() { return stopped; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return stopped; }
        public void execute(Runnable task) { task.run(); }
    }

    private TaskId seed() {
        TaskStepDefinition step = new TaskStepDefinition(null, 0, "Rudern", 0, 0,
                StepPrescription.forAmount(StepAmount.setsReps(3, 12)), "40 kg", StepActivationKind.SCHEDULED);
        TaskId task = cases.catalog.create.execute(new TaskDefinition("Training", null, TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER,
                null, null, null, null, "", List.of(step)));
        source = db.steps().templates(task.value).get(0).id;
        cases.today.materializeDue.execute();
        execution = value("SELECT id FROM occurrence_steps WHERE sourceTemplateId='" + source + "'");
        assertNotNull(execution); return task;
    }
    private TaskId seedFlow() {
        List<TaskStepDefinition> steps = List.of(
                new TaskStepDefinition(null, 0, "Start", 0, 0, StepPrescription.forAmount(StepAmount.none()), "", StepActivationKind.SCHEDULED),
                new TaskStepDefinition(null, 1, "Folge", 0, 0, StepPrescription.forAmount(StepAmount.none()), "", StepActivationKind.FOLLOW_UP));
        TaskId task = cases.flows.saveGraph.execute(new FlowGraphEdit(null, "Ablauf",
                new FlowTileGraph(List.of("a", "b"), List.of(new FlowTileGraph.Link("a", "b"))),
                steps, new LinkedHashMap<>(Map.of("a", FlowDelayPolicy.fixed(0), "b", FlowDelayPolicy.fixed(0))), List.of(), List.of()));
        source = db.steps().templates(task.value).get(0).id;
        return task;
    }
    private TaskStepDefinition definition(TaskStepDefinition old, String text, StepAmount amount,
                                           RestTimerPolicy rest, String note, int weekdays, int interval) {
        return new TaskStepDefinition(old.id, old.position, text, weekdays, interval,
                new StepPrescription(amount, rest), note, old.activationKind);
    }
    private int count(String table) { return Integer.parseInt(value("SELECT COUNT(*) FROM " + table)); }
    private String value(String sql) {
        try (Cursor cursor = db.getOpenHelper().getReadableDatabase().query(sql)) {
            return cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getString(0) : null;
        }
    }
    private List<String> runtimeState() {
        List<String> rows = snapshot("step_flow_runs", "flow_run_resources", "flow_run_edges");
        try (Cursor cursor = db.getOpenHelper().getReadableDatabase().query(
                "SELECT id,state,readyAtEpochMillis,actionAtEpochMillis,earnedTau,chosenDelayMillis FROM flow_run_steps ORDER BY id")) {
            while (cursor.moveToNext()) for (int i = 0; i < cursor.getColumnCount(); i++) rows.add(cursor.getString(i));
        }
        return rows;
    }
    private List<String> snapshot(String... tables) {
        List<String> rows = new ArrayList<>();
        for (String table : tables) try (Cursor cursor = db.getOpenHelper().getReadableDatabase().query("SELECT * FROM " + table + " ORDER BY rowid")) {
            rows.add(table);
            while (cursor.moveToNext()) for (int i = 0; i < cursor.getColumnCount(); i++) rows.add(cursor.getString(i));
        }
        return rows;
    }
}
