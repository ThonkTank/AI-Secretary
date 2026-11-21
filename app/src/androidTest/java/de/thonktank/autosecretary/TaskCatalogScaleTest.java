package de.thonktank.autosecretary;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import de.thonktank.autosecretary.data.local.RoomCatalogRepository;
import de.thonktank.autosecretary.data.local.RoomStepRepository;
import de.thonktank.autosecretary.data.local.RoomTransactionRunner;
import de.thonktank.autosecretary.data.local.TaskEntity;
import de.thonktank.autosecretary.data.local.TaskScheduleEntity;
import de.thonktank.autosecretary.data.local.TaskStepEntity;
import de.thonktank.autosecretary.domain.model.TaskCatalog;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.usecase.LoadTaskCatalog;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public final class TaskCatalogScaleTest {
    @Test public void fullCatalogReadsAllTasksStepsAndPlacementsWithThreeSelects() {
        AtomicInteger selects = new AtomicInteger();
        AppDatabase db = Room.inMemoryDatabaseBuilder(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), AppDatabase.class)
                .setQueryCallback((sql, args) -> {
                    if (sql.startsWith("SELECT * FROM")) selects.incrementAndGet();
                }, Runnable::run).build();
        try {
            db.runInTransaction(() -> {
                for (int i = 1199; i >= 0; i--) {
                    String id = "task-" + i;
                    db.catalog().insertTask(new TaskEntity(id, id, "DAILY", 1, 0, false, "",
                            false, i % 2 == 0, "2026-01-01", null, null, null, i,
                            false, null, "FOREVER", null, null, null, null, ""));
                    db.steps().insertTemplates(Collections.singletonList(
                            new TaskStepEntity("step-" + i, id, 0, id)));
                    db.catalog().putScheduleEntries(Arrays.asList(
                            new TaskScheduleEntity("evening-" + i, id, "EVENING", i),
                            new TaskScheduleEntity("morning-" + i, id, "MORNING", i)));
                }
            });
            RoomTransactionRunner transactions = new RoomTransactionRunner(db);
            selects.set(0);
            TaskCatalog result = new LoadTaskCatalog(new RoomCatalogRepository(db),
                    new RoomStepRepository(db, transactions), transactions).execute();
            assertEquals(3, selects.get());
            assertEquals(1200, result.items.size());
            for (int i = 0; i < result.items.size(); i++) {
                TaskCatalog.Item item = result.items.get(i);
                assertEquals("task-" + i, item.task.id.value);
                assertEquals(i % 2 == 0, item.task.archived);
                assertEquals(1, item.steps.size());
                assertEquals("step-" + i, item.steps.get(0).id);
                assertEquals(2, item.schedule.size());
                assertEquals(TaskSlot.MORNING, item.schedule.get(0).slot);
                assertEquals(TaskSlot.EVENING, item.schedule.get(1).slot);
            }
        } finally {
            db.close();
        }
    }
}
