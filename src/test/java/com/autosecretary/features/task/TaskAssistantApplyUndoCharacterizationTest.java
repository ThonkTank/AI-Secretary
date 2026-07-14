package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.task.application.ApplyTaskChangesUseCase;
import com.autosecretary.features.task.application.TaskChangeUndoHolder;
import com.autosecretary.features.task.application.UndoTaskChangesUseCase;
import com.autosecretary.features.task.data.TaskCategoryDao;
import com.autosecretary.features.task.data.TaskCategoryWindowDao;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.assistant.ChangeOp;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal.CategoryChange;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal.TaskChange;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskCategory;
import com.autosecretary.features.task.domain.model.TaskCategoryWindow;
import com.autosecretary.shared.Priority;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.SynchronousExecutorService;
import com.autosecretary.testing.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class TaskAssistantApplyUndoCharacterizationTest extends AutoSecretaryRobolectricTest {
    private AppDatabase db;
    private TaskDao taskDao;
    private TaskCategoryDao categoryDao;
    private TaskCategoryWindowDao windowDao;
    private TaskChangeUndoHolder undoHolder;
    private ApplyTaskChangesUseCase applyUseCase;
    private UndoTaskChangesUseCase undoUseCase;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
        taskDao = db.taskDao();
        categoryDao = db.taskCategoryDao();
        windowDao = db.taskCategoryWindowDao();
        undoHolder = new TaskChangeUndoHolder();
        SynchronousExecutorService exec = new SynchronousExecutorService();
        applyUseCase = new ApplyTaskChangesUseCase(db, taskDao, categoryDao, windowDao, undoHolder, exec, exec);
        undoUseCase = new UndoTaskChangesUseCase(db, taskDao, categoryDao, windowDao, undoHolder, exec, exec);
    }

    @After
    public void tearDown() {
        db.close();
    }

    /**
     * Invariant: an unknown UPDATE/DELETE id is rejected before any write — the proposal never
     * touches the database and no undo entry is recorded.
     */
    @Test
    public void proposalWithUnknownIdIsRejectedWithoutWriting() {
        seedCategory("c1", "Arbeit");
        seedTask("t1", "Alt", "c1");

        TaskAssistantProposal proposal = new TaskAssistantProposal(
                List.of(),
                List.of(new TaskChange(ChangeOp.UPDATE, "does-not-exist", "Neu", null, null, null, null)));

        AtomicReference<String> error = new AtomicReference<>();
        applyUseCase.apply(proposal, () -> {}, error::set);

        assertNotNull("unknown id should be rejected", error.get());
        assertEquals("Alt", taskDao.read("t1").core.title); // unchanged
        assertTrue("nothing recorded for undo", undoHolder.isEmpty());
    }

    /**
     * Invariant: applies stack; each undo steps exactly one apply back, restoring the precise prior
     * state (recreated deletions, removed creations, reverted updates), and undo on an empty stack
     * is a no-op.
     */
    @Test
    public void multiLevelApplyUndoRestoresEachPriorStateStepByStep() {
        seedCategory("c1", "Arbeit");
        seedTask("t1", "Alt", "c1");

        // Apply #1: rename t1, create a second task.
        applyOk(new TaskAssistantProposal(List.of(), List.of(
                new TaskChange(ChangeOp.UPDATE, "t1", "Neu", null, null, null, null),
                new TaskChange(ChangeOp.CREATE, null, "Zweite", null, "c1", Priority.HIGH, null))));
        assertEquals("Neu", taskDao.read("t1").core.title);
        assertEquals(2, taskDao.readAll().size());

        // Apply #2: delete t1, create a new category.
        applyOk(new TaskAssistantProposal(
                List.of(new CategoryChange(ChangeOp.CREATE, null, "Privat", null, null)),
                List.of(new TaskChange(ChangeOp.DELETE, "t1", null, null, null, null, null))));
        assertNull(taskDao.read("t1"));
        assertEquals(2, categoryDao.readAll().size());

        // Undo #2 → state after apply #1.
        undoOk(true);
        assertNotNull(taskDao.read("t1"));
        assertEquals("Neu", taskDao.read("t1").core.title);
        assertEquals(1, categoryDao.readAll().size());
        assertEquals(2, taskDao.readAll().size());

        // Undo #1 → exact original.
        undoOk(true);
        assertEquals("Alt", taskDao.read("t1").core.title);
        assertEquals("c1", taskDao.read("t1").core.categoryId);
        assertEquals(1, taskDao.readAll().size());
        assertEquals(1, categoryDao.readAll().size());

        // Undo on empty stack → no-op.
        undoOk(false);
        assertEquals(1, taskDao.readAll().size());
    }

    /**
     * Invariant: undoing a category deletion restores the category, its time windows, and the
     * categoryId that was cascade-cleared from tasks pointing at it.
     */
    @Test
    public void undoRestoresDeletedCategoryWindowsAndClearedTaskCategory() {
        seedCategory("c1", "Arbeit");
        seedTask("t1", "Alt", "c1");
        TaskCategoryWindow window = new TaskCategoryWindow(
                DayOfWeek.MONDAY, "c1", LocalTime.of(9, 0), LocalTime.of(10, 0));
        windowDao.write(window);

        applyOk(new TaskAssistantProposal(
                List.of(new CategoryChange(ChangeOp.DELETE, "c1", null, null, null)),
                List.of()));
        assertNull(categoryDao.read("c1"));
        assertTrue(windowDao.readAll().isEmpty());
        assertNull(taskDao.read("t1").core.categoryId); // cascade-cleared

        undoOk(true);
        assertNotNull(categoryDao.read("c1"));
        assertEquals(1, windowDao.readAll().size());
        assertEquals("c1", taskDao.read("t1").core.categoryId);
    }

    private void applyOk(TaskAssistantProposal proposal) {
        AtomicBoolean applied = new AtomicBoolean(false);
        AtomicReference<String> error = new AtomicReference<>();
        applyUseCase.apply(proposal, () -> applied.set(true), error::set);
        assertNull("apply error: " + error.get(), error.get());
        assertTrue(applied.get());
    }

    private void undoOk(boolean expectedRestored) {
        AtomicReference<Boolean> result = new AtomicReference<>();
        undoUseCase.undoLast(result::set);
        assertEquals(expectedRestored, result.get());
    }

    private void seedCategory(String id, String name) {
        TaskCategory category = new TaskCategory();
        category.id = id;
        category.name = name;
        categoryDao.write(category);
    }

    private void seedTask(String id, String title, String categoryId) {
        Task task = new Task();
        task.core.id = id;
        task.core.title = title;
        task.core.categoryId = categoryId;
        taskDao.write(task);
    }
}
