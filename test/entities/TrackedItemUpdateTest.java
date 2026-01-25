package entities;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.time.LocalDate;
import java.time.LocalTime;

import entities.trackedItem.*;
import repository.SQLrepo;
import repository.Table;

@RunWith(RobolectricTestRunner.class)
public class TrackedItemUpdateTest {

    private SQLrepo repo;
    private LocalDate tag;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("autosecretary.db");
        repo = new SQLrepo(context);
        tag = LocalDate.of(2026, 1, 22);
    }

    // --- Helper ---

    private trackedItem taskMitInterval(int intervalTage) {
        trackedItem item = new trackedItem.Builder(ItemType.TASK, "Test", Priority.MODERATE)
                .created("2026-01-01")
                .repetition(RepetitionType.INTERVAL, intervalTage, RepUnits.DAY)
                .build();
        repo.write(item);
        return repo.fetch(Table.ITEMS, item.id);
    }

    private trackedItem taskMitReps(int reps) {
        trackedItem item = new trackedItem.Builder(ItemType.TASK, "RepsTest", Priority.MODERATE)
                .created("2026-01-01")
                .repetition(RepetitionType.REPS_PER_TIME, reps, RepUnits.WEEK)
                .build();
        repo.write(item);
        return repo.fetch(Table.ITEMS, item.id);
    }

    // ============================================================================
    // COMPLETED = TRUE
    // ============================================================================

    @Test
    public void completedTrue_inkrementiertCompletions() {
        trackedItem item = taskMitInterval(7);
        assertEquals(0, item.completions);

        item.update(true, LocalTime.of(14, 0), tag, repo);

        trackedItem geladen = repo.fetch(Table.ITEMS, item.id);
        assertEquals(1, geladen.completions);
    }

    @Test
    public void completedTrue_setztLastCompletion() {
        trackedItem item = taskMitInterval(7);
        assertNull(item.lastCompletion);

        item.update(true, LocalTime.of(14, 0), tag, repo);

        trackedItem geladen = repo.fetch(Table.ITEMS, item.id);
        assertEquals(tag, geladen.lastCompletion);
    }

    @Test
    public void completedTrue_aktualisiertPrefTime_erstmal() {
        trackedItem item = taskMitInterval(7);
        assertNull(item.prefTime);

        item.update(true, LocalTime.of(16, 30), tag, repo);

        trackedItem geladen = repo.fetch(Table.ITEMS, item.id);
        assertEquals(LocalTime.of(16, 30), geladen.prefTime);
        assertEquals(1, geladen.totalCompletions);
    }

    @Test
    public void completedTrue_aktualisiertPrefTime_durchschnitt() {
        trackedItem item = taskMitInterval(7);
        // Manuell prefTime setzen: 14:00, totalCompletions=9
        item.prefTime = LocalTime.of(14, 0);
        item.totalCompletions = 9;
        repo.write(item);

        item.update(true, LocalTime.of(16, 0), tag, repo);

        trackedItem geladen = repo.fetch(Table.ITEMS, item.id);
        // (14*60*9 + 16*60) / 10 = (7560 + 960) / 10 = 852 min = 14:12
        assertEquals(LocalTime.of(14, 12), geladen.prefTime);
        assertEquals(10, geladen.totalCompletions);
    }

    @Test
    public void completedTrue_prefTimeNurBeiTasks() {
        trackedItem item = new trackedItem.Builder(ItemType.GOAL, "GoalTest", Priority.MODERATE)
                .created("2026-01-01")
                .repetition(RepetitionType.INTERVAL, 7, RepUnits.DAY)
                .build();
        repo.write(item);
        item = repo.fetch(Table.ITEMS, item.id);

        item.update(true, LocalTime.of(14, 0), tag, repo);

        trackedItem geladen = repo.fetch(Table.ITEMS, item.id);
        assertNull(geladen.prefTime);
    }

    // ============================================================================
    // COMPLETED = FALSE
    // ============================================================================

    @Test
    public void completedFalse_ueberfaellig_resetStreak() {
        trackedItem item = taskMitInterval(3);
        item.currentStreak = 5;
        item.lastCompletion = LocalDate.of(2026, 1, 10); // 12 Tage her → overdue
        repo.write(item);

        item.update(false, null, tag, repo);

        trackedItem geladen = repo.fetch(Table.ITEMS, item.id);
        assertEquals(0, geladen.currentStreak);
        assertEquals(1, geladen.nrOfStreaks);
        assertEquals(5, geladen.averageStreak); // (0*(1-1) + 5) / 1
    }

    @Test
    public void completedFalse_ueberfaellig_resetCompletions() {
        trackedItem item = taskMitReps(3);
        item.completions = 2;
        item.lastCompletion = LocalDate.of(2026, 1, 1); // weit überfällig
        repo.write(item);

        item.update(false, null, tag, repo);

        trackedItem geladen = repo.fetch(Table.ITEMS, item.id);
        assertEquals(0, geladen.completions);
    }

    @Test
    public void completedFalse_completeFirstTrue_keinReset() {
        trackedItem item = taskMitInterval(3);
        item.currentStreak = 5;
        item.completeFirst = true;
        item.lastCompletion = LocalDate.of(2026, 1, 10); // überfällig
        repo.write(item);

        item.update(false, null, tag, repo);

        trackedItem geladen = repo.fetch(Table.ITEMS, item.id);
        assertEquals(5, geladen.currentStreak); // Unverändert
    }

    @Test
    public void completedFalse_nichtUeberfaellig_keinReset() {
        trackedItem item = taskMitInterval(7);
        item.currentStreak = 3;
        item.lastCompletion = LocalDate.of(2026, 1, 20); // 2 Tage her, Interval=7 → nicht overdue
        repo.write(item);

        item.update(false, null, tag, repo);

        trackedItem geladen = repo.fetch(Table.ITEMS, item.id);
        assertEquals(3, geladen.currentStreak); // Unverändert
    }

    // ============================================================================
    // PERSISTIERUNG
    // ============================================================================

    @Test
    public void update_persistiertImmer() {
        trackedItem item = taskMitInterval(7);
        long id = item.id;

        item.update(true, LocalTime.of(10, 0), tag, repo);

        trackedItem geladen = repo.fetch(Table.ITEMS, id);
        assertNotNull(geladen);
        assertEquals(1, geladen.completions);
    }
}
