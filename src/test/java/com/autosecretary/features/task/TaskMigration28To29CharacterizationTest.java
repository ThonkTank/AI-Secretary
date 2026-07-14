package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * Protects the invariant that migrating v28 → v29 introduces the leisure flag and the
 * category-window table without touching existing data: {@code task_core.leisure} is added with a
 * default of 0 (existing rows read back as non-leisure), and {@code task_category_window} exists
 * with exactly the expected columns. Uses {@code exportSchema = false}, so the v28 schema is
 * hand-built and the migration invoked directly.
 */
public final class TaskMigration28To29CharacterizationTest extends AutoSecretaryRobolectricTest {

    private SupportSQLiteOpenHelper helper;
    private SupportSQLiteDatabase db;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        SupportSQLiteOpenHelper.Configuration config = SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // in-memory
                .callback(new SupportSQLiteOpenHelper.Callback(28) {
                    @Override
                    public void onCreate(SupportSQLiteDatabase database) {
                        createV28Schema(database);
                    }

                    @Override
                    public void onUpgrade(SupportSQLiteDatabase database, int oldVersion, int newVersion) {
                        // No-op: this fixture only ever opens at version 28.
                    }
                })
                .build();
        helper = new FrameworkSQLiteOpenHelperFactory().create(config);
        db = helper.getWritableDatabase();
    }

    @After
    public void tearDown() {
        helper.close();
    }

    @Test
    public void migration28To29AddsLeisureDefaultZeroAndCategoryWindowTable() {
        db.execSQL("INSERT INTO task_core(id, title) VALUES ('t1', 'Bestehende Task')");

        AppDatabase.MIGRATION_28_29.migrate(db);

        // Existing row gains leisure = 0 (non-leisure) by default.
        assertEquals(0, scalarLong("SELECT leisure FROM task_core WHERE id = 't1'"));

        // The category-window table exists with exactly the expected columns.
        assertTrue(tableExists("task_category_window"));
        Set<String> columns = columnNames("task_category_window");
        assertEquals(Set.of("id", "dayOfWeek", "categoryId", "startTime", "endTime"), columns);
    }

    /** Minimal subset of the v28 schema touched by the migration. */
    private static void createV28Schema(SupportSQLiteDatabase database) {
        database.execSQL("CREATE TABLE task_core (id TEXT NOT NULL PRIMARY KEY, title TEXT)");
    }

    private long scalarLong(String sql) {
        try (Cursor cursor = db.query(sql)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : -1;
        }
    }

    private boolean tableExists(String table) {
        try (Cursor cursor = db.query(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", new Object[]{table})) {
            return cursor.moveToFirst();
        }
    }

    private Set<String> columnNames(String table) {
        Set<String> names = new HashSet<>();
        try (Cursor cursor = db.query("PRAGMA table_info(" + table + ")")) {
            int nameIndex = cursor.getColumnIndexOrThrow("name");
            while (cursor.moveToNext()) {
                names.add(cursor.getString(nameIndex));
            }
        }
        return names;
    }
}
