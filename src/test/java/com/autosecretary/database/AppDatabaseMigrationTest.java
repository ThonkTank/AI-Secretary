package com.autosecretary.database;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.database.Cursor;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.testing.AutoSecretaryRobolectricTest;

import org.junit.Test;

/**
 * Invariant protected: migration 29→30 heals tasks whose {@code repetition_periodUnit} is NULL
 * (the assistant one-off-task state that crashed the scheduler) to 'DAY', leaving set values alone.
 */
public final class AppDatabaseMigrationTest extends AutoSecretaryRobolectricTest {

    @Test
    public void migration29To30HealsNullPeriodUnitToDay() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(null) // in-memory
                        .callback(new SupportSQLiteOpenHelper.Callback(30) {
                            @Override public void onCreate(SupportSQLiteDatabase db) { }
                            @Override public void onUpgrade(SupportSQLiteDatabase db, int oldV, int newV) { }
                        })
                        .build());
        SupportSQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("CREATE TABLE task_core (id TEXT PRIMARY KEY NOT NULL, repetition_periodUnit TEXT)");
        db.execSQL("INSERT INTO task_core (id, repetition_periodUnit) VALUES ('broken', NULL)");
        db.execSQL("INSERT INTO task_core (id, repetition_periodUnit) VALUES ('kept', 'WEEK')");

        AppDatabase.MIGRATION_29_30.migrate(db);

        assertEquals("DAY", periodUnitOf(db, "broken"));
        assertEquals("WEEK", periodUnitOf(db, "kept"));
        db.close();
    }

    private static String periodUnitOf(SupportSQLiteDatabase db, String id) {
        try (Cursor c = db.query("SELECT repetition_periodUnit FROM task_core WHERE id = '" + id + "'")) {
            c.moveToFirst();
            return c.getString(0);
        }
    }
}
