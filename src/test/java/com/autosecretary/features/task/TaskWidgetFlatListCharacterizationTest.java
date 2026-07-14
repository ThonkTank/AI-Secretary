package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.task.application.LoadTaskWidgetItemsUseCase;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskCategory;
import com.autosecretary.shared.Priority;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.TaskFixtures;
import com.autosecretary.testing.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

/**
 * Protects the flat task widget invariants: the widget lists open tasks sorted by priority
 * (highest first), independent of which day their slots fall on, and honours a category filter.
 */
public final class TaskWidgetFlatListCharacterizationTest extends AutoSecretaryRobolectricTest {
    private AppDatabase db;
    private LoadTaskWidgetItemsUseCase useCase;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
        TaskDao taskDao = db.taskDao();
        useCase = new LoadTaskWidgetItemsUseCase(taskDao, db.taskCategoryDao(), new TaskListItemMapper());

        TaskCategory work = new TaskCategory("Arbeit", "💼", "#FF112233");
        TaskCategory leisure = new TaskCategory("Freizeit", "🎮", "#FF445566");
        db.taskCategoryDao().write(work);
        db.taskCategoryDao().write(leisure);

        LocalDate today = LocalDate.now();

        Task critical = TaskFixtures.taskWithSlot("Wichtig", today);
        critical.core.priority = Priority.CRITICAL;
        critical.core.categoryId = work.id;

        // Scheduled only on a far-future day — must still show in the day-independent widget.
        Task medium = TaskFixtures.taskWithSlot("Mittel", today.plusDays(5));
        medium.core.priority = Priority.MEDIUM;
        medium.core.categoryId = work.id;

        // Unscheduled (no slot) leisure task — still an open task, lowest priority.
        Task low = new Task();
        low.core.title = "Niedrig";
        low.core.priority = Priority.LOW;
        low.core.categoryId = leisure.id;

        taskDao.write(critical);
        taskDao.write(medium);
        taskDao.write(low);
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void widgetListsOpenTasksByPriorityIndependentOfDay() {
        List<TaskListItem> items = useCase.execute(LoadTaskWidgetItemsUseCase.CATEGORY_ALL);

        assertEquals(3, items.size());
        assertEquals("Wichtig", items.get(0).title);
        assertEquals("Mittel", items.get(1).title);
        assertEquals("Niedrig", items.get(2).title);
    }

    @Test
    public void widgetCategoryFilterReturnsOnlyThatCategory() {
        String workId = db.taskCategoryDao().readAll().stream()
                .filter(c -> c.name.equals("Arbeit")).findFirst().orElseThrow().id;

        List<TaskListItem> items = useCase.execute(workId);

        assertEquals(2, items.size());
        for (TaskListItem item : items) {
            assertTrue("Nur Arbeit-Tasks erwartet", workId.equals(item.categoryId));
        }
    }
}
