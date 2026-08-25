package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.alltasks.AllTasksAction;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksPresentationState;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksRequest;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksSavedStateAdapter;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksUiState;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksViewModel;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.presentation.today.TodayAction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.calendar.CalendarDataSource;
import de.thonktank.autosecretary.calendar.CalendarResult;
import de.thonktank.autosecretary.data.local.RoomTaskRepository;
import de.thonktank.autosecretary.data.local.RoomInvalidationSource;
import de.thonktank.autosecretary.data.observable.CalendarInvalidationSource;
import de.thonktank.autosecretary.data.observable.ClockInvalidationSource;
import de.thonktank.autosecretary.data.observable.PreferenceInvalidationSource;
import de.thonktank.autosecretary.data.preferences.UiPreferences;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.ScheduleEntryId;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.domain.repository.ApplicationTaskRepository;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.TaskUseCases;
import de.thonktank.autosecretary.domain.schedule.ScheduleMoveRequest;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.DashboardPresenter;
import de.thonktank.autosecretary.presentation.DashboardUiMapper;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidationSource;
import de.thonktank.autosecretary.update.presentation.UpdateUiState;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class PresentationStateRobolectricTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 15);

    @Rule public final TestRule instantExecutors = new InstantTaskExecutorRule();

    private Context context;
    private AppDatabase database;
    private ApplicationTaskRepository repository;
    private TaskUseCases tasks;
    private DashboardPresenter presenter;
    private UiPreferences preferences;
    private final FixedClock clock = new FixedClock();
    private final RecordingLogger logger = new RecordingLogger();
    private final AtomicInteger ids = new AtomicInteger();
    private TaskViewModel viewModel;
    private PresentationInvalidationSource invalidationSource;
    private CalendarInvalidationSource calendarInvalidations;
    private final List<String> databaseQueries = new CopyOnWriteArrayList<>();

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteSharedPreferences("forest_ui");
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .setQueryExecutor(Runnable::run)
                .setTransactionExecutor(Runnable::run)
                .setQueryCallback((sql, arguments) -> databaseQueries.add(sql), Runnable::run)
                .build();
        repository = new RoomTaskRepository(database);
        IdGenerator idGenerator = () -> "presentation-" + ids.incrementAndGet();
        tasks = new TaskUseCases(repository, clock, idGenerator);
        presenter = new DashboardPresenter(clock, tasks.loadDashboard, tasks.materializeDue,
                new DashboardUiMapper(new AndroidUiTextProvider(context)));
        preferences = new UiPreferences(context, logger);
    }

    @After public void tearDown() {
        if (viewModel != null) viewModel.onCleared();
        if (invalidationSource != null) invalidationSource.close();
        database.close();
        context.deleteSharedPreferences("forest_ui");
    }

    @Test public void composerMergesCalendarAndTasksOutsideTheActivity() {
        TodayUiModel model = TodayUiModel.compose(
                DashboardFixtures.fullDashboard(), DashboardFixtures.calendarEvents());

        assertEquals(5, model.timeline.size());
        assertEquals("Urlaub", model.timeline.get(0).event.title);
        assertTrue(model.timeline.stream().filter(item -> item.task != null)
                .noneMatch(item -> model.completedToday.stream().anyMatch(done ->
                        done.occurrenceId.equals(item.task.occurrenceId))));
        assertEquals(1, model.completedToday.size());
        assertEquals("occurrence-done", model.completedToday.get(0).occurrenceId);
        assertEquals("steps", model.focus.taskId());
    }

    @Test public void presentationCollectionsCannotBeMutated() {
        TodayUiModel model = TodayUiModel.compose(
                DashboardFixtures.fullDashboard(), DashboardFixtures.calendarEvents());
        try {
            model.completedToday.clear();
            fail("completed history must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertFalse(model.completedToday.isEmpty());
        }
        try {
            model.timeline.clear();
            fail("timeline must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertFalse(model.timeline.isEmpty());
        }
    }

    @Test public void navigationAndOpenEditorRestoreFromSavedState() throws Exception {
        SavedStateHandle handle = new SavedStateHandle();
        viewModel = newViewModel(handle);
        assertFalse(value().loading);

        TaskId migratedId = TaskId.of("migrated-ongoing");
        repository.insertTask(Task.restore(migratedId, "Bearbeitbar", Recurrence.INTERVAL,
                4, 0, true, "Fertig", false, false, clock.today(), null, null,
                clock.today(), 1_024L, false, null, TaskBoundKind.FOREVER, null, null,
                null, null, ""));
        repository.insertTemplates(java.util.Arrays.asList(
                new de.thonktank.autosecretary.domain.model.TaskStepTemplate(
                        "ongoing-a", migratedId, 0, "A"),
                new de.thonktank.autosecretary.domain.model.TaskStepTemplate(
                        "ongoing-b", migratedId, 1, "B")));
        repository.putScheduleEntries(Collections.singletonList(
                new de.thonktank.autosecretary.domain.model.TaskScheduleEntry(
                        "ongoing-schedule", migratedId, TaskSlot.EVENING, 1_024L)));
        String taskId = repository.allTasks().get(0).id.value;

        viewModel.navigate(NavigationDestination.OPTIONS);
        viewModel.openEditor(taskId);
        assertTrue(value().editor.open && !value().editor.loading);
        assertEquals("Bearbeitbar", value().editor.title);
        assertEquals(4, value().editor.intervalDays);
        assertEquals(2, value().editor.stepStates.size());
        viewModel.onCleared();

        viewModel = newViewModel(handle);
        assertEquals(NavigationDestination.OPTIONS, value().navigation);
        assertTrue(value().editor.open);
        assertEquals(taskId, value().editor.taskId);
    }

    @Test public void duplicateCommandsAreIgnoredWhileTheFirstIsRunning() throws Exception {
        ManualExecutor worker = new ManualExecutor();
        viewModel = newViewModel(new SavedStateHandle(), worker);
        worker.runAll();
        assertFalse(value().loading);

        EditorUiState draft = EditorUiState.create().withDraft("Einmal", TaskSlot.MORNING,
                Recurrence.ONCE, 1, 0, Collections.emptyList());
        viewModel.saveEditor(draft);
        viewModel.saveEditor(draft);

        worker.runAll();
        assertFalse(value().isRunning(new UiCommand(UiCommand.Kind.CREATE, "new")));
        assertEquals(1, repository.allTasks().size());
    }

    @Test public void refreshCompletesForTheRecreatedLifecycleOwnerWithoutDuplicateWork() {
        ManualExecutor worker = new ManualExecutor();
        AtomicInteger calendarLoads = new AtomicInteger();
        viewModel = newViewModel(new SavedStateHandle(), worker, calendarLoads);
        assertTrue(value().loading);
        assertEquals(1, worker.pendingCount());

        RecordingLifecycleOwner first = new RecordingLifecycleOwner();
        List<Boolean> firstStates = new ArrayList<>();
        viewModel.state().observe(first, state -> firstStates.add(state.loading));
        assertEquals(Collections.singletonList(true), firstStates);

        first.destroy();
        RecordingLifecycleOwner recreated = new RecordingLifecycleOwner();
        List<Boolean> recreatedStates = new ArrayList<>();
        viewModel.state().observe(recreated, state -> recreatedStates.add(state.loading));

        worker.runAll();

        assertFalse(value().loading);
        assertEquals(1, calendarLoads.get());
        assertEquals(0, worker.pendingCount());
        assertEquals(Collections.singletonList(true), firstStates);
        assertTrue(recreatedStates.get(0));
        assertFalse(recreatedStates.get(recreatedStates.size() - 1));
        assertEquals(1, Collections.frequency(recreatedStates, false));
        recreated.destroy();
    }

    @Test public void editorValidationErrorsStayInTypedEditorState() throws Exception {
        viewModel = newViewModel(new SavedStateHandle());
        assertFalse(value().loading);

        viewModel.saveEditor(EditorUiState.create());

        assertFalse(value().editor.issues.isEmpty());
        assertFalse(value().editor.saving);
    }

    @Test public void repetitionDraftsStayLocalUntilSubmissionPersistsThem() {
        viewModel = newViewModel(new SavedStateHandle(), new DirectExecutor());
        TaskDefinition definition = new TaskDefinition("Gym", null, TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER,
                null, null, null, null, "", Collections.singletonList(
                        new TaskStepDefinition(null, 0, "Kniebeugen", 0,
                                StepAmount.setsReps(3, 12), "")));
        tasks.create.execute(definition);
        refreshDatabase();
        String stepId = value().dashboard.focus.steps.get(0).id;

        viewModel.dispatchToday(TodayAction.adjustRepetition(stepId, 1));
        viewModel.dispatchToday(TodayAction.adjustRepetition(stepId, 1));

        assertEquals(14, value().repetitionInput.value);
        assertTrue(repository.findOccurrenceStep(stepId).repetitionProgress.actualRepetitions
                .isEmpty());

        viewModel.dispatchToday(TodayAction.submitRepetition(stepId));

        assertNull(value().repetitionInput.stepId);
        assertEquals(Collections.singletonList(14),
                repository.findOccurrenceStep(stepId).repetitionProgress.actualRepetitions);
    }

    @Test public void displayPreferencesAndUpdateStatusJoinStateWithoutReloadingContent() {
        viewModel = newViewModel(new SavedStateHandle(), new DirectExecutor());

        calendarInvalidations.materializeExternalChange();
        TodayUiModel dashboardBefore = value().dashboard;

        preferences.setFocusStepLimit(FocusStepLimit.THREE);

        assertEquals(FocusStepLimit.THREE, value().focusStepLimit);
        assertSame(dashboardBefore, value().dashboard);

        preferences.setThemeMode(UiThemeMode.DARK);
        assertEquals(UiThemeMode.DARK, value().themeMode);
        assertEquals(DayPalette.at(clock.time(), DayPalette.Mode.DARK).background,
                value().palette.background);
        assertSame(dashboardBefore, value().dashboard);

        viewModel.updateUpdateState(UpdateUiState.checking());
        assertEquals(UpdateUiState.Status.CHECKING, value().update.status);
        assertSame(dashboardBefore, value().dashboard);
    }

    @Test public void todayRefreshDoesNotExecuteTheManagementCatalogQuery() {
        viewModel = newViewModel(new SavedStateHandle());
        databaseQueries.clear();

        calendarInvalidations.materializeExternalChange();

        long unfilteredTaskInventoryReads = databaseQueries.stream()
                .map(value -> value.trim().toLowerCase(java.util.Locale.ROOT))
                .filter(value -> value.equals("select * from tasks"))
                .count();
        assertEquals("Today may load tasks once for its dashboard, but not again for Alles",
                1L, unfilteredTaskInventoryReads);
    }

    @Test public void todayReorderUsesOneRoomDrivenDashboardReadAndSkipsCalendar() {
        AtomicInteger calendarLoads = new AtomicInteger();
        viewModel = newViewModel(new SavedStateHandle(), new DirectExecutor(),
                calendarLoads);
        tasks.create.execute(new TaskDefinition("Reihenfolge", null, TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER,
                null, null, null, null, "", java.util.Arrays.asList(
                new TaskStepDefinition(null, 0, "A", 0, StepAmount.none(), ""),
                new TaskStepDefinition(null, 1, "B", 0, StepAmount.none(), ""),
                new TaskStepDefinition(null, 2, "C", 0, StepAmount.none(), ""))));
        refreshDatabase();
        List<de.thonktank.autosecretary.presentation.today.FocusStepUiModel> steps =
                value().dashboard.focus.steps;
        String first = steps.get(0).id;
        databaseQueries.clear();
        calendarLoads.set(0);

        viewModel.moveTodayStep(first, null);

        assertEquals(0, calendarLoads.get());
        assertEquals(first, value().dashboard.focus.steps.get(2).id);
        long dashboardReads = databaseQueries.stream()
                .map(sql -> sql.trim().toLowerCase(java.util.Locale.ROOT))
                .filter(sql -> sql.equals("select * from tasks"))
                .count();
        assertEquals(1L, dashboardReads);
    }

    @Test public void completionReloadsOnlyTodayAndPreservesSiblingPresentationState() {
        AtomicInteger calendarLoads = new AtomicInteger();
        viewModel = newViewModel(new SavedStateHandle(), new DirectExecutor(), calendarLoads);
        tasks.create.execute(TaskDefinition.basic("Abschließen", TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, Collections.emptyList()));
        refreshDatabase();
        viewModel.openEditor(null);
        preferences.setFocusStepLimit(FocusStepLimit.THREE);
        CalendarUiState calendarBefore = value().calendar;
        EditorUiState editorBefore = value().editor;
        String occurrenceId = value().dashboard.focus.occurrenceId();
        calendarLoads.set(0);

        viewModel.complete(occurrenceId);

        assertEquals(0, calendarLoads.get());
        assertSame(calendarBefore, value().calendar);
        assertSame(editorBefore, value().editor);
        assertEquals(FocusStepLimit.THREE, value().focusStepLimit);
        assertTrue(value().dashboard.completedToday.stream()
                .anyMatch(done -> done.occurrenceId.equals(occurrenceId)));
    }

    @Test public void managementViewModelOwnsCatalogAndRestoresOnlyThroughSavedStateAdapter() {
        SavedStateHandle handle = new SavedStateHandle();
        AllTasksViewModel management = newAllTasksViewModel(handle);

        management.dispatch(AllTasksAction.queryChanged("Gym"));
        management.dispatch(AllTasksAction.statusChanged(AllTasksUiState.Status.ALL));
        management.dispatch(AllTasksAction.slotsChanged(
                java.util.EnumSet.of(TaskSlot.EVENING)));
        management.dispatch(AllTasksAction.recurrencesChanged(
                java.util.EnumSet.of(Recurrence.WEEKDAYS)));
        management.dispatch(AllTasksAction.weekdayChanged(4));
        management.dispatch(AllTasksAction.modeChanged(AllTasksUiState.Mode.SORT));
        String cardKey = AllTasksUiState.cardKey("rotation-task", TaskSlot.MORNING);
        management.dispatch(AllTasksAction.cardToggled(cardKey));
        management.dispatch(AllTasksAction.filtersExpandedChanged(false));

        android.os.Bundle stored = handle.get("all_tasks_filter");
        AllTasksPresentationState restored = new AllTasksSavedStateAdapter().decode(stored);
        assertEquals("Gym", restored.filter.query);
        assertEquals(AllTasksUiState.Status.ACTIVE, restored.filter.status);
        assertEquals(java.util.EnumSet.of(TaskSlot.EVENING), restored.filter.slots);
        assertEquals(java.util.EnumSet.of(Recurrence.WEEKDAYS), restored.filter.recurrences);
        assertEquals(4, restored.filter.weekday);
        assertEquals(AllTasksUiState.Mode.SORT, restored.mode);
        assertTrue(restored.expandedCardKeys.contains(cardKey));
        assertFalse(restored.filtersExpanded);
        assertNotNull(management.state().getValue());
        management.onCleared();

        AllTasksViewModel afterRotation = newAllTasksViewModel(handle);
        assertEquals("Gym", afterRotation.state().getValue().content.query);
        assertEquals(AllTasksUiState.Mode.SORT, afterRotation.state().getValue().content.mode);
        assertTrue(afterRotation.state().getValue().content.expandedCardKeys.contains(cardKey));
        assertFalse(afterRotation.state().getValue().content.filtersExpanded);
        afterRotation.onCleared();
    }

    @Test public void managementCommandIsReprojectedThroughRoomWithoutABrokerSignal() {
        tasks.create.execute(TaskDefinition.basic("Sortieren", TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, Collections.emptyList()));
        de.thonktank.autosecretary.domain.model.Task task = repository.allTasks().get(0);
        String entryId = repository.scheduleEntries(task.id).get(0).id;
        AllTasksViewModel management = newAllTasksViewModel(new SavedStateHandle());

        management.dispatch(AllTasksAction.scheduleMoved(new ScheduleMoveRequest(
                ScheduleEntryId.of(entryId), TaskSlot.EVENING, java.util.Optional.empty())));

        assertEquals(TaskSlot.EVENING, repository.scheduleEntries(task.id).get(0).slot);
        assertEquals(TaskSlot.EVENING,
                management.state().getValue().content.schedule.get(0).slot);
        management.onCleared();
    }

    @Test public void managementRequestSurvivesRecreationUntilExplicitConfirmation() {
        tasks.create.execute(TaskDefinition.basic("Löschen", TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, Collections.emptyList()));
        Task task = repository.allTasks().get(0);
        SavedStateHandle handle = new SavedStateHandle();
        AllTasksViewModel management = newAllTasksViewModel(handle);

        management.dispatch(AllTasksAction.deleteRequested(task.id, task.title));

        AllTasksRequest pending = management.state().getValue().firstRequest();
        assertNotNull(pending);
        assertEquals(AllTasksRequest.Kind.CONFIRM_DELETE, pending.kind);
        assertNotNull(handle.get("all_tasks_requests"));
        handle.set("all_tasks_request_sequence", 0L);
        management.onCleared();

        AllTasksViewModel recreated = newAllTasksViewModel(handle);
        AllTasksRequest restored = recreated.state().getValue().firstRequest();
        assertNotNull(restored);
        assertEquals(pending.id, restored.id);
        assertEquals(task.id, restored.taskId);

        recreated.dispatch(AllTasksAction.editTask(task.id));
        assertEquals(2, recreated.state().getValue().requests.size());
        assertFalse(pending.id.equals(recreated.state().getValue().requests.get(1).id));

        recreated.dispatch(AllTasksAction.confirmDelete(restored.id));

        assertEquals(AllTasksRequest.Kind.OPEN_EDITOR,
                recreated.state().getValue().firstRequest().kind);
        assertTrue(repository.allTasks().stream().noneMatch(value -> value.id.equals(task.id)));
        recreated.onCleared();
    }

    @Test public void acknowledgingHostRequestPreservesTheExactRenderProjection() {
        AllTasksViewModel management = newAllTasksViewModel(new SavedStateHandle());
        AllTasksUiState content = management.state().getValue().content;

        management.dispatch(AllTasksAction.editTask(TaskId.of("request-task")));
        management.dispatch(AllTasksAction.editTask(TaskId.of("request-task")));
        AllTasksRequest request = management.state().getValue().firstRequest();
        assertNotNull(request);
        assertEquals(1, management.state().getValue().requests.size());
        assertSame(content, management.state().getValue().content);

        management.dispatch(AllTasksAction.acknowledgeRequest(request.id));

        assertNull(management.state().getValue().firstRequest());
        assertSame(content, management.state().getValue().content);
        management.onCleared();
    }

    @Test public void oneRoomWriteReprojectsDashboardAndCatalogWithoutCrossSignals() {
        viewModel = newViewModel(new SavedStateHandle());
        AllTasksViewModel management = newAllTasksViewModel(new SavedStateHandle());

        tasks.create.execute(TaskDefinition.basic("Gemeinsame Wahrheit", TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, Collections.emptyList()));
        refreshDatabase();

        assertTrue((value().dashboard.focus != null
                        && "Gemeinsame Wahrheit".equals(value().dashboard.focus.title()))
                || value().dashboard.timeline.stream().anyMatch(item -> item.task != null
                        && "Gemeinsame Wahrheit".equals(item.task.title)));
        assertTrue(management.state().getValue().content.tasks.stream()
                .anyMatch(item -> "Gemeinsame Wahrheit".equals(item.task.title)));
        management.onCleared();
    }

    private TaskViewModel newViewModel(SavedStateHandle handle) {
        return newViewModel(handle, new DirectExecutor());
    }

    private TaskViewModel newViewModel(SavedStateHandle handle,
                                       AbstractExecutorService worker) {
        return newViewModel(handle, worker, new AtomicInteger());
    }

    private TaskViewModel newViewModel(SavedStateHandle handle,
                                       AbstractExecutorService worker,
                                       AtomicInteger calendarLoads) {
        CalendarDataSource calendar = new CalendarDataSource() {
            @Override public CalendarResult loadToday() {
                calendarLoads.incrementAndGet();
                return new CalendarResult.Success(DashboardFixtures.calendarEvents());
            }

            @Override public Subscription observeChanges(Runnable observer) {
                return () -> { };
            }
        };
        if (invalidationSource != null) invalidationSource.close();
        calendarInvalidations = new CalendarInvalidationSource(calendar);
        invalidationSource = new PresentationInvalidationSource(
                new RoomInvalidationSource(database), calendarInvalidations,
                new PreferenceInvalidationSource(preferences),
                new ClockInvalidationSource(clock, observer -> () -> { }), Runnable::run);
        return new TaskViewModel(tasks, presenter, calendar, preferences, clock, logger,
                new AndroidUiTextProvider(context), invalidationSource, handle, worker,
                Runnable::run);
    }

    private AllTasksViewModel newAllTasksViewModel(SavedStateHandle handle) {
        if (invalidationSource == null) {
            CalendarDataSource calendar = new CalendarDataSource() {
                @Override public CalendarResult loadToday() {
                    return new CalendarResult.Success(Collections.emptyList());
                }
                @Override public Subscription observeChanges(Runnable observer) {
                    return () -> { };
                }
            };
            calendarInvalidations = new CalendarInvalidationSource(calendar);
            invalidationSource = new PresentationInvalidationSource(
                    new RoomInvalidationSource(database), calendarInvalidations,
                    new PreferenceInvalidationSource(preferences),
                    new ClockInvalidationSource(clock, observer -> () -> { }), Runnable::run);
        }
        return new AllTasksViewModel(tasks.loadTaskCatalog, tasks.moveScheduleEntry,
                tasks.moveTaskStep, tasks.swapTaskSteps, tasks.delete,
                new AndroidUiTextProvider(context), handle, new DirectExecutor(),
                invalidationSource, Runnable::run);
    }

    private void refreshDatabase() {
        database.getInvalidationTracker().refreshVersionsSync();
    }

    private DashboardUiState value() {
        return viewModel.state().getValue();
    }

    private static class DirectExecutor extends AbstractExecutorService {
        private boolean shutdown;
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }
        @Override public void execute(Runnable command) {
            if (shutdown) throw new IllegalStateException("Executor is shut down");
            command.run();
        }
    }

    private static final class ManualExecutor extends DirectExecutor {
        private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
        @Override public void execute(Runnable command) { pending.add(command); }
        void runNext() { pending.remove().run(); }
        void runAll() {
            int remaining = 100;
            while (!pending.isEmpty() && remaining-- > 0) pending.remove().run();
            if (!pending.isEmpty()) throw new AssertionError("Executor did not become idle");
        }
        int pendingCount() { return pending.size(); }
    }

    private static final class RecordingLifecycleOwner implements LifecycleOwner {
        private final LifecycleRegistry lifecycle = new LifecycleRegistry(this);

        RecordingLifecycleOwner() {
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START);
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
        }

        @Override public Lifecycle getLifecycle() { return lifecycle; }

        void destroy() { lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY); }
    }

    private static final class FixedClock implements Clock {
        @Override public LocalDate today() { return TODAY; }
        @Override public LocalTime time() { return LocalTime.of(10, 15); }
    }

    private static final class RecordingLogger implements AppLogger {
        @Override public void info(String tag, String message) { }
        @Override public void error(String tag, String message, Throwable error) { }
    }
}
