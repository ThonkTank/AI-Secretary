package usecases.daliyPlanning;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import entities.todoList;
import entities.todoList.TimeSlot;
import entities.trackedItem;
import entities.trackedItem.*;
import repository.SQLrepo;
import repository.Table;

@RunWith(RobolectricTestRunner.class)
public class CleanToDoTest {

    private SQLrepo repo;
    private LocalDate gestern;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("autosecretary.db");
        repo = new SQLrepo(context);
        gestern = LocalDate.now().minusDays(1);
    }

    // --- Helper ---

    private trackedItem erstelleTask(String titel) {
        trackedItem item = new trackedItem.Builder(ItemType.TASK, titel, Priority.MODERATE)
                .created("2026-01-01")
                .repetition(RepetitionType.INTERVAL, 1, RepUnits.DAY)
                .build();
        repo.write(item);
        return repo.fetch(Table.ITEMS, item.id);
    }

    private todoList erstelleTodoMitSlots(LocalDate datum, List<TimeSlot> slots) {
        todoList todo = new todoList();
        todo.date = datum;
        todo.start = LocalTime.of(8, 0);
        todo.end = LocalTime.of(22, 0);
        todo.timeSlots = slots;
        repo.write(todo);
        return todo;
    }

    private TimeSlot slotFuer(trackedItem item, boolean completed) {
        TimeSlot slot = new TimeSlot();
        slot.item = item.id;
        slot.start = LocalTime.of(9, 0);
        slot.end = LocalTime.of(9, 30);
        slot.completed = completed;
        return slot;
    }

    // ============================================================================
    // CLEAN TESTS
    // ============================================================================

    @Test
    public void clean_completedSlot_aktualisiertItem() {
        trackedItem task = erstelleTask("Erledigt");

        TimeSlot slot = slotFuer(task, true);
        slot.workStart = LocalTime.of(14, 30);
        List<TimeSlot> slots = new ArrayList<>();
        slots.add(slot);
        erstelleTodoMitSlots(gestern, slots);

        cleanToDo.clean(repo);

        trackedItem geladen = repo.fetch(Table.ITEMS, task.id);
        assertEquals(1, geladen.completions);
        assertEquals(gestern, geladen.lastCompletion);
        assertEquals(LocalTime.of(14, 30), geladen.prefTime);
    }

    @Test
    public void clean_uncompletedSlot_resetBeiOverdue() {
        trackedItem task = erstelleTask("Verpasst");
        task.currentStreak = 3;
        task.lastCompletion = gestern.minusDays(5); // 5 Tage her, Interval=1 → overdue
        repo.write(task);

        List<TimeSlot> slots = new ArrayList<>();
        slots.add(slotFuer(task, false));
        erstelleTodoMitSlots(gestern, slots);

        cleanToDo.clean(repo);

        trackedItem geladen = repo.fetch(Table.ITEMS, task.id);
        assertEquals(0, geladen.currentStreak);
        assertEquals(0, geladen.completions);
    }

    @Test
    public void clean_entferntTodoListe() {
        trackedItem task = erstelleTask("Aufräumen");

        List<TimeSlot> slots = new ArrayList<>();
        slots.add(slotFuer(task, true));
        todoList todo = erstelleTodoMitSlots(gestern, slots);

        cleanToDo.clean(repo);

        // Todo sollte weg sein
        todoList geladen = repo.fetch(Table.TODOS, todo.id);
        assertNull(geladen);
    }

    @Test
    public void clean_entferntTimeSlots() {
        trackedItem task = erstelleTask("SlotCleanup");

        List<TimeSlot> slots = new ArrayList<>();
        slots.add(slotFuer(task, true));
        todoList todo = erstelleTodoMitSlots(gestern, slots);

        cleanToDo.clean(repo);

        // time_slots sollten weg sein
        SQLiteDatabase db = repo.getReadableDatabase();
        Cursor cursor = db.query("time_slots", null,
                "todo_id = ?", new String[]{String.valueOf(todo.id)},
                null, null, null);
        assertEquals(0, cursor.getCount());
        cursor.close();
    }

    @Test
    public void clean_verschachtelteSlots_alleVerarbeitet() {
        trackedItem goal = new trackedItem.Builder(ItemType.GOAL, "GoalTest", Priority.MODERATE)
                .created("2026-01-01")
                .build();
        repo.write(goal);
        goal = repo.fetch(Table.ITEMS, goal.id);

        trackedItem task1 = erstelleTask("Sub1");
        trackedItem task2 = erstelleTask("Sub2");

        // Goal-Slot mit verschachtelten Task-Slots
        TimeSlot goalSlot = new TimeSlot();
        goalSlot.item = goal.id;
        goalSlot.start = LocalTime.of(9, 0);
        goalSlot.end = LocalTime.of(10, 0);
        goalSlot.completed = false;

        TimeSlot subSlot1 = slotFuer(task1, true);
        TimeSlot subSlot2 = slotFuer(task2, true);
        goalSlot.timeSlots = new ArrayList<>();
        goalSlot.timeSlots.add(subSlot1);
        goalSlot.timeSlots.add(subSlot2);

        List<TimeSlot> slots = new ArrayList<>();
        slots.add(goalSlot);
        erstelleTodoMitSlots(gestern, slots);

        cleanToDo.clean(repo);

        // Beide Sub-Tasks sollten geupdated sein
        trackedItem geladen1 = repo.fetch(Table.ITEMS, task1.id);
        assertEquals(1, geladen1.completions);
        assertEquals(gestern, geladen1.lastCompletion);

        trackedItem geladen2 = repo.fetch(Table.ITEMS, task2.id);
        assertEquals(1, geladen2.completions);
        assertEquals(gestern, geladen2.lastCompletion);
    }

    @Test
    public void clean_keineTodoGestern_tutnichts() {
        // Kein Crash wenn keine gestrige Liste existiert
        cleanToDo.clean(repo);
    }
}
