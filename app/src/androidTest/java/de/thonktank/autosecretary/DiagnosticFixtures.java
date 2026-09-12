package de.thonktank.autosecretary;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Base64;

import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Synthetic data and full snapshots; this writer rejects production and physical targets. */
final class DiagnosticFixtures {
    static void requireIsolated(Context context) {
        if (!"de.thonktank.autosecretary.test".equals(context.getPackageName())
                || !("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE))) {
            throw new AssertionError("Diagnostic fixtures require the isolated emulator test package");
        }
    }

    static void seed(Context target, Context helper, int version) throws Exception {
        requireIsolated(target);
        if (version != 24 && version != 27) throw new AssertionError("Unsupported diagnostic fixture schema");
        WorkManager.getInstance(target).cancelAllWork().getResult().get(10, TimeUnit.SECONDS);
        target.deleteDatabase("auto_secretary.db");
        for (String name : List.of("diagnostic-worker-proof.json", "diagnostic-service-proof.json")) {
            Files.deleteIfExists(new File(target.getFilesDir(), name).toPath());
        }
        JSONObject schema = new JSONObject(asset(helper,
                "de.thonktank.autosecretary.AppDatabase/" + version + ".json")).getJSONObject("database");
        File file = target.getDatabasePath("auto_secretary.db");
        file.getParentFile().mkdirs();
        try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null)) {
            db.setForeignKeyConstraintsEnabled(true);
            db.beginTransaction();
            try {
                JSONArray entities = schema.getJSONArray("entities");
                for (int i = 0; i < entities.length(); i++) {
                    JSONObject entity = entities.getJSONObject(i);
                    String table = entity.getString("tableName");
                    db.execSQL(entity.getString("createSql").replace("${TABLE_NAME}", table));
                    JSONArray indices = entity.optJSONArray("indices");
                    if (indices != null) for (int j = 0; j < indices.length(); j++) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${TABLE_NAME}", table));
                    }
                }
                JSONArray setup = schema.getJSONArray("setupQueries");
                for (int i = 0; i < setup.length(); i++) db.execSQL(setup.getString(i));
                JSONArray seed = new JSONObject(asset(helper, "diagnostic-business.json")
                        .replace("${TODAY}", LocalDate.now().toString())).getJSONArray("seed");
                for (int i = 0; i < seed.length(); i++) {
                    JSONObject entry = seed.getJSONObject(i);
                    String table = entry.getString("table");
                    // Schema24 predates these tables and the taskKind column. Its placement
                    // lives in occurrences.slot/sortOrder; no historical archive is invented.
                    if (version == 24 && (table.equals("today_placements")
                            || table.equals("migration_recovery"))) continue;
                    JSONArray rows = entry.getJSONArray("rows");
                    for (int j = 0; j < rows.length(); j++) {
                        JSONObject row = rows.getJSONObject(j);
                        ContentValues values = new ContentValues();
                        for (java.util.Iterator<String> keys = row.keys(); keys.hasNext();) {
                            String key = keys.next();
                            if (version == 24 && table.equals("tasks") && key.equals("taskKind")) continue;
                            Object value = row.get(key);
                            if (value == JSONObject.NULL) values.putNull(key);
                            else if (value instanceof Number) values.put(key, ((Number) value).longValue());
                            else if (value instanceof Boolean) values.put(key, (Boolean) value ? 1 : 0);
                            else values.put(key, value.toString());
                        }
                        db.insertOrThrow(table, null, values);
                    }
                }
                db.setVersion(version);
                try (Cursor violations = db.rawQuery("PRAGMA foreign_key_check", null)) {
                    check(violations.getCount() == 0, "Diagnostic fixture is not healthy");
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
        write(target, "diagnostic-before.json", snapshot(target).toString());
    }

    static void assertUnchanged(Context context) throws Exception {
        requireIsolated(context);
        try {
            AppDatabase forbidden = new de.thonktank.autosecretary.data.local.DatabaseFactory().create(context);
            try {
                forbidden.getOpenHelper().getWritableDatabase();
            } finally { forbidden.close(); }
            throw new AssertionError("Product Room access was allowed in the diagnostic process");
        } catch (IllegalStateException blocked) {
            check("Business database access during diagnostic bootstrap".equals(blocked.getMessage()),
                    "Unexpected failure instead of early Room denial: " + blocked);
        }
        String before = read(context, "diagnostic-before.json");
        String after = snapshot(context).toString();
        check(before.equals(after), "Diagnostic changed business schema/data: expected=" + before + ", actual=" + after);
    }

    static void assertNativeEvents(Context context, int pid) throws Exception {
        JSONObject service = new JSONObject(read(context, "diagnostic-service-proof.json"));
        JSONObject worker = new JSONObject(read(context, "diagnostic-worker-proof.json"));
        check(service.getInt("pid") == pid && service.getBoolean("connected")
                && service.getString("component").equals("androidx.work.impl.background.systemjob.SystemJobService"),
                "SystemJobService was not bound in the diagnostic process: " + service);
        check(worker.getInt("pid") == pid && worker.getString("state").equals("ENQUEUED")
                && worker.getInt("attempts") > 0, "Real worker did not defer: " + worker);
    }

    static void awaitNormalRecovery(Context context) throws Exception {
        requireIsolated(context);
        long deadline = android.os.SystemClock.elapsedRealtime() + 60_000;
        File proof = new File(context.getFilesDir(), "diagnostic-worker-proof.json");
        UUID work = proof.exists() ? UUID.fromString(new JSONObject(read(context,
                "diagnostic-worker-proof.json")).getString("id")) : null;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            boolean timer;
            try (SQLiteDatabase db = open(context); Cursor row = db.rawQuery(
                    "SELECT state,remainingMillis FROM timer_sessions WHERE id='diagnostic-timer'", null)) {
                timer = row.moveToFirst() && row.getString(0).equals("FINISHED") && row.getLong(1) == 0;
            }
            WorkInfo state = work == null ? null : WorkManager.getInstance(context)
                    .getWorkInfoById(work).get(1, TimeUnit.SECONDS);
            if (timer && (work == null || (state != null && state.getState() == WorkInfo.State.SUCCEEDED))) {
                assertNormalData(context);
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Normal start did not reconcile timer and deferred worker");
    }

    private static void assertNormalData(Context context) throws Exception {
        JSONObject before = new JSONObject(read(context, "diagnostic-before.json"));
        try (SQLiteDatabase db = open(context)) {
            check(db.getVersion() == 27, "Normal Room start did not reach schema27");
            JSONArray tables = before.getJSONArray("tables");
            for (int i = 0; i < tables.length(); i++) {
                JSONObject table = tables.getJSONObject(i);
                String name = table.getString("name");
                if (name.equals("room_master_table")) continue;
                JSONArray expected = table.getJSONArray("rows");
                try (Cursor exists = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                        new String[]{name})) {
                    if (!exists.moveToFirst()) {
                        check(expected.length() == 0, "Migration removed populated table " + name);
                        continue;
                    }
                }
                JSONArray columns = table.getJSONArray("columns");
                List<String> selected = new ArrayList<>();
                for (int j = 0; j < columns.length(); j++) selected.add(quote(columns.getString(j)));
                // An empty historical table may have structurally removed columns after migration.
                if (expected.length() == 0) {
                    try (Cursor count = db.rawQuery("SELECT COUNT(*) FROM " + quote(name), null)) {
                        count.moveToFirst(); check(count.getLong(0) == 0, "Normal start added rows to " + name);
                    }
                    continue;
                }
                if (name.equals("timer_sessions")) {
                    JSONArray row = new JSONArray(expected.getString(0));
                    for (int j = 0; j < columns.length(); j++) {
                        if (columns.getString(j).equals("state")) row.put(j, new JSONArray().put(3).put("FINISHED"));
                        if (columns.getString(j).equals("remainingMillis")) row.put(j, new JSONArray().put(1).put(0L));
                    }
                    expected = new JSONArray().put(row.toString());
                }
                try (Cursor rows = db.rawQuery("SELECT " + String.join(",", selected) + " FROM " + quote(name), null)) {
                    String actual = rows(rows).toString();
                    check(expected.toString().equals(actual), "Normal start changed retained " + name
                            + ": expected=" + expected + ", actual=" + actual);
                }
            }
        }
    }

    private static JSONObject snapshot(Context context) throws Exception {
        try (SQLiteDatabase db = open(context)) {
            JSONObject result = new JSONObject().put("version", db.getVersion());
            try (Cursor definitions = db.rawQuery("SELECT type,name,tbl_name,sql FROM sqlite_master "
                    + "WHERE sql IS NOT NULL ORDER BY type,name", null)) {
                result.put("schema", rows(definitions));
            }
            JSONArray tables = new JSONArray();
            try (Cursor names = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name", null)) {
                while (names.moveToNext()) {
                    String name = names.getString(0);
                    try (Cursor data = db.rawQuery("SELECT * FROM " + quote(name), null)) {
                        tables.put(new JSONObject().put("name", name)
                                .put("columns", new JSONArray(data.getColumnNames())).put("rows", rows(data)));
                    }
                }
            }
            return result.put("tables", tables);
        }
    }

    private static JSONArray rows(Cursor cursor) throws Exception {
        List<String> result = new ArrayList<>();
        while (cursor.moveToNext()) {
            JSONArray row = new JSONArray();
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                int type = cursor.getType(i);
                Object value = switch (type) {
                    case Cursor.FIELD_TYPE_NULL -> JSONObject.NULL;
                    case Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i);
                    case Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i);
                    case Cursor.FIELD_TYPE_BLOB -> Base64.encodeToString(cursor.getBlob(i), Base64.NO_WRAP);
                    default -> cursor.getString(i);
                };
                row.put(new JSONArray().put(type).put(value));
            }
            result.add(row.toString());
        }
        Collections.sort(result);
        return new JSONArray(result);
    }

    private static SQLiteDatabase open(Context context) {
        return SQLiteDatabase.openDatabase(context.getDatabasePath("auto_secretary.db").getPath(),
                null, SQLiteDatabase.OPEN_READONLY);
    }
    private static String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }
    private static String asset(Context context, String name) throws Exception {
        try (java.io.InputStream input = context.getAssets().open(name)) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1;) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    static String read(Context context, String name) throws Exception {
        return new String(Files.readAllBytes(new File(context.getFilesDir(), name).toPath()), StandardCharsets.UTF_8);
    }
    private static void write(Context context, String name, String value) throws Exception {
        Files.write(new File(context.getFilesDir(), name).toPath(), value.getBytes(StandardCharsets.UTF_8));
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
