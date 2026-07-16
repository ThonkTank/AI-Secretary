package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.task.application.ApplyTaskChangesUseCase;
import com.autosecretary.features.task.application.TaskChangeUndoHolder;
import com.autosecretary.features.task.application.config.TaskCategoryRepository;
import com.autosecretary.features.task.application.config.TaskCategoryWindowRepository;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal;
import com.autosecretary.features.task.ui.TaskCategoryWindowViewModel;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.ReplanCoordinators;
import com.autosecretary.testing.SynchronousExecutorService;
import com.autosecretary.testing.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * Invariant protected: scheduling-input mutations request a re-plan through the coordinator, so the
 * schedule stays responsive to changes. Each trigger site is verified via a recording coordinator.
 */
public final class ScheduleReplanTriggerTest extends AutoSecretaryRobolectricTest {
    private AppDatabase db;
    private SynchronousExecutorService exec;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
        exec = new SynchronousExecutorService();
    }

    @After
    public void tearDown() {
        db.close();
    }

    /** Applying an assistant proposal (here a no-op proposal) triggers exactly one re-plan. */
    @Test
    public void assistantApplyTriggersReplanInvariant() {
        ReplanCoordinators.Recording replan = ReplanCoordinators.recording(db);
        ApplyTaskChangesUseCase applyUseCase = new ApplyTaskChangesUseCase(
                db, db.taskDao(), db.taskCategoryDao(), db.taskCategoryWindowDao(),
                new TaskCategoryWindowRepository(db.taskCategoryWindowDao(), db.taskCategoryDao()),
                new TaskChangeUndoHolder(), replan.coordinator, exec, exec);

        applyUseCase.apply(new TaskAssistantProposal(List.of(), List.of()), () -> {}, error -> {});

        assertEquals("apply requests one re-plan", 1, replan.runs());
    }

    /** Saving reserved category windows triggers exactly one re-plan. */
    @Test
    public void categoryWindowSaveTriggersReplanInvariant() {
        ReplanCoordinators.Recording replan = ReplanCoordinators.recording(db);
        TaskCategoryWindowViewModel viewModel = new TaskCategoryWindowViewModel(
                new TaskCategoryWindowRepository(db.taskCategoryWindowDao(), db.taskCategoryDao()),
                new TaskCategoryRepository(db.taskCategoryDao(), db.taskCategoryWindowDao()),
                replan.coordinator, exec, exec);

        viewModel.save(List.of(), List.of(), () -> {});

        assertEquals("saving windows requests one re-plan", 1, replan.runs());
    }
}
