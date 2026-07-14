package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class TaskSchemaSnapshotCharacterizationTest extends AutoSecretaryRobolectricTest {
    private AppDatabase db;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void taskRoomSchemaKeepsTablesColumnsIndexesAndForeignKeysInvariant() {
        assertColumns("task_core",
                "id", "title", "description", "goalIcon", "goalColorHex",
                "budgetRequiredCents", "budgetAccountId", "budgetCategoryId", "categoryId", "mealType",
                "priority", "schedulingType", "cooldown", "startDate", "deadline",
                "fixedDate", "fixedStart", "fixedEnd", "fixedDuration", "created",
                "closeOnMiss", "adaptive", "leisure", "minDuration", "maxDuration", "completed",
                "history_completions", "history_trackedCompletions",
                "history_currentStreak", "history_nrStreaks", "history_totalDuration",
                "repetition_reps", "repetition_periodCompletions",
                "repetition_periodStart", "repetition_completeFirst",
                "repetition_carryoverDebt", "repetition_perPeriod",
                "repetition_periodUnit", "progress_unit", "progress_resetPerRep",
                "progress_target", "progress_current", "progress_minPerRep",
                "progress_maxPerRep", "progress_totalProgress", "progress_totalTime");
        assertColumns("task_slots",
                "id", "taskId", "parent", "chainId", "day", "start", "end",
                "scheduled", "completed", "score", "displacementScore",
                "displacementGroupId", "displacementGroupType", "realStart", "realEnd");
        assertColumns("task_pref_slots", "id", "taskId", "days", "start");
        assertColumns("task_category", "id", "name", "icon", "colorHex", "sortOrder");
        assertColumns("task_category_window", "id", "dayOfWeek", "categoryId", "startTime", "endTime");
        assertColumns("task_prerequisites", "taskId", "prerequisiteId", "minGapMinutes");
        assertColumns("task_planned_meals",
                "taskId", "day", "recipeId", "plannedServings", "completed", "actualServings");

        assertHasIndex("task_slots", "taskId");
        assertHasIndex("task_pref_slots", "taskId");
        assertHasIndex("task_prerequisites", "taskId");
        assertHasIndex("task_planned_meals", "taskId");

        assertForeignKey("task_slots", "taskId", "task_core", "id", "CASCADE");
        assertForeignKey("task_pref_slots", "taskId", "task_core", "id", "CASCADE");
        assertForeignKey("task_prerequisites", "taskId", "task_core", "id", "CASCADE");
        assertForeignKey("task_planned_meals", "taskId", "task_core", "id", "CASCADE");
    }

    private void assertColumns(String tableName, String... expectedColumns) {
        Set<String> actual = new HashSet<>();
        try (Cursor cursor = db.getOpenHelper().getWritableDatabase()
                .query("PRAGMA table_info(`" + tableName + "`)")) {
            int nameIndex = cursor.getColumnIndexOrThrow("name");
            while (cursor.moveToNext()) {
                actual.add(cursor.getString(nameIndex));
            }
        }
        assertEquals(tableName, new HashSet<>(Arrays.asList(expectedColumns)), actual);
    }

    private void assertHasIndex(String tableName, String indexedColumn) {
        Set<String> indexedColumns = new HashSet<>();
        try (Cursor indexCursor = db.getOpenHelper().getWritableDatabase()
                .query("PRAGMA index_list(`" + tableName + "`)")) {
            int indexNameColumn = indexCursor.getColumnIndexOrThrow("name");
            while (indexCursor.moveToNext()) {
                String indexName = indexCursor.getString(indexNameColumn);
                try (Cursor infoCursor = db.getOpenHelper().getWritableDatabase()
                        .query("PRAGMA index_info(`" + indexName + "`)")) {
                    int columnNameIndex = infoCursor.getColumnIndexOrThrow("name");
                    while (infoCursor.moveToNext()) {
                        indexedColumns.add(infoCursor.getString(columnNameIndex));
                    }
                }
            }
        }
        assertTrue(tableName + " index on " + indexedColumn, indexedColumns.contains(indexedColumn));
    }

    private void assertForeignKey(String tableName,
                                  String fromColumn,
                                  String targetTable,
                                  String targetColumn,
                                  String onDelete) {
        try (Cursor cursor = db.getOpenHelper().getWritableDatabase()
                .query("PRAGMA foreign_key_list(`" + tableName + "`)")) {
            int fromIndex = cursor.getColumnIndexOrThrow("from");
            int tableIndex = cursor.getColumnIndexOrThrow("table");
            int toIndex = cursor.getColumnIndexOrThrow("to");
            int onDeleteIndex = cursor.getColumnIndexOrThrow("on_delete");
            while (cursor.moveToNext()) {
                boolean matches = fromColumn.equals(cursor.getString(fromIndex))
                        && targetTable.equals(cursor.getString(tableIndex))
                        && targetColumn.equals(cursor.getString(toIndex))
                        && onDelete.equals(cursor.getString(onDeleteIndex));
                if (matches) {
                    return;
                }
            }
        }
        throw new AssertionError(tableName + " foreign key " + fromColumn + " -> "
                + targetTable + "." + targetColumn + " ON DELETE " + onDelete);
    }
}
