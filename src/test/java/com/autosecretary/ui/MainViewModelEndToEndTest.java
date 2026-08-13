package com.autosecretary.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.application.MoveWorkItemUseCase;
import com.autosecretary.application.PlanFocusUseCase;
import com.autosecretary.application.ResolveMigrationCandidateUseCase;
import com.autosecretary.application.WorkItemCommands;
import com.autosecretary.data.FocusDatabase;
import com.autosecretary.data.RoomWorkItemRepository;
import com.autosecretary.domain.CompletionStats;
import com.autosecretary.domain.FocusPlanner;
import com.autosecretary.domain.PlanningSettings;
import com.autosecretary.domain.Task;
import com.autosecretary.ui.editor.StepEditorState;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public final class MainViewModelEndToEndTest {
    private static final String TASK_ID = "00000000-0000-0000-0000-000000000001";
    @Rule public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private FocusDatabase database;
    private ExecutorService executor;
    private MainViewModel viewModel;
    private RoomWorkItemRepository repository;
    private LocalDateTime now;
    private PlanFocusUseCase planning;
    private com.autosecretary.application.PlanningSettingsUseCase settings;
    private SavedStateHandle savedState;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, FocusDatabase.class).build();
        repository = new RoomWorkItemRepository(database);
        now = LocalDateTime.of(2026, 8, 11, 8, 0);
        planning = new PlanFocusUseCase(repository,
                (start, end) -> List.of(),
                new com.autosecretary.application.PlanningSettingsRepository() {
                    @Override public PlanningSettings load() { return PlanningSettings.defaults(); }
                    @Override public void save(PlanningSettings settings) { }
                },
                () -> now,
                new FocusPlanner());
        executor = Executors.newSingleThreadExecutor();
        settings = new com.autosecretary.application.PlanningSettingsUseCase(
                new com.autosecretary.application.PlanningSettingsRepository() {
                    @Override public PlanningSettings load() { return PlanningSettings.defaults(); }
                    @Override public void save(PlanningSettings settings) { }
                });
        savedState = new SavedStateHandle();
        viewModel = createViewModel(savedState);
        viewModel.state().observeForever(ignored -> { });
        await(() -> ready() && viewModel.state().getValue().dashboard().workItems().isEmpty());
    }

    private MainViewModel createViewModel(SavedStateHandle handle) {
        return new MainViewModel(handle, planning,
                new WorkItemCommands(repository, () -> now),
                new MoveWorkItemUseCase(repository, () -> now),
                new ResolveMigrationCandidateUseCase(repository, () -> now), () -> now,
                settings, executor, Runnable::run, () -> { });
    }

    @After
    public void tearDown() {
        executor.shutdownNow();
        database.close();
    }

    @Test
    public void saveCompleteAndRetainedSelectionFlowThroughAllLayers() {
        Task task = new Task(TASK_ID, "End-to-End", 30, null, null, true, List.of(),
                LocalDateTime.of(2026, 8, 1, 8, 0), false, CompletionStats.empty(), 0);

        viewModel.selectSurface("all");
        viewModel.selectFilter("done");
        viewModel.save(task);
        await(() -> ready() && viewModel.state().getValue().dashboard().workItems().size() == 1);
        assertEquals("all", viewModel.state().getValue().surface());
        assertEquals("done", viewModel.state().getValue().filter());
        assertFalse(((Task) viewModel.state().getValue().dashboard().workItems().get(0)).completed());

        viewModel.complete(TASK_ID);
        await(() -> ready() && ((Task) viewModel.state().getValue().dashboard()
                .workItems().get(0)).completed());

        assertTrue(((Task) viewModel.state().getValue().dashboard().workItems().get(0)).completed());
        assertEquals(1, viewModel.state().getValue().completionSignal());
    }

    @Test
    public void savedStateRestoresSurfaceFilterOpenDialogAndRawInputs() {
        viewModel.selectSurface("all");
        viewModel.selectFilter("routines");
        viewModel.openEditor(false, null);
        var editor = viewModel.state().getValue().editor();
        viewModel.editEditor(editor.edit("Unfertiger Titel", "abc", "2026-08-20",
                "EVENING", false, "", "", List.of(
                        StepEditorState.empty().edit("Erster Schritt", "Mo,Mi"))));

        MainViewModel recreated = createViewModel(savedState);
        recreated.state().observeForever(ignored -> { });
        viewModel = recreated;

        assertEquals("all", viewModel.state().getValue().surface());
        assertEquals("routines", viewModel.state().getValue().filter());
        assertEquals("Unfertiger Titel", viewModel.state().getValue().editor().titleInput());
        assertEquals("abc", viewModel.state().getValue().editor().durationInput());
        assertEquals("Erster Schritt",
                viewModel.state().getValue().editor().steps().get(0).titleInput());
    }

    private boolean ready() {
        MainUiState state = viewModel.state().getValue();
        return state != null && !state.loading() && state.dashboard() != null;
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (condition.getAsBoolean()) return;
            try { Thread.sleep(10); }
            catch (InterruptedException error) { throw new AssertionError(error); }
        }
        throw new AssertionError("ViewModel-Zustand wurde nicht rechtzeitig beobachtbar");
    }
}
