package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.today.TodayStepMoveResult;
import de.thonktank.autosecretary.domain.usecase.CreateTask;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.usecase.MoveTodayStep;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class TodayStepOrderRoomTest {
    private AppDatabase database;
    private RoomRepositoryFixture repository;
    private int id;

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        repository = new RoomRepositoryFixture(database);
    }

    @After public void tearDown() { database.close(); }

    @Test public void roomWritesOnlyChangedPositionsAndNeverTemplatesOrRepetitions() {
        MutableClock clock = new MutableClock();
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        new CreateTask(repository.catalog, repository.steps, repository.today, repository.transactions, clock, ids).execute(TaskDefinition.basic(
                "Routine", TaskSlot.MORNING, Recurrence.DAILY, 1, 0,
                Arrays.asList("A", "B", "C", "D", "E")));
        new MaterializeDueOccurrences(repository.catalog, repository.steps, repository.today, repository.flows, repository.transactions, clock, ids).execute();
        Occurrence occurrence = repository.today.openOccurrences().get(0);
        java.util.List<OccurrenceStep> steps = repository.steps.occurrenceSteps(occurrence.id);
        repository.steps.updateOccurrenceStep(withDone(steps.get(1), true));
        repository.steps.updateOccurrenceStep(withDone(steps.get(3), true));
        repository.steps.updateOccurrenceStep(de.thonktank.autosecretary.testing.StepTestFixtures.occurrence(steps.get(0).id, occurrence.id, 0,
                steps.get(0).text, false, StepAmount.setsReps(2, 12), "",
                Collections.singletonList(9), steps.get(0).sourceTemplateId,
                steps.get(0).comboOwnerId));

        SupportSQLiteDatabase sql = database.getOpenHelper().getWritableDatabase();
        installAudit(sql);
        TodayStepMoveResult result = new MoveTodayStep(repository.steps, repository.today, repository.transactions).execute(
                steps.get(4).id, steps.get(0).id);

        assertEquals(TodayStepMoveResult.Status.MOVED, result.status);
        assertEquals(3, result.positionUpdates.size());
        assertEquals(3, auditCount(sql, "occurrence_steps"));
        assertEquals(0, auditCount(sql, "task_steps"));
        assertEquals(0, auditCount(sql, "repetition_results"));
        assertEquals(Collections.singletonList(9), repository.steps.findOccurrenceStep(steps.get(0).id)
                .repetitionProgress.repetitions());
        assertEquals(5, repository.steps.templates(occurrence.taskId).size());

        TodayStepMoveResult noChange = new MoveTodayStep(repository.steps, repository.today, repository.transactions).execute(
                steps.get(4).id, steps.get(0).id);
        assertEquals(TodayStepMoveResult.Status.NO_CHANGE, noChange.status);
        assertEquals(3, auditCount(sql, "occurrence_steps"));
    }

    private static OccurrenceStep withDone(OccurrenceStep step, boolean done) {
        return de.thonktank.autosecretary.testing.StepTestFixtures.occurrence(step.id, step.occurrenceId, step.position, step.text, done,
                step.prescription, step.note, Collections.emptyList(), step.sourceTemplateId,
                step.comboOwnerId);
    }

    private static void installAudit(SupportSQLiteDatabase sql) {
        sql.execSQL("CREATE TEMP TABLE write_audit(tableName TEXT NOT NULL, rowId TEXT NOT NULL)");
        sql.execSQL("CREATE TEMP TRIGGER audit_occurrence AFTER UPDATE OF position ON "
                + "occurrence_steps BEGIN INSERT INTO write_audit VALUES "
                + "('occurrence_steps', NEW.id); END");
        sql.execSQL("CREATE TEMP TRIGGER audit_templates AFTER UPDATE ON task_steps BEGIN "
                + "INSERT INTO write_audit VALUES ('task_steps', NEW.id); END");
        sql.execSQL("CREATE TEMP TRIGGER audit_templates_insert AFTER INSERT ON task_steps BEGIN "
                + "INSERT INTO write_audit VALUES ('task_steps', NEW.id); END");
        sql.execSQL("CREATE TEMP TRIGGER audit_templates_delete AFTER DELETE ON task_steps BEGIN "
                + "INSERT INTO write_audit VALUES ('task_steps', OLD.id); END");
        sql.execSQL("CREATE TEMP TRIGGER audit_repetitions_update AFTER UPDATE ON "
                + "repetition_results BEGIN INSERT INTO write_audit VALUES "
                + "('repetition_results', NEW.stepId); END");
        sql.execSQL("CREATE TEMP TRIGGER audit_repetitions_insert AFTER INSERT ON "
                + "repetition_results BEGIN INSERT INTO write_audit VALUES "
                + "('repetition_results', NEW.stepId); END");
        sql.execSQL("CREATE TEMP TRIGGER audit_repetitions_delete AFTER DELETE ON "
                + "repetition_results BEGIN INSERT INTO write_audit VALUES "
                + "('repetition_results', OLD.stepId); END");
    }

    private static int auditCount(SupportSQLiteDatabase sql, String table) {
        try (Cursor cursor = sql.query("SELECT COUNT(*) FROM write_audit WHERE tableName = ?",
                new Object[]{table})) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    private static final class MutableClock implements Clock {
        @Override public LocalDate today() { return LocalDate.of(2026, 8, 21); }
        @Override public LocalTime time() { return LocalTime.NOON; }
    }
}
