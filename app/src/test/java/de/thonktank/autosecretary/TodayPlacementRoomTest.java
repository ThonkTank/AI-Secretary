package de.thonktank.autosecretary;

import static org.junit.Assert.*;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.today.TodayQueue;
import de.thonktank.autosecretary.domain.usecase.*;
import de.thonktank.autosecretary.presentation.DashboardUiMapper;
import de.thonktank.autosecretary.widget.WidgetDashboardMapper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public final class TodayPlacementRoomTest {
    private AppDatabase database;
    private RoomRepositoryFixture repository;
    private int sequence;
    private LocalDate date = LocalDate.of(2026, 9, 12);
    private final Clock clock = new Clock() {
        public LocalDate today() { return date; }
        public LocalTime time() { return LocalTime.NOON; }
    };
    @Before public void setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries().build();
        repository = new RoomRepositoryFixture(database);
    }
    @After public void tearDown() { database.close(); }

    @Test public void overdueMovesToSectionEndThenNextSectionWithoutChangingDueDate() {
        Occurrence a = add("A", TaskSlot.MORNING, date.minusDays(4));
        Occurrence b = add("B", TaskSlot.MORNING, date);
        Occurrence c = add("C", TaskSlot.MIDDAY, date);
        later(a); assertOrder(b, a, c);
        later(a); assertOrder(b, c, a);
        assertEquals(TaskSlot.MIDDAY, queue().get(2).slot);
        assertEquals(a.scheduledOn, repository.today.findOccurrence(a.id).scheduledOn);
        assertEquals(TaskSlot.MORNING, repository.today.findOccurrence(a.id).slot);
        assertEquals(TaskSlot.MORNING, repository.catalog.scheduleEntries(a.taskId).get(0).slot);
    }
    @Test public void titleMovesAcrossDatesAndSlotsAndPersistsForNewReader() {
        Occurrence a = add("A", TaskSlot.MORNING, date.minusDays(4));
        Occurrence b = add("B", TaskSlot.MIDDAY, date);
        Occurrence c = add("C", TaskSlot.EVENING, date);
        move(c, MoveTodayItem.Action.FIRST); assertOrder(c, a, b);
        assertEquals(TaskSlot.MORNING, queue().get(0).slot);
        repository = new RoomRepositoryFixture(database);
        assertOrder(c, a, b);
        move(c, MoveTodayItem.Action.FIRST); assertOrder(c, a, b);
        Dashboard data = repository.dashboard().execute(date);
        assertEquals(c.id, new WidgetDashboardMapper((id, args) -> "text").map(data, date).focus.itemId);
        assertEquals(c.id, new DashboardUiMapper((id, args) -> "text").map(data, date).focus.actionTarget.item.id);
    }
    @Test public void singleItemTraversesEmptySectionsThenHidesUntilTomorrow() {
        Occurrence a = add("A", TaskSlot.MORNING, date.minusDays(2));
        later(a); assertEquals(TaskSlot.MIDDAY, queue().get(0).slot);
        later(a); assertEquals(TaskSlot.EVENING, queue().get(0).slot);
        later(a); assertEquals(TaskSlot.LATER, queue().get(0).slot);
        later(a); assertTrue(queue().isEmpty());
        repository = new RoomRepositoryFixture(database);
        assertTrue(queue().isEmpty());
        assertTrue(new DashboardUiMapper((id, args) -> "text").map(repository.dashboard().execute(date), date).focus == null);
        date = date.plusDays(1);
        assertOrder(a); assertEquals(TaskSlot.MORNING, queue().get(0).slot);
        assertEquals(a.scheduledOn, repository.today.findOccurrence(a.id).scheduledOn);
    }
    @Test public void tomorrowReturnsAfterItsSectionEvenWhenOverdueDatesInterleaveSections() {
        Occurrence a = add("A", TaskSlot.MIDDAY, date.minusDays(2));
        Occurrence b = add("B", TaskSlot.MIDDAY, date.minusDays(1));
        Occurrence c = add("C", TaskSlot.MORNING, date);
        later(a); later(a); later(a); later(a);
        assertOrder(b, c);
        date = date.plusDays(1);
        assertOrder(b, a, c);
    }

    @Test public void placementSurvivesDatabaseCloseAndCompletedTargetIsCleaned() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        String name = "today-placement-restart";
        database.close(); context.deleteDatabase(name);
        database = Room.databaseBuilder(context, AppDatabase.class, name).allowMainThreadQueries().build();
        repository = new RoomRepositoryFixture(database);
        try {
            Occurrence a = add("A", TaskSlot.MORNING, date);
            Occurrence b = add("B", TaskSlot.MORNING, date);
            later(a);
            database.close();
            database = Room.databaseBuilder(context, AppDatabase.class, name).allowMainThreadQueries().build();
            repository = new RoomRepositoryFixture(database);
            assertOrder(b, a);
            repository.today.updateOccurrence(b.complete(date));
            assertOrder(a);
            assertFalse(repository.today.todayPlacements().stream().anyMatch(p -> p.id.equals(b.id)));
        } finally { database.close(); context.deleteDatabase(name); }
    }

    @Test public void staleTargetDoesNotMoveOtherWorkAndFailureRollsBackQueue() {
        Occurrence a = add("A", TaskSlot.MORNING, date);
        Occurrence b = add("B", TaskSlot.MORNING, date);
        assertFalse(mover().execute(TodayPlacement.Kind.OCCURRENCE, "missing", MoveTodayItem.Action.FIRST));
        database.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER fail_placement BEFORE INSERT ON today_placements WHEN NEW.position=1 "
                + "BEGIN SELECT RAISE(ABORT, 'test failure'); END");
        try { later(a); fail("Expected transaction failure"); } catch (RuntimeException expected) { }
        assertTrue(repository.today.todayPlacements().isEmpty()); assertOrder(a, b);
    }
    private Occurrence add(String title, TaskSlot slot, LocalDate due) {
        TaskDefinition definition = new TaskDefinition(title, null, slot, Recurrence.ONCE,
                1, 0, 0, TaskBoundKind.FOREVER, null, null, null, null, "", Collections.emptyList());
        TaskId task = new CreateTask(repository.catalog, repository.steps, repository.today,
                repository.transactions, clock, () -> "id-" + (++sequence)).execute(definition);
        Occurrence occurrence = new Occurrence("occ-" + (++sequence), task, due, slot,
                OccurrenceState.OPEN, sequence, null);
        repository.today.insertOccurrence(occurrence); return occurrence;
    }
    private MoveTodayItem mover() { return new MoveTodayItem(repository.today,
            repository.dashboard(), repository.transactions, clock); }
    private void move(Occurrence value, MoveTodayItem.Action action) {
        assertTrue(mover().execute(TodayPlacement.Kind.OCCURRENCE, value.id, action));
    }
    private void later(Occurrence value) { move(value, MoveTodayItem.Action.LATER); }
    private List<TodayQueue.Entry> queue() { return TodayQueue.visible(repository.dashboard().execute(date), date); }
    private void assertOrder(Occurrence... expected) {
        assertEquals(Arrays.stream(expected).map(e -> e.id).collect(Collectors.toList()),
                queue().stream().map(e -> e.id).collect(Collectors.toList()));
    }
}
