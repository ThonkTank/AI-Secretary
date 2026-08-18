package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.local.RoomTaskRepository;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.TaskRepository;
import de.thonktank.autosecretary.domain.usecase.CreateTask;
import de.thonktank.autosecretary.domain.usecase.HarvestOccurrence;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.usecase.ToggleStep;
import de.thonktank.autosecretary.domain.usecase.UndoOccurrence;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class XpComboRobolectricTest {
    private final LocalDate today = LocalDate.of(2026, 8, 18);
    private AppDatabase database;
    private TaskRepository repository;
    private Clock clock;
    private int id;

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        repository = new RoomTaskRepository(database);
        clock = new Clock() {
            @Override public LocalDate today() { return today; }
            @Override public LocalTime time() { return LocalTime.NOON; }
        };
    }

    @After public void tearDown() { database.close(); }

    @Test public void stepAndRoutineUseTheirOwnFactorsAndTodayUndoIsExact() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        new CreateTask(repository, clock, ids, new TaskOrdering()).execute("Routine",
                TaskSlot.MORNING, Recurrence.DAILY, 1, 0,
                Collections.singletonList("Schritt"), false, "");
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        Occurrence occurrence = repository.openOccurrences().get(0);
        OccurrenceStep step = repository.occurrenceSteps(occurrence.id).get(0);
        repository.putCombo(new ComboProgress(step.comboOwnerId, occurrence.taskId,
                ComboProgress.Kind.STEP, 6, today));
        repository.putCombo(new ComboProgress(ComboProgress.taskOwner(occurrence.taskId),
                occurrence.taskId, ComboProgress.Kind.TASK, 3, today));

        new ToggleStep(repository, clock).execute(step.id);
        assertEquals(25, repository.findOccurrenceStep(step.id).earnedXp);
        new HarvestOccurrence(repository, clock).execute(occurrence.id);
        assertEquals(50, repository.xp());
        assertEquals(OccurrenceState.COMPLETED, repository.findOccurrence(occurrence.id).state);

        new UndoOccurrence(repository, clock).execute(occurrence.id);
        assertEquals(0, repository.xp());
        assertEquals(OccurrenceState.OPEN, repository.findOccurrence(occurrence.id).state);
        assertTrue(repository.findOccurrenceStep(step.id).done);
        new ToggleStep(repository, clock).execute(step.id);
        assertEquals(6, repository.combo(step.comboOwnerId).points);
        assertEquals(3, repository.combo(ComboProgress.taskOwner(occurrence.taskId)).points);
    }
}
