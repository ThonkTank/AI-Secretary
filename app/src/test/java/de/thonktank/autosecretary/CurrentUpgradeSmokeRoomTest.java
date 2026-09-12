package de.thonktank.autosecretary;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.today.TodayQueue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Current-smoke data must survive actual startup use cases, not just SQL insertion. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public final class CurrentUpgradeSmokeRoomTest {
    @Test public void seededCurrentStateSurvivesMaterializationProjectionAndReopening() throws Exception {
        verifyOn(LocalDate.of(2026, 9, 12));
    }

    @Test public void calendarBindingAlsoPreservesStateOnALaterDay() throws Exception {
        verifyOn(LocalDate.of(2031, 6, 18));
    }

    private void verifyOn(LocalDate day) throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "current-smoke-" + java.util.UUID.randomUUID();
        JSONObject fixture = new JSONObject(Files.readString(Path.of("../release/current-smoke/schema-27.json"))
                .replace("${TODAY}", day.toString()));
        Clock clock = new Clock() {
            public LocalDate today() { return day; }
            public LocalTime time() { return LocalTime.NOON; }
        };
        try {
            for (int pass = 0; pass < 2; pass++) {
                AppDatabase database = Room.databaseBuilder(context, AppDatabase.class, name)
                        .allowMainThreadQueries().setQueryExecutor(Runnable::run)
                        .setTransactionExecutor(Runnable::run).build();
                try {
                    var sql = database.getOpenHelper().getWritableDatabase();
                    assertEquals(fixture.getInt("databaseVersion"), sql.getVersion());
                    if (pass == 0) {
                        JSONArray seed = fixture.getJSONArray("seed");
                        database.runInTransaction(() -> {
                            try {
                                for (int i = 0; i < seed.length(); i++) {
                                    JSONObject entry = seed.getJSONObject(i);
                                    JSONArray rows = entry.getJSONArray("rows");
                                    for (int r = 0; r < rows.length(); r++) {
                                        JSONObject row = rows.getJSONObject(r);
                                        List<String> columns = keys(row);
                                        List<Object> values = new ArrayList<>();
                                        for (String column : columns) values.add(row.isNull(column) ? null : row.get(column));
                                        sql.execSQL("INSERT INTO " + entry.getString("table") + "(" + String.join(",", columns)
                                                + ") VALUES (" + String.join(",", Collections.nCopies(columns.size(), "?")) + ")",
                                                values.toArray());
                                    }
                                }
                            } catch (Exception error) { throw new AssertionError(error); }
                        });
                    }
                    var useCases = new ApplicationUseCaseComposition(database, clock,
                            () -> java.util.UUID.randomUUID().toString(), ComboPolicySource.defaults());
                    useCases.today.materializeDue.execute();
                    var queue = TodayQueue.visible(useCases.today.loadDashboard.execute(clock.today()), clock.today());
                    assertEquals(1, queue.size());
                    assertEquals("current-smoke-open", queue.get(0).id);
                    assertEquals(TaskSlot.MIDDAY, queue.get(0).slot);
                    JSONArray expected = fixture.getJSONArray("expectedTarget");
                    for (int i = 0; i < expected.length(); i++) {
                        JSONObject entry = expected.getJSONObject(i);
                        try (var row = sql.query("SELECT * FROM " + entry.getString("table") + " WHERE id=?",
                                new Object[]{entry.getJSONObject("where").getString("id")})) {
                            assertTrue(row.moveToFirst());
                            JSONObject values = entry.getJSONObject("values");
                            for (String column : keys(values)) {
                                assertEquals(entry.getString("table") + ":" + column,
                                        values.isNull(column) ? null : values.get(column).toString(),
                                        row.getString(row.getColumnIndexOrThrow(column)));
                            }
                            assertFalse(row.moveToNext());
                        }
                    }
                    try (var errors = sql.query("PRAGMA foreign_key_check")) { assertFalse(errors.moveToFirst()); }
                } finally { database.close(); }
            }
        } finally { context.deleteDatabase(name); }
    }

    private static List<String> keys(JSONObject value) {
        List<String> keys = new ArrayList<>();
        value.keys().forEachRemaining(keys::add);
        return keys;
    }
}
