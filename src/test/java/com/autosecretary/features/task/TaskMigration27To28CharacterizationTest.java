package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

/**
 * Protects the invariant that migrating v27 → v28 flattens the task hierarchy into categories:
 * every parent task becomes a {@code task_category} (keeping its id, title, icon, colour), each
 * child task is assigned that category, the promoted parent rows (and their slots/pref-slots/
 * prerequisites) are removed, and {@code task_relation} is dropped. Non-hierarchy tasks keep a
 * NULL category. Uses {@code exportSchema = false}, so the v27 schema is hand-built and the
 * migration is invoked directly rather than via {@code MigrationTestHelper}.
 */
public final class TaskMigration27To28CharacterizationTest extends AutoSecretaryRobolectricTest {

    private SupportSQLiteOpenHelper helper;
    private SupportSQLiteDatabase db;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        SupportSQLiteOpenHelper.Configuration config = SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // in-memory
                .callback(new SupportSQLiteOpenHelper.Callback(27) {
                    @Override
                    public void onCreate(SupportSQLiteDatabase database) {
                        createV27Schema(database);
                    }

                    @Override
                    public void onUpgrade(SupportSQLiteDatabase database, int oldVersion, int newVersion) {
                        // No-op: this fixture only ever opens at version 27.
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
    public void migration27To28PromotesParentsToCategoriesAndDropsHierarchy() {
        // A parent "Haushalt" with a child "Küche wischen", plus an independent task "Sport".
        insertTask("parent", "Haushalt", "🏠", "#FF112233");
        insertTask("child", "Küche wischen", "🎯", "#FF445566");
        insertTask("solo", "Sport", "🎯", "#FF778899");
        db.execSQL("INSERT INTO task_relation(child, parent) VALUES ('child', 'parent')");
        // Parent has its own slot/pref-slot/prerequisite; child keeps a slot that must survive.
        db.execSQL("INSERT INTO task_slots(id, taskId) VALUES ('slotP', 'parent')");
        db.execSQL("INSERT INTO task_slots(id, taskId) VALUES ('slotC', 'child')");
        db.execSQL("INSERT INTO task_pref_slots(id, taskId) VALUES ('prefP', 'parent')");
        db.execSQL("INSERT INTO task_prerequisites(taskId, prerequisiteId) VALUES ('parent', 'x')");

        AppDatabase.MIGRATION_27_28.migrate(db);

        // Parent promoted to a category keeping its id, title, icon, colour.
        assertEquals("Haushalt", scalarString("SELECT name FROM task_category WHERE id = 'parent'"));
        assertEquals("🏠", scalarString("SELECT icon FROM task_category WHERE id = 'parent'"));
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM task_category"));

        // Parent task row (and its dependents) removed; child + solo remain.
        assertFalse(rowExists("SELECT 1 FROM task_core WHERE id = 'parent'"));
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM task_slots WHERE taskId = 'parent'"));
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM task_pref_slots WHERE taskId = 'parent'"));
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM task_prerequisites WHERE taskId = 'parent'"));

        // Child assigned to the new category; its own slot survives.
        assertEquals("parent", scalarString("SELECT categoryId FROM task_core WHERE id = 'child'"));
        assertTrue(rowExists("SELECT 1 FROM task_slots WHERE id = 'slotC'"));

        // Independent task stays uncategorised.
        assertTrue(rowExists("SELECT 1 FROM task_core WHERE id = 'solo'"));
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM task_core WHERE id = 'solo' AND categoryId IS NOT NULL"));

        // Hierarchy table is gone.
        assertEquals(0, scalarLong(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'task_relation'"));
    }

    /** Minimal subset of the v27 schema touched by the migration. */
    private static void createV27Schema(SupportSQLiteDatabase database) {
        database.execSQL("CREATE TABLE task_core (id TEXT NOT NULL PRIMARY KEY, title TEXT, "
                + "goalIcon TEXT NOT NULL, goalColorHex TEXT NOT NULL)");
        database.execSQL("CREATE TABLE task_relation (child TEXT NOT NULL, parent TEXT NOT NULL, "
                + "PRIMARY KEY(child, parent))");
        database.execSQL("CREATE TABLE task_slots (id TEXT NOT NULL PRIMARY KEY, taskId TEXT)");
        database.execSQL("CREATE TABLE task_pref_slots (id TEXT NOT NULL PRIMARY KEY, taskId TEXT)");
        database.execSQL("CREATE TABLE task_prerequisites (taskId TEXT NOT NULL, prerequisiteId TEXT NOT NULL, "
                + "PRIMARY KEY(taskId, prerequisiteId))");
    }

    private void insertTask(String id, String title, String icon, String colorHex) {
        db.execSQL("INSERT INTO task_core(id, title, goalIcon, goalColorHex) VALUES (?, ?, ?, ?)",
                new Object[]{id, title, icon, colorHex});
    }

    private String scalarString(String sql) {
        try (Cursor cursor = db.query(sql)) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private long scalarLong(String sql) {
        try (Cursor cursor = db.query(sql)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : -1;
        }
    }

    private boolean rowExists(String sql) {
        try (Cursor cursor = db.query(sql)) {
            return cursor.moveToFirst();
        }
    }
}
