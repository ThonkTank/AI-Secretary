package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.lifecycle.SavedStateHandle;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.local.RoomTaskRepository;
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.ApplicationTaskRepository;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.TaskUseCases;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class FlowSetupViewModelTest {
    private AppDatabase database;
    private TaskUseCases tasks;
    private SavedStateHandle savedState;
    private FlowSetupViewModel viewModel;

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        ApplicationTaskRepository repository = new RoomTaskRepository(database);
        Clock clock = new Clock() {
            @Override public LocalDate today() { return LocalDate.of(2026, 8, 27); }
            @Override public LocalTime time() { return LocalTime.NOON; }
        };
        tasks = new TaskUseCases(repository, clock, new SequenceIds());
        tasks.create.execute(TaskDefinition.basic("Wäsche", TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, java.util.Arrays.asList(
                        "Waschgang", "Aufhängen", "Abhängen")));
        tasks.create.execute(TaskDefinition.basic("Spülmaschine", TaskSlot.EVENING,
                Recurrence.DAILY, 1, 0, java.util.Arrays.asList(
                        "Starten", "Ausräumen")));
        savedState = new SavedStateHandle();
        viewModel = create(context);
    }

    @After public void tearDown() {
        if (viewModel != null) viewModel.onCleared();
        database.close();
    }

    @Test public void draftSurvivesTaskSwitchAndSelectedTaskSurvivesRecreation() {
        FlowSetupScreenState initial = viewModel.state().getValue();
        assertNotNull(initial.setup);
        String source = initial.setup.steps.get(0).id;
        String target = initial.setup.steps.get(1).id;
        FlowSetupDraft laundry = new FlowSetupDraft(Collections.singletonList(
                new StepTransition(source, target, FlowDelayPolicy.rememberLast(7_200_000L))),
                Collections.emptyList());

        viewModel.dispatch(FlowSetupAction.updateDraft(laundry));
        viewModel.dispatch(FlowSetupAction.selectTask(1));
        assertEquals(1, viewModel.state().getValue().selectedTaskIndex);
        viewModel.dispatch(FlowSetupAction.selectTask(0));
        assertEquals(1, viewModel.state().getValue().draft.transitions.size());
        assertEquals(7_200_000L, viewModel.state().getValue().draft.transitions.get(0)
                .delay.proposedDelayMillis());

        viewModel.dispatch(FlowSetupAction.selectTask(1));
        viewModel.onCleared();
        viewModel = create(ApplicationProvider.getApplicationContext());
        assertEquals(1, viewModel.state().getValue().selectedTaskIndex);
    }

    private FlowSetupViewModel create(Context context) {
        return new FlowSetupViewModel(tasks, null, new NoOpLogger(),
                new AndroidUiTextProvider(context), savedState, new DirectExecutor());
    }

    private static final class DirectExecutor extends AbstractExecutorService {
        private boolean shutdown;
        @Override public void shutdown() { shutdown = true; }
        @Override public java.util.List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        @Override public void execute(Runnable command) { command.run(); }
    }

    private static final class SequenceIds implements IdGenerator {
        private int next;
        @Override public String nextId() { return "flow-owner-" + ++next; }
    }

    private static final class NoOpLogger implements AppLogger {
        @Override public void info(String tag, String message) { }
        @Override public void error(String tag, String message, Throwable error) { }
    }
}
