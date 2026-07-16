package com.autosecretary.features.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.task.application.ApplyTaskChangesUseCase;
import com.autosecretary.features.task.application.ScheduleReplanCoordinator;
import com.autosecretary.features.task.application.TaskChangeUndoHolder;
import com.autosecretary.features.task.application.config.TaskCategoryWindowRepository;
import com.autosecretary.features.task.data.TaskCategoryDao;
import com.autosecretary.features.task.data.TaskCategoryWindowDao;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.assistant.ChangeOp;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal.CategoryChange;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal.TaskChange;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskCategory;
import com.autosecretary.shared.Priority;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.ReplanCoordinators;
import com.autosecretary.testing.SynchronousExecutorService;
import com.autosecretary.testing.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Invariant protected: an assistant task proposal references its category by <em>name</em>, and the
 * apply step resolves it to a real category id — so the model can never persist a dangling category
 * reference (the {@code cat-dnd}-style invented ids that corrupted grouping in the field). A name that
 * matches nothing is rejected before any write.
 */
public final class AssistantCategoryByNameCharacterizationTest extends AutoSecretaryRobolectricTest {
    private AppDatabase db;
    private TaskDao taskDao;
    private TaskCategoryDao categoryDao;
    private ApplyTaskChangesUseCase applyUseCase;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
        taskDao = db.taskDao();
        categoryDao = db.taskCategoryDao();
        TaskCategoryWindowDao windowDao = db.taskCategoryWindowDao();
        TaskCategoryWindowRepository windowRepository =
                new TaskCategoryWindowRepository(windowDao, categoryDao);
        SynchronousExecutorService exec = new SynchronousExecutorService();
        ScheduleReplanCoordinator replan = ReplanCoordinators.inert();
        applyUseCase = new ApplyTaskChangesUseCase(db, taskDao, categoryDao, windowDao, windowRepository,
                new TaskChangeUndoHolder(), replan, exec, exec);
    }

    @After
    public void tearDown() {
        db.close();
    }

    /** An existing category, referenced by name (case-insensitively), resolves to its real id. */
    @Test
    public void taskReferencingExistingCategoryByNameGetsRealIdInvariant() {
        TaskCategory existing = seedCategory("Haushalt / Putzen");

        applyOk(new TaskAssistantProposal(List.of(), List.of(
                new TaskChange(ChangeOp.CREATE, null, "Bad putzen", null, "haushalt / putzen",
                        Priority.MEDIUM, false))));

        Task created = onlyTask();
        assertEquals("categoryName is resolved to the real category id, never a slug",
                existing.id, created.core.categoryId);
    }

    /** A category created in the SAME proposal is resolvable by name for a task in that proposal. */
    @Test
    public void taskReferencingSameProposalCategoryByNameGetsRealIdInvariant() {
        applyOk(new TaskAssistantProposal(
                List.of(new CategoryChange(ChangeOp.CREATE, null, "DnD", "🎲", "#7C3AED")),
                List.of(new TaskChange(ChangeOp.CREATE, null, "Umfrage posten", null, "DnD",
                        Priority.HIGH, false))));

        TaskCategory dnd = categoryByName("DnD");
        assertNotNull("the category was created", dnd);
        assertEquals("the task links to the newly created category's real id",
                dnd.id, onlyTask().core.categoryId);
    }

    /** A category name matching nothing (existing or same-proposal) is rejected before any write. */
    @Test
    public void taskReferencingUnknownCategoryNameIsRejectedInvariant() {
        AtomicReference<String> error = new AtomicReference<>();
        AtomicBoolean applied = new AtomicBoolean(false);
        applyUseCase.apply(new TaskAssistantProposal(List.of(), List.of(
                        new TaskChange(ChangeOp.CREATE, null, "Task", null, "cat-dnd",
                                Priority.MEDIUM, false))),
                () -> applied.set(true), error::set);

        assertNotNull("an unknown category name is rejected", error.get());
        assertTrue(error.get().contains("Unbekannte Kategorie"));
        assertTrue("nothing is written on rejection", taskDao.readAll().isEmpty());
    }

    /** A task without any category stays uncategorised (null name is fine). */
    @Test
    public void taskWithoutCategoryStaysUncategorisedInvariant() {
        applyOk(new TaskAssistantProposal(List.of(), List.of(
                new TaskChange(ChangeOp.CREATE, null, "Frei", null, null, Priority.LOW, false))));
        assertNull(onlyTask().core.categoryId);
    }

    private void applyOk(TaskAssistantProposal proposal) {
        AtomicBoolean applied = new AtomicBoolean(false);
        AtomicReference<String> error = new AtomicReference<>();
        applyUseCase.apply(proposal, () -> applied.set(true), error::set);
        assertNull("apply error: " + error.get(), error.get());
        assertTrue(applied.get());
    }

    private Task onlyTask() {
        List<Task> tasks = taskDao.readAll();
        assertEquals(1, tasks.size());
        return tasks.get(0);
    }

    private TaskCategory seedCategory(String name) {
        TaskCategory category = new TaskCategory();
        category.name = name;
        categoryDao.write(category);
        return category;
    }

    private TaskCategory categoryByName(String name) {
        return categoryDao.readAll().stream()
                .filter(c -> name.equals(c.name)).findFirst().orElse(null);
    }
}
