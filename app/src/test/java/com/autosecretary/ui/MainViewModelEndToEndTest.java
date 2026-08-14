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
import com.autosecretary.application.PlanningSettingsRepository;
import com.autosecretary.application.CalendarReadResult;
import com.autosecretary.application.TimeProvider;
import com.autosecretary.data.FocusDatabase;
import com.autosecretary.data.RoomWorkItemRepository;
import com.autosecretary.domain.CompletionStats;
import com.autosecretary.domain.FocusPlanner;
import com.autosecretary.domain.PlanningSettings;
import com.autosecretary.domain.Task;
import com.autosecretary.ui.editor.StepEditorState;
import com.autosecretary.ui.editor.EditorViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
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
    private PlanningSettingsRepository settings;
    private SavedStateHandle savedState;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, FocusDatabase.class).build();
        repository = new RoomWorkItemRepository(database);
        now = LocalDateTime.of(2026, 8, 11, 8, 0);
        settings = new PlanningSettingsRepository() {
            @Override public PlanningSettings load() { return PlanningSettings.defaults(); }
            @Override public void save(PlanningSettings settings) { }
        };
        planning = new PlanFocusUseCase(repository,
                range -> new CalendarReadResult.Available(List.of()), settings,
                time(), new FocusPlanner());
        executor = Executors.newSingleThreadExecutor();
        savedState = new SavedStateHandle();
        viewModel = createViewModel(savedState);
        viewModel.state().observeForever(ignored -> { });
        await(() -> ready() && dashboard().workItems().isEmpty());
    }

    private MainViewModel createViewModel(SavedStateHandle handle) {
        return new MainViewModel(handle, planning, repository,
                new MoveWorkItemUseCase(repository, time()), time(),
                executor, Runnable::run, () -> { });
    }

    private TimeProvider time() {
        return new TimeProvider() {
            @Override public Instant now() {
                return now.atZone(ZoneId.of("Europe/Berlin")).toInstant();
            }
            @Override public ZoneId zone() { return ZoneId.of("Europe/Berlin"); }
        };
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

        viewModel.selectSurface(Surface.ALL);
        viewModel.selectFilter(WorkItemFilter.DONE);
        viewModel.save(task);
        await(() -> ready() && dashboard().workItems().size() == 1);
        assertEquals(Surface.ALL, viewModel.state().getValue().surface());
        assertEquals(WorkItemFilter.DONE, viewModel.state().getValue().filter());
        assertFalse(((Task) dashboard().workItems().get(0)).completed());

        viewModel.complete(TASK_ID);
        await(() -> ready() && ((Task) dashboard()
                .workItems().get(0)).completed()
                && viewModel.effects().getValue() instanceof MainUiEffect.Completion);

        assertTrue(((Task) dashboard().workItems().get(0)).completed());
        assertTrue(viewModel.effects().getValue() instanceof MainUiEffect.Completion);
    }

    @Test
    public void savedStateRestoresSurfaceFilterOpenDialogAndRawInputs() {
        viewModel.selectSurface(Surface.ALL);
        viewModel.selectFilter(WorkItemFilter.ROUTINES);
        EditorViewModel editorViewModel = new EditorViewModel(
                savedState, repository, time(), executor, Runnable::run);
        editorViewModel.open(false, null);
        var editor = editorViewModel.editor();
        editorViewModel.edit(editor.edit("Unfertiger Titel", "abc", "2026-08-20",
                "EVENING", false, "", "", List.of(
                        StepEditorState.empty().edit("Erster Schritt", "Mo,Mi"))));

        MainViewModel recreated = createViewModel(savedState);
        EditorViewModel recreatedEditor = new EditorViewModel(
                savedState, repository, time(), executor, Runnable::run);
        recreated.state().observeForever(ignored -> { });
        viewModel = recreated;

        assertEquals(Surface.ALL, viewModel.state().getValue().surface());
        assertEquals(WorkItemFilter.ROUTINES, viewModel.state().getValue().filter());
        assertEquals("Unfertiger Titel", recreatedEditor.editor().titleInput());
        assertEquals("abc", recreatedEditor.editor().durationInput());
        assertEquals("Erster Schritt",
                recreatedEditor.editor().steps().get(0).titleInput());
    }

    private boolean ready() {
        MainUiState state = viewModel.state().getValue();
        return state instanceof MainUiState.Ready;
    }

    private com.autosecretary.application.DashboardData dashboard() {
        return ((MainUiState.Ready) viewModel.state().getValue()).dashboard();
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
