package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.calendar.CalendarDataSource;
import de.thonktank.autosecretary.data.local.RoomTaskRepository;
import de.thonktank.autosecretary.data.preferences.UiPreferences;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.TaskRepository;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.TaskUseCases;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.DashboardPresenter;
import de.thonktank.autosecretary.presentation.DashboardUiMapper;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;

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
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class PresentationStateRobolectricTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 15);

    @Rule public final TestRule instantExecutors = new InstantTaskExecutorRule();

    private Context context;
    private AppDatabase database;
    private TaskRepository repository;
    private TaskUseCases tasks;
    private DashboardPresenter presenter;
    private UiPreferences preferences;
    private final FixedClock clock = new FixedClock();
    private final RecordingLogger logger = new RecordingLogger();
    private final AtomicInteger ids = new AtomicInteger();
    private TaskViewModel viewModel;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteSharedPreferences("forest_ui");
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        repository = new RoomTaskRepository(database);
        IdGenerator idGenerator = () -> "presentation-" + ids.incrementAndGet();
        tasks = new TaskUseCases(repository, clock, idGenerator);
        presenter = new DashboardPresenter(clock, tasks.loadDashboard, tasks.materializeDue,
                new DashboardUiMapper(new AndroidUiTextProvider(context)));
        preferences = new UiPreferences(context, logger);
    }

    @After public void tearDown() {
        if (viewModel != null) viewModel.onCleared();
        database.close();
        context.deleteSharedPreferences("forest_ui");
    }

    @Test public void composerMergesCalendarAndTasksOutsideTheActivity() {
        DashboardUiModel model = DashboardUiModel.compose(
                DashboardFixtures.fullDashboard(), DashboardFixtures.calendarEvents());

        assertEquals(6, model.timeline.size());
        assertEquals("Urlaub", model.timeline.get(0).event.title);
        assertEquals("done", model.timeline.get(1).task.taskId);
        assertEquals("steps", model.firstOpen().taskId);
    }

    @Test public void presentationCollectionsCannotBeMutated() {
        DashboardUiModel model = DashboardUiModel.compose(
                DashboardFixtures.fullDashboard(), DashboardFixtures.calendarEvents());
        try {
            model.tasks.clear();
            fail("tasks must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertFalse(model.tasks.isEmpty());
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
        await(() -> !value().loading);

        tasks.create.execute("Bearbeitbar", TaskSlot.EVENING, Recurrence.INTERVAL, 4, 0,
                java.util.Arrays.asList("A", "B"), true, "Fertig");
        String taskId = repository.allTasks().get(0).id.value;

        viewModel.navigate(NavigationDestination.OPTIONS);
        viewModel.openEditor(taskId);
        await(() -> value().editor.open && !value().editor.loading);
        assertEquals("Bearbeitbar", value().editor.title);
        assertEquals(4, value().editor.intervalDays);
        assertEquals(2, value().editor.steps.size());
        viewModel.onCleared();

        viewModel = newViewModel(handle);
        assertEquals(NavigationDestination.OPTIONS, value().navigation);
        assertTrue(value().editor.open);
        assertEquals(taskId, value().editor.taskId);
        assertEquals("Fertig", value().editor.condition);
    }

    @Test public void duplicateCommandsAreIgnoredWhileTheFirstIsRunning() throws Exception {
        viewModel = newViewModel(new SavedStateHandle());
        await(() -> !value().loading);

        viewModel.create("Einmal", TaskSlot.MORNING, Recurrence.ONCE, 1, 0,
                Collections.emptyList(), false, "");
        viewModel.create("Einmal", TaskSlot.MORNING, Recurrence.ONCE, 1, 0,
                Collections.emptyList(), false, "");

        await(() -> !value().isRunning("create"));
        assertEquals(1, repository.allTasks().size());
    }

    @Test public void errorsAreTypedOneShotEvents() throws Exception {
        viewModel = newViewModel(new SavedStateHandle());
        await(() -> !value().loading);

        viewModel.create("", TaskSlot.MORNING, Recurrence.ONCE, 1, 0,
                Collections.emptyList(), false, "");

        await(() -> viewModel.events().getValue() != null);
        UiEvent event = viewModel.events().getValue();
        assertNotNull(event);
        assertEquals(UiEvent.Type.ERROR, event.type);
        assertTrue(event.consume());
        assertFalse(event.consume());
    }

    private TaskViewModel newViewModel(SavedStateHandle handle) {
        CalendarDataSource calendar = DashboardFixtures::calendarEvents;
        return new TaskViewModel(tasks, presenter, calendar, preferences, clock, logger,
                new AndroidUiTextProvider(context), handle);
    }

    private DashboardUiState value() {
        return viewModel.state().getValue();
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 3_000L;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline)
            Thread.sleep(10L);
        assertTrue("Timed out waiting for asynchronous state", condition.getAsBoolean());
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
