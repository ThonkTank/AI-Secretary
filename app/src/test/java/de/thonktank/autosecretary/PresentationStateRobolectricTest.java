package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.alltasks.AllTasksAction;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksPresentationState;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksRequest;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksSavedStateAdapter;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksUiState;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksViewModel;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayScreenState;
import de.thonktank.autosecretary.presentation.today.TodayRequest;
import de.thonktank.autosecretary.presentation.today.TaskActionTarget;
import de.thonktank.autosecretary.presentation.today.TodayViewModel;
import de.thonktank.autosecretary.presentation.legacy.LegacyStateFlowBinder;
import de.thonktank.autosecretary.presentation.shell.AppShellAction;
import de.thonktank.autosecretary.presentation.shell.AppShellViewModel;

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
import de.thonktank.autosecretary.domain.model.TaskStepId;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.data.local.TaskStore;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.schedule.ScheduleMoveRequest;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.DashboardPresenter;
import de.thonktank.autosecretary.presentation.DashboardUiMapper;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidationSource;
import de.thonktank.autosecretary.presentation.navigation.AppDestination;
import de.thonktank.autosecretary.presentation.navigation.AppNavigator;
import de.thonktank.autosecretary.presentation.navigation.TaskEditorNavigator;

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
    private TaskStore repository;
    private ApplicationUseCaseComposition tasks;
    private DashboardPresenter presenter;
    private UiPreferences preferences;
    private final FixedClock clock = new FixedClock();
    private final RecordingLogger logger = new RecordingLogger();
    private final AtomicInteger ids = new AtomicInteger();
    private TodayViewModel viewModel;
    private TaskEditorViewModel editorViewModel;
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
        tasks = new ApplicationUseCaseComposition(repository, repository, repository, clock,
                idGenerator,
                de.thonktank.autosecretary.domain.repository.ComboPolicySource.defaults());
        presenter = new DashboardPresenter(clock, tasks.today.loadDashboard, tasks.today.materializeDue,
                new DashboardUiMapper(new AndroidUiTextProvider(context)));
        preferences = new UiPreferences(context, logger);
    }

    @After public void tearDown() {
        if (viewModel != null) viewModel.onCleared();
        if (editorViewModel != null) editorViewModel.onCleared();
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

    @Test public void navigationAndOpenEditorRestoreFromTheirOwnSavedStates() throws Exception {
        SavedStateHandle shellHandle = new SavedStateHandle();
        SavedStateHandle todayHandle = new SavedStateHandle();
        SavedStateHandle editorHandle = new SavedStateHandle();
        viewModel = newViewModel(todayHandle);
        editorViewModel = newEditorViewModel(editorHandle, new DirectExecutor());
        assertFalse(value().loading);

        TaskId migratedId = TaskId.of("migrated-ongoing");
        repository.insertTask(Task.restore(migratedId, "Bearbeitbar", Recurrence.INTERVAL,
                4, 0, true, "Fertig", false, false, clock.today(), null, null,
                clock.today(), 1_024L, false, null, TaskBoundKind.FOREVER, null, null,
                null, null, ""));
        repository.insertTemplates(java.util.Arrays.asList(
                de.thonktank.autosecretary.testing.StepTestFixtures.template(
                        "ongoing-a", migratedId, 0, "A"),
                de.thonktank.autosecretary.testing.StepTestFixtures.template(
                        "ongoing-b", migratedId, 1, "B")));
        repository.putScheduleEntries(Collections.singletonList(
                new de.thonktank.autosecretary.domain.model.TaskScheduleEntry(
                        "ongoing-schedule", migratedId, TaskSlot.EVENING, 1_024L)));
        String taskId = repository.allTasks().get(0).id.value;

        AppShellViewModel shell = newShellViewModel(shellHandle);
        shell.dispatch(AppShellAction.destinationSelected(NavigationDestination.OPTIONS));
        editorViewModel.dispatch(TaskEditorAction.open(taskId));
        assertTrue(editorValue().open && !editorValue().loading);
        assertEquals("Bearbeitbar", editorValue().title);
        assertEquals(4, editorValue().intervalDays);
        assertEquals(2, editorValue().stepStates.size());
        viewModel.onCleared();
        shell.onCleared();
        editorViewModel.onCleared();
        viewModel = null;
        editorViewModel = null;

        viewModel = newViewModel(todayHandle);
        shell = newShellViewModel(shellHandle);
        editorViewModel = newEditorViewModel(editorHandle, new DirectExecutor());
        assertEquals(NavigationDestination.OPTIONS, shell.state().getValue().navigation);
        assertTrue(editorValue().open);
        assertEquals(taskId, editorValue().taskId);
        shell.onCleared();
    }

    @Test public void shellOwnsRestoredNavigationAndAppearanceWithoutTodayMutation() {
        viewModel = newViewModel(new SavedStateHandle(), new DirectExecutor());
        SavedStateHandle handle = new SavedStateHandle();
        AppShellViewModel shell = newShellViewModel(handle);
        TodayUiModel todayBefore = value().today();

        shell.dispatch(AppShellAction.destinationSelected(NavigationDestination.OPTIONS));
        preferences.setThemeMode(UiThemeMode.DARK);

        assertEquals(NavigationDestination.OPTIONS, shell.state().getValue().navigation);
        assertEquals(DayPalette.at(clock.time(), DayPalette.Mode.DARK).background,
                shell.state().getValue().palette.background);
        assertSame(todayBefore, value().today());
        shell.onCleared();

        shell = newShellViewModel(handle);
        assertEquals(NavigationDestination.OPTIONS, shell.state().getValue().navigation);
        assertEquals(DayPalette.at(clock.time(), DayPalette.Mode.DARK).background,
                shell.state().getValue().palette.background);
        shell.onCleared();
    }

    @Test public void duplicateCommandsAreIgnoredWhileTheFirstIsRunning() throws Exception {
        ManualExecutor worker = new ManualExecutor();
        editorViewModel = newEditorViewModel(new SavedStateHandle(), worker);

        EditorUiState draft = EditorUiState.create().withDraft("Einmal", TaskSlot.MORNING,
                Recurrence.ONCE, 1, 0, Collections.emptyList());
        editorViewModel.dispatch(TaskEditorAction.save(draft));
        editorViewModel.dispatch(TaskEditorAction.save(draft));

        worker.runAll();
        assertFalse(editorValue().saving);
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
        LegacyStateFlowBinder.observe(first, viewModel.state(),
                state -> firstStates.add(state.loading));
        assertEquals(Collections.singletonList(true), firstStates);

        first.destroy();
        RecordingLifecycleOwner recreated = new RecordingLifecycleOwner();
        List<Boolean> recreatedStates = new ArrayList<>();
        LegacyStateFlowBinder.observe(recreated, viewModel.state(),
                state -> recreatedStates.add(state.loading));

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
        editorViewModel = newEditorViewModel(new SavedStateHandle(), new DirectExecutor());

        editorViewModel.dispatch(TaskEditorAction.save(EditorUiState.create()));

        assertFalse(editorValue().issues.isEmpty());
        assertFalse(editorValue().saving);
    }

    @Test public void newEditorLoadsSharedCapacityCatalogWithoutBecomingDirty() {
        tasks.flows.saveCapacityResource.execute("shared-rack", "Wäscheständer", 2);
        editorViewModel = newEditorViewModel(new SavedStateHandle(), new DirectExecutor());

        editorViewModel.dispatch(TaskEditorAction.openNew());

        assertTrue(editorValue().open);
        assertFalse(editorValue().loading);
        assertFalse(editorValue().dirty);
        assertEquals(1, editorValue().flowDraft.resources.size());
        assertEquals("Wäscheständer", editorValue().flowDraft.resources.get(0).name);
    }

    @Test public void dismissingAStillLoadingEditorPreventsStaleReopen() {
        TaskId id = TaskId.of("slow-editor");
        repository.insertTask(Task.restore(id, "Langsam", Recurrence.DAILY,
                1, 0, false, "", false, false, clock.today(), null, null,
                clock.today(), 1_024L, false, null, TaskBoundKind.FOREVER, null, null,
                null, null, ""));
        ManualExecutor worker = new ManualExecutor();
        editorViewModel = newEditorViewModel(new SavedStateHandle(), worker);

        editorViewModel.dispatch(TaskEditorAction.open(id.value));
        assertTrue(editorValue().loading);
        editorViewModel.dispatch(TaskEditorAction.dismiss());
        worker.runAll();

        assertFalse(editorValue().open);
    }

    @Test public void completedSaveCannotCloseANewerEditorGeneration() {
        ManualExecutor worker = new ManualExecutor();
        editorViewModel = newEditorViewModel(new SavedStateHandle(), worker);
        EditorUiState draft = EditorUiState.create().withDraft("Gespeichert",
                TaskSlot.MORNING, Recurrence.DAILY, 1, 0, Collections.emptyList());

        editorViewModel.dispatch(TaskEditorAction.save(draft));
        editorViewModel.dispatch(TaskEditorAction.openNew());
        EditorUiState newer = editorValue();
        worker.runAll();

        assertEquals(1, repository.allTasks().size());
        assertSame(newer, editorValue());
        assertTrue(editorValue().open);
    }

    @Test public void interruptedWriteRestoresAsRetryableDraftInsteadOfPermanentSaving() {
        SavedStateHandle handle = new SavedStateHandle();
        ManualExecutor interruptedWorker = new ManualExecutor();
        editorViewModel = newEditorViewModel(handle, interruptedWorker);
        EditorUiState draft = EditorUiState.create().withDraft("Nicht verloren",
                TaskSlot.MORNING, Recurrence.DAILY, 1, 0, Collections.emptyList());

        editorViewModel.dispatch(TaskEditorAction.save(draft));
        assertTrue(editorValue().saving);
        editorViewModel.onCleared();

        editorViewModel = newEditorViewModel(handle, new DirectExecutor());

        assertTrue(editorValue().open);
        assertFalse(editorValue().saving);
        assertEquals("Nicht verloren", editorValue().title);
        assertFalse(editorValue().storageError.isEmpty());
    }

    @Test public void editorErrorRequestSurvivesProcessStateUntilAcknowledged() {
        SavedStateHandle handle = new SavedStateHandle();
        editorViewModel = newEditorViewModel(handle, new DirectExecutor());

        editorViewModel.dispatch(TaskEditorAction.open("missing-editor"));

        TaskEditorRequest pending = editorViewModel.state().getValue().firstRequest();
        assertNotNull(pending);
        editorViewModel.onCleared();
        editorViewModel = newEditorViewModel(handle, new DirectExecutor());
        assertEquals(pending.id, editorViewModel.state().getValue().firstRequest().id);

        editorViewModel.dispatch(TaskEditorAction.acknowledgeRequest(pending.id));

        assertNull(editorViewModel.state().getValue().firstRequest());
        assertFalse(editorValue().open);
    }

    @Test public void repetitionDraftsStayLocalUntilSubmissionPersistsThem() {
        viewModel = newViewModel(new SavedStateHandle(), new DirectExecutor());
        TaskDefinition definition = new TaskDefinition("Gym", null, TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER,
                null, null, null, null, "", Collections.singletonList(
                        de.thonktank.autosecretary.testing.StepTestFixtures.definition(null, 0, "Kniebeugen", 0,
                                StepAmount.setsReps(3, 12), "")));
        tasks.catalog.create.execute(definition);
        refreshDatabase();
        String stepId = value().today().focus.steps.get(0).id;

        viewModel.dispatch(TodayAction.adjustRepetition(stepId, 1));
        viewModel.dispatch(TodayAction.adjustRepetition(stepId, 1));

        assertEquals(14, value().repetitionInput.value);
        assertTrue(repository.findOccurrenceStep(stepId).repetitionProgress.actualRepetitions
                .isEmpty());

        viewModel.dispatch(TodayAction.submitRepetition(stepId));

        assertNull(value().repetitionInput.stepId);
        assertEquals(Collections.singletonList(14),
                repository.findOccurrenceStep(stepId).repetitionProgress.actualRepetitions);
    }

    @Test public void todayMenuRequestRestoresAndTransitionsAtomically() {
        SavedStateHandle handle = new SavedStateHandle();
        viewModel = newViewModel(handle, new DirectExecutor());
        TaskActionTarget target = TaskActionTarget.of("menu-task", "menu-occurrence",
                "Menüaufgabe", TaskSlot.MIDDAY, false, false);

        viewModel.dispatch(TodayAction.openTaskMenu(target));
        viewModel.dispatch(TodayAction.openTaskMenu(target));

        assertEquals(1, value().requests.size());
        TodayRequest menu = value().firstRequest();
        assertNotNull(menu);
        assertEquals(TodayRequest.Kind.TASK_MENU, menu.kind);
        viewModel.onCleared();
        viewModel = newViewModel(handle, new DirectExecutor());
        assertEquals(menu.id, value().firstRequest().id);

        viewModel.dispatch(TodayAction.requestDeleteTask(menu.id));

        assertEquals(1, value().requests.size());
        TodayRequest confirmation = value().firstRequest();
        assertEquals(TodayRequest.Kind.CONFIRM_DELETE, confirmation.kind);
        assertFalse(menu.id.equals(confirmation.id));
        assertNull(value().request(menu.id));
        viewModel.dispatch(TodayAction.acknowledgeRequest(menu.id));
        assertEquals(confirmation.id, value().firstRequest().id);
        viewModel.onCleared();
        viewModel = newViewModel(handle, new DirectExecutor());
        assertEquals(confirmation.id, value().firstRequest().id);

        viewModel.dispatch(TodayAction.acknowledgeRequest(confirmation.id));

        assertNull(value().firstRequest());
    }

    @Test public void displayPreferencesReprojectDashboardWithoutReloadingContent() {
        viewModel = newViewModel(new SavedStateHandle(), new DirectExecutor());

        calendarInvalidations.materializeExternalChange();
        TodayUiModel dashboardBefore = value().today();

        preferences.setFocusStepLimit(FocusStepLimit.THREE);

        assertEquals(FocusStepLimit.THREE, value().focusStepLimit);
        assertSame(dashboardBefore, value().today());

        preferences.setThemeMode(UiThemeMode.DARK);
        assertSame(dashboardBefore, value().today());
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
        tasks.catalog.create.execute(new TaskDefinition("Reihenfolge", null, TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER,
                null, null, null, null, "", java.util.Arrays.asList(
                de.thonktank.autosecretary.testing.StepTestFixtures.definition(null, 0, "A", 0, StepAmount.none(), ""),
                de.thonktank.autosecretary.testing.StepTestFixtures.definition(null, 1, "B", 0, StepAmount.none(), ""),
                de.thonktank.autosecretary.testing.StepTestFixtures.definition(null, 2, "C", 0, StepAmount.none(), ""))));
        refreshDatabase();
        List<de.thonktank.autosecretary.presentation.today.FocusStepUiModel> steps =
                value().today().focus.steps;
        String first = steps.get(0).id;
        databaseQueries.clear();
        calendarLoads.set(0);

        viewModel.dispatch(TodayAction.moveStep(first, null));

        assertEquals(0, calendarLoads.get());
        assertEquals(first, value().today().focus.steps.get(2).id);
        long dashboardReads = databaseQueries.stream()
                .map(sql -> sql.trim().toLowerCase(java.util.Locale.ROOT))
                .filter(sql -> sql.equals("select * from tasks"))
                .count();
        assertEquals(1L, dashboardReads);
    }

    @Test public void completionReloadsOnlyTodayAndPreservesSiblingPresentationState() {
        AtomicInteger calendarLoads = new AtomicInteger();
        viewModel = newViewModel(new SavedStateHandle(), new DirectExecutor(), calendarLoads);
        editorViewModel = newEditorViewModel(new SavedStateHandle(), new DirectExecutor());
        tasks.catalog.create.execute(TaskDefinition.basic("Abschließen", TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, Collections.emptyList()));
        refreshDatabase();
        editorViewModel.dispatch(TaskEditorAction.openNew());
        preferences.setFocusStepLimit(FocusStepLimit.THREE);
        EditorUiState editorBefore = editorValue();
        String occurrenceId = value().today().focus.occurrenceId();
        calendarLoads.set(0);

        viewModel.dispatch(TodayAction.completeOccurrence(occurrenceId));

        assertEquals(0, calendarLoads.get());
        assertSame(editorBefore, editorValue());
        assertEquals(FocusStepLimit.THREE, value().focusStepLimit);
        assertTrue(value().today().completedToday.stream()
                .anyMatch(done -> done.occurrenceId.equals(occurrenceId)));
        RewardEffect reward = value().rewards.first();
        assertNotNull(reward);
        viewModel.dispatch(TodayAction.openTaskMenu(TaskActionTarget.of(
                "parallel-task", "parallel-occurrence", "Parallel",
                TaskSlot.MIDDAY, false, false)));
        TodayUiModel afterCompletion = value().today();
        String requestId = value().firstRequest().id;

        viewModel.dispatch(TodayAction.acknowledgeReward(reward.id));

        assertNull(value().rewards.first());
        assertSame(afterCompletion, value().today());
        assertEquals(requestId, value().firstRequest().id);
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
        tasks.catalog.create.execute(TaskDefinition.basic("Sortieren", TaskSlot.MORNING,
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
        tasks.catalog.create.execute(TaskDefinition.basic("Löschen", TaskSlot.MORNING,
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

        java.util.List<AppDestination> destinations = new java.util.ArrayList<>();
        recreated.onCleared();
        recreated = newAllTasksViewModel(handle, destinations::add);
        recreated.dispatch(AllTasksAction.editTask(task.id));
        assertEquals(1, destinations.size());
        assertEquals(1, recreated.state().getValue().requests.size());

        recreated.dispatch(AllTasksAction.confirmDelete(restored.id));

        assertNull(recreated.state().getValue().firstRequest());
        assertTrue(repository.allTasks().stream().noneMatch(value -> value.id.equals(task.id)));
        recreated.onCleared();
    }

    @Test public void managementNavigationUsesAppBoundaryWithoutPollutingScreenState() {
        java.util.List<AppDestination> destinations = new java.util.ArrayList<>();
        AllTasksViewModel management = newAllTasksViewModel(
                new SavedStateHandle(), destinations::add);
        AllTasksUiState content = management.state().getValue().content;

        management.dispatch(AllTasksAction.editTask(TaskId.of("request-task")));
        management.dispatch(AllTasksAction.editStep(TaskId.of("request-task"),
                TaskStepId.of("request-step")));
        management.dispatch(AllTasksAction.addStep(TaskId.of("request-task")));

        assertEquals(3, destinations.size());
        AppDestination.TaskEditor target = (AppDestination.TaskEditor) destinations.get(0);
        assertEquals(TaskId.of("request-task"), target.taskId);
        assertNull(target.stepId);
        assertFalse(target.addStep);
        AppDestination.TaskEditor step = (AppDestination.TaskEditor) destinations.get(1);
        assertEquals("request-step", step.stepId.value);
        assertFalse(step.addStep);
        AppDestination.TaskEditor add = (AppDestination.TaskEditor) destinations.get(2);
        assertNull(add.stepId);
        assertTrue(add.addStep);
        assertNull(management.state().getValue().firstRequest());
        assertSame(content, management.state().getValue().content);
        management.onCleared();
    }

    @Test public void appNavigatorPreparesHeaderMotionBeforeOpenAndProtectsAnOpenDraft() {
        AtomicInteger entrances = new AtomicInteger();
        editorViewModel = newEditorViewModel(new SavedStateHandle(), new DirectExecutor());
        TaskEditorNavigator navigator = new TaskEditorNavigator(
                editorViewModel, entrances::incrementAndGet);

        navigator.navigate(AppDestination.newTaskFromHeader());

        assertEquals(1, entrances.get());
        EditorUiState opened = editorValue();
        assertTrue(opened.open);

        navigator.navigate(AppDestination.newTask());

        assertSame(opened, editorValue());
        assertEquals(1, entrances.get());
    }

    @SuppressWarnings("deprecation")
    @Test public void legacyPendingEditorRouteMigratesOnceOutOfManagementSavedState() {
        android.os.Bundle item = new android.os.Bundle();
        item.putString("id", "all-tasks:7");
        item.putString("kind", "OPEN_EDITOR");
        item.putString("task_id", "legacy-task");
        item.putString("step_id", "legacy-step");
        android.os.Bundle stored = new android.os.Bundle();
        stored.putParcelableArrayList("items", new java.util.ArrayList<>(
                java.util.Collections.singletonList(item)));
        SavedStateHandle handle = new SavedStateHandle();
        handle.set("all_tasks_requests", stored);
        java.util.List<AppDestination> destinations = new java.util.ArrayList<>();

        AllTasksViewModel management = newAllTasksViewModel(handle, destinations::add);

        assertEquals(1, destinations.size());
        AppDestination.TaskEditor target = (AppDestination.TaskEditor) destinations.get(0);
        assertEquals(TaskId.of("legacy-task"), target.taskId);
        assertEquals("legacy-step", target.stepId.value);
        assertFalse(target.addStep);
        assertNull(management.state().getValue().firstRequest());
        android.os.Bundle rewritten = handle.get("all_tasks_requests");
        assertTrue(rewritten.getParcelableArrayList("items").isEmpty());
        management.onCleared();
    }

    @Test public void oneRoomWriteReprojectsDashboardAndCatalogWithoutCrossSignals() {
        viewModel = newViewModel(new SavedStateHandle());
        AllTasksViewModel management = newAllTasksViewModel(new SavedStateHandle());

        tasks.catalog.create.execute(TaskDefinition.basic("Gemeinsame Wahrheit", TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, Collections.emptyList()));
        refreshDatabase();

        assertTrue((value().today().focus != null
                        && "Gemeinsame Wahrheit".equals(value().today().focus.title()))
                || value().today().timeline.stream().anyMatch(item -> item.task != null
                        && "Gemeinsame Wahrheit".equals(item.task.title)));
        assertTrue(management.state().getValue().content.tasks.stream()
                .anyMatch(item -> "Gemeinsame Wahrheit".equals(item.task.title)));
        management.onCleared();
    }

    private TodayViewModel newViewModel(SavedStateHandle handle) {
        return newViewModel(handle, new DirectExecutor());
    }

    private TodayViewModel newViewModel(SavedStateHandle handle,
                                       AbstractExecutorService worker) {
        return newViewModel(handle, worker, new AtomicInteger());
    }

    private TodayViewModel newViewModel(SavedStateHandle handle,
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
        return new TodayViewModel(tasks.today, tasks.catalog, tasks.training, presenter, calendar,
                preferences, clock, logger,
                new AndroidUiTextProvider(context), invalidationSource, handle, worker,
                Runnable::run);
    }

    private AllTasksViewModel newAllTasksViewModel(SavedStateHandle handle) {
        return newAllTasksViewModel(handle, destination -> { });
    }

    private AppShellViewModel newShellViewModel(SavedStateHandle handle) {
        if (invalidationSource == null)
            throw new IllegalStateException("Today invalidation source must exist first");
        return new AppShellViewModel(preferences, clock, logger, invalidationSource,
                handle, new DirectExecutor(), Runnable::run);
    }

    private AllTasksViewModel newAllTasksViewModel(SavedStateHandle handle,
                                                    AppNavigator navigator) {
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
        return new AllTasksViewModel(tasks.catalog.loadTaskCatalog, tasks.catalog.moveScheduleEntry,
                tasks.catalog.moveTaskStep, tasks.catalog.swapTaskSteps, tasks.catalog.delete,
                new AndroidUiTextProvider(context), handle, new DirectExecutor(),
                invalidationSource, Runnable::run, navigator);
    }

    private TaskEditorViewModel newEditorViewModel(SavedStateHandle handle,
                                                    AbstractExecutorService worker) {
        return new TaskEditorViewModel(tasks.catalog, tasks.flows, tasks.today, clock, logger,
                new AndroidUiTextProvider(context), handle, worker);
    }

    private void refreshDatabase() {
        database.getInvalidationTracker().refreshVersionsSync();
    }

    private TodayScreenState value() {
        return viewModel.state().getValue();
    }

    private EditorUiState editorValue() {
        return editorViewModel.state().getValue().content;
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
