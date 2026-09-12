package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import de.thonktank.autosecretary.domain.model.MissedOccurrenceMode;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Fast schema and risk-lane contracts for the signed production upgrade fixture corpus. */
@RunWith(RobolectricTestRunner.class)
public final class ProductionUpgradeFixtureContractTest {
    private static final String MANIFEST = "release/upgrade-fixtures/corpus.json";

    @Test public void corpusContainsOnlyTheFourApprovedSourceHistories() throws Exception {
        Map<String, JSONObject> fixtures = fixtures();
        assertEquals(Set.of("schema-8-floor", "schema-20-organic-flow",
                "schema-22-clean-candidate", "schema-23-repair-boundary"), fixtures.keySet());
        assertSource(fixtures.get("schema-8-floor"), "forest-android-1008001",
                "0.2.80", 1_008_001, 8,
                "825b0433bfeb5a34462af147b060f1bcccbc3227");
        assertSource(fixtures.get("schema-20-organic-flow"), "forest-android-1013701",
                "0.2.137", 1_013_701, 20,
                "e566a078d4edf8bda053c0257c6d9861db647ea8");
        assertSource(fixtures.get("schema-22-clean-candidate"), "forest-android-1015701",
                "0.2.157", 1_015_701, 22,
                "324205e914a73aea0a406b718509e1508afb6165");
        assertSource(fixtures.get("schema-23-repair-boundary"), "forest-android-1015801",
                "0.2.158", 1_015_801, 23,
                "a25add10fc833f362d01ee16781821c4b4df8d56");

        Properties release = new Properties();
        try (InputStream input = Files.newInputStream(
                repositoryFile("release/release.properties").toPath())) {
            release.load(input);
        }
        assertEquals(fixtures.get("schema-8-floor").getJSONObject("source").getString("tag"),
                release.getProperty("supportedUpgradeTag"));
        assertEquals(DatabaseContract.PRODUCTION_UPGRADE_SOURCE_VERSION,
                fixtures.get("schema-8-floor").getJSONObject("source")
                        .getInt("databaseVersion"));
    }

    @Test public void riskMatrixHasThreeFloorLanesAndThreeTargetedApiTwentySixLanes()
            throws Exception {
        Map<String, JSONObject> fixtures = fixtures();
        assertEquals(Set.of("26:default:stable", "35:google_apis:stable",
                        "37.0:google_apis:canary"),
                lanes(fixtures.get("schema-8-floor")));
        assertEquals(Set.of("26:default:stable"),
                lanes(fixtures.get("schema-20-organic-flow")));
        assertEquals(Set.of("26:default:stable"),
                lanes(fixtures.get("schema-22-clean-candidate")));
        assertEquals(Set.of("26:default:stable"),
                lanes(fixtures.get("schema-23-repair-boundary")));
    }

    @Test public void everySeedRowCoversEveryExportedSourceColumn() throws Exception {
        for (JSONObject fixture : fixtures().values()) {
            int version = fixture.getJSONObject("source").getInt("databaseVersion");
            Map<String, Set<String>> schema = columns(version);
            JSONArray seed = fixture.getJSONArray("seed");
            for (int entryIndex = 0; entryIndex < seed.length(); entryIndex++) {
                JSONObject entry = seed.getJSONObject(entryIndex);
                String table = entry.getString("table");
                assertNotNull("Fixture table is absent from schema " + version,
                        schema.get(table));
                JSONArray rows = entry.getJSONArray("rows");
                assertTrue("Seed entry has no rows", rows.length() > 0);
                for (int rowIndex = 0; rowIndex < rows.length(); rowIndex++) {
                    assertEquals("Fixture must explicitly declare every source column in "
                                    + fixture.getString("id") + ":" + table,
                            schema.get(table), keys(rows.getJSONObject(rowIndex)));
                }
            }
        }
    }

    @Test public void everyExpectedValueAndSelectorExistsInTargetSchema() throws Exception {
        for (JSONObject fixture : fixtures().values()) {
            assertEquals(DatabaseContract.VERSION, fixture.getInt("targetDatabaseVersion"));
            Map<String, Set<String>> schema = columns(fixture.getInt("targetDatabaseVersion"));
            JSONArray expected = fixture.getJSONArray("expectedTarget");
            assertTrue("Fixture has no target expectations", expected.length() > 0);
            for (int index = 0; index < expected.length(); index++) {
                JSONObject row = expected.getJSONObject(index);
                String table = row.getString("table");
                assertNotNull("Expected target table is absent", schema.get(table));
                Set<String> declared = keys(row.getJSONObject("where"));
                boolean absent = row.has("absent");
                assertEquals(absent ? Set.of("table", "where", "absent") : Set.of("table", "where", "values"), keys(row));
                assertFalse("Selector must be bounded", declared.isEmpty());
                if (absent) assertEquals(Boolean.TRUE, row.get("absent"));
                else {
                    assertTrue(row.getJSONObject("values").length() > 0);
                    declared.addAll(keys(row.getJSONObject("values")));
                }
                assertTrue("Expected target columns are absent from " + table,
                        schema.get(table).containsAll(declared));
            }
        }
    }

    @Test @org.robolectric.annotation.Config(sdk = {26, 35})
    public void everyProductionFixtureActuallyUpgradesThroughRegisteredRoomMigrations() throws Exception {
        android.content.Context context = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        for (JSONObject fixture : fixtures().values()) {
            String name = "upgrade-corpus-" + java.util.UUID.randomUUID();
            int source = fixture.getJSONObject("source").getInt("databaseVersion");
            androidx.sqlite.db.SupportSQLiteOpenHelper original = new androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
                    .create(androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                            .callback(new androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(source) {
                                public void onCreate(androidx.sqlite.db.SupportSQLiteDatabase db) {
                                    de.thonktank.autosecretary.testing.ExportedRoomSchemaFixture.create(db, source);
                                }
                                public void onUpgrade(androidx.sqlite.db.SupportSQLiteDatabase db, int old, int next) { }
                            }).build());
            try {
                androidx.sqlite.db.SupportSQLiteDatabase old = original.getWritableDatabase();
                JSONArray seed = fixture.getJSONArray("seed");
                for (int i = 0; i < seed.length(); i++) {
                    JSONObject entry = seed.getJSONObject(i);
                    JSONArray rows = entry.getJSONArray("rows");
                    for (int r = 0; r < rows.length(); r++) {
                        JSONObject row = rows.getJSONObject(r);
                        List<String> fields = new ArrayList<>(keys(row));
                        List<Object> values = new ArrayList<>();
                        for (String field : fields) values.add(row.isNull(field) ? null : row.get(field));
                        old.execSQL("INSERT OR " + entry.getString("conflict") + " INTO " + entry.getString("table")
                                + "(" + String.join(",", fields) + ") VALUES ("
                                + String.join(",", java.util.Collections.nCopies(fields.size(), "?")) + ")", values.toArray());
                    }
                }
                original.close();
                AppDatabase upgraded = androidx.room.Room.databaseBuilder(context, AppDatabase.class, name)
                        .addMigrations(de.thonktank.autosecretary.data.local.DatabaseMigrations.from(source))
                        .allowMainThreadQueries().build();
                try {
                    androidx.sqlite.db.SupportSQLiteDatabase db = upgraded.getOpenHelper().getWritableDatabase();
                    JSONArray expected = fixture.getJSONArray("expectedTarget");
                    for (int i = 0; i < expected.length(); i++) {
                        JSONObject entry = expected.getJSONObject(i), where = entry.getJSONObject("where");
                        List<String> selectors = new ArrayList<>(keys(where));
                        List<Object> arguments = new ArrayList<>();
                        List<String> clauses = new ArrayList<>();
                        for (String selector : selectors) {
                            if (where.isNull(selector)) clauses.add(selector + " IS NULL");
                            else { clauses.add(selector + "=?"); arguments.add(where.get(selector)); }
                        }
                        try (android.database.Cursor row = db.query("SELECT * FROM " + entry.getString("table")
                                + " WHERE " + String.join(" AND ", clauses), arguments.toArray())) {
                            if (entry.has("absent")) {
                                assertEquals(Boolean.TRUE, entry.get("absent"));
                                assertEquals(fixture.getString("id") + ": forbidden " + entry, 0, row.getCount());
                                continue;
                            }
                            JSONObject values = entry.getJSONObject("values");
                            assertTrue(fixture.getString("id") + ": " + entry, row.moveToFirst());
                            for (String key : keys(values)) assertEquals(fixture.getString("id") + ": " + key,
                                    values.isNull(key) ? null : values.get(key).toString(), row.getString(row.getColumnIndexOrThrow(key)));
                            assertFalse(row.moveToNext());
                        }
                    }
                    upgraded.runInTransaction(() -> {
                        var runs = new de.thonktank.autosecretary.data.local.SqlFlowGraphRunRepository(() -> db).active();
                        if (source == 8) assertTrue(runs.isEmpty());
                        else {
                            assertEquals(1, runs.size());
                            assertEquals(Long.valueOf(32503680000000L), runs.get(0).run.nextReadyAt());
                            assertEquals(3, runs.get(0).run.steps.size());
                        }
                    });
                } finally { upgraded.close(); }
            } finally { original.close(); context.deleteDatabase(name); }
        }
    }

    @Test public void schemaTwentyCoversCrashAndSilentPermutationValues() throws Exception {
        JSONObject fixture = fixtures().get("schema-20-organic-flow");
        List<JSONObject> rows = expectedRows(fixture, "flow_run_steps");
        assertEquals(3, rows.size());
        JSONObject empty = expectedById(rows, "upgrade-organic-empty-delay");
        assertTrue(empty.getJSONObject("values").isNull("lastUsedDelayMillis"));
        assertEquals("Leere letzte Verzögerung",
                empty.getJSONObject("values").getString("note"));
        assertEquals(2, empty.getJSONObject("values").getInt("targetRir"));
        JSONObject full = expectedById(rows, "upgrade-organic-full-delay");
        assertEquals(9000, full.getJSONObject("values").getInt("lastUsedDelayMillis"));
        assertEquals(10000, full.getJSONObject("values").getInt("chosenDelayMillis"));
        assertEquals(2, full.getJSONObject("values").getInt("targetRir"));
    }

    @Test public void seededTaskModesAreValidCurrentDomainValues() throws Exception {
        for (JSONObject fixture : fixtures().values()) {
            JSONArray seed = fixture.getJSONArray("seed");
            for (int entryIndex = 0; entryIndex < seed.length(); entryIndex++) {
                JSONObject entry = seed.getJSONObject(entryIndex);
                if (!"tasks".equals(entry.getString("table"))) continue;
                JSONArray rows = entry.getJSONArray("rows");
                for (int rowIndex = 0; rowIndex < rows.length(); rowIndex++) {
                    JSONObject row = rows.getJSONObject(rowIndex);
                    if (row.has("missedOccurrenceMode")) {
                        assertNotNull(MissedOccurrenceMode.valueOf(
                                row.getString("missedOccurrenceMode")));
                    }
                }
            }
        }
    }

    @Test public void schemaTwentyThreeSeparatesRepairFromByteEqualCorrectRow() throws Exception {
        JSONObject fixture = fixtures().get("schema-23-repair-boundary");
        List<JSONObject> rows = expectedRows(fixture, "flow_run_steps");
        JSONObject repaired = expectedById(rows, "upgrade-repair-permuted")
                .getJSONObject("values");
        assertEquals("EXTERNAL", repaired.getString("plannedLoadMode"));
        assertEquals("KG", repaired.getString("plannedLoadUnit"));
        assertEquals("Korrupte Notiz", repaired.getString("note"));
        assertEquals("FIXED", repaired.getString("delayMode"));

        JSONObject seededCorrect = seedRow(fixture, "flow_run_steps",
                "upgrade-repair-correct");
        JSONObject expectedCorrect = expectedById(rows, "upgrade-repair-correct");
        JSONObject combinedExpected = new JSONObject(expectedCorrect.getJSONObject("values")
                .toString());
        combinedExpected.put("id", expectedCorrect.getJSONObject("where").getString("id"));
        assertEquals(keys(seededCorrect), keys(combinedExpected));
        for (Iterator<String> names = seededCorrect.keys(); names.hasNext();) {
            String name = names.next();
            assertEquals("Correct schema-23 value changed for " + name,
                    seededCorrect.get(name), combinedExpected.get(name));
        }
    }

    private static void assertSource(JSONObject fixture, String tag, String versionName,
                                     int versionCode, int databaseVersion, String commit)
            throws Exception {
        assertNotNull(fixture);
        assertFalse(fixture.getString("risk").isBlank());
        JSONObject source = fixture.getJSONObject("source");
        assertEquals(tag, source.getString("tag"));
        assertEquals(versionName, source.getString("versionName"));
        assertEquals(versionCode, source.getInt("versionCode"));
        assertEquals(databaseVersion, source.getInt("databaseVersion"));
        assertEquals(commit, source.getString("commitSha"));
        assertEquals("de.thonktank.autosecretary", source.getString("packageName"));
        assertEquals("de45d94c9724beeaa2e0dff31f69f53bb0f4c9ba79a5aa419d1f29d18f4d91da",
                source.getString("signerSha256"));
        assertEquals(64, source.getString("apkSha256").length());
        assertEquals(64, source.getString("metadataSha256").length());
    }

    private static Set<String> lanes(JSONObject fixture) throws Exception {
        Set<String> result = new HashSet<>();
        JSONArray lanes = fixture.getJSONArray("apiLanes");
        for (int index = 0; index < lanes.length(); index++) {
            JSONObject lane = lanes.getJSONObject(index);
            assertFalse(lane.getString("risk").isBlank());
            assertEquals("x86_64", lane.getString("arch"));
            result.add(lane.get("apiLevel") + ":" + lane.getString("target") + ":"
                    + lane.getString("channel"));
        }
        return result;
    }

    private static List<JSONObject> expectedRows(JSONObject fixture, String table)
            throws Exception {
        List<JSONObject> result = new ArrayList<>();
        JSONArray expected = fixture.getJSONArray("expectedTarget");
        for (int index = 0; index < expected.length(); index++) {
            JSONObject row = expected.getJSONObject(index);
            if (table.equals(row.getString("table"))) result.add(row);
        }
        return result;
    }

    private static JSONObject expectedById(List<JSONObject> rows, String id) throws Exception {
        for (JSONObject row : rows) {
            if (id.equals(row.getJSONObject("where").optString("id"))) return row;
        }
        throw new AssertionError("Missing expected row " + id);
    }

    private static JSONObject seedRow(JSONObject fixture, String table, String id) throws Exception {
        JSONArray seed = fixture.getJSONArray("seed");
        for (int entryIndex = 0; entryIndex < seed.length(); entryIndex++) {
            JSONObject entry = seed.getJSONObject(entryIndex);
            if (!table.equals(entry.getString("table"))) continue;
            JSONArray rows = entry.getJSONArray("rows");
            for (int rowIndex = 0; rowIndex < rows.length(); rowIndex++) {
                JSONObject row = rows.getJSONObject(rowIndex);
                if (id.equals(row.optString("id"))) return row;
            }
        }
        throw new AssertionError("Missing seed row " + id);
    }

    private static Map<String, JSONObject> fixtures() throws Exception {
        JSONObject manifest = jsonFile(MANIFEST);
        assertEquals(1, manifest.getInt("contractVersion"));
        JSONArray entries = manifest.getJSONArray("fixtures");
        Map<String, JSONObject> result = new HashMap<>();
        Set<String> files = new HashSet<>();
        for (int index = 0; index < entries.length(); index++) {
            JSONObject entry = entries.getJSONObject(index);
            String id = entry.getString("id");
            String file = entry.getString("file");
            assertTrue("Duplicate fixture id " + id, result.put(id,
                    jsonFile("release/upgrade-fixtures/" + file)) == null);
            assertTrue("Duplicate fixture file " + file, files.add(file));
            assertEquals(id, result.get(id).getString("id"));
        }
        return result;
    }

    private static JSONObject jsonFile(String path) throws Exception {
        return new JSONObject(Files.readString(repositoryFile(path).toPath(),
                StandardCharsets.UTF_8));
    }

    private static Map<String, Set<String>> columns(int version) throws Exception {
        JSONObject database = jsonFile("app/schemas/" + AppDatabase.class.getName() + "/"
                + version + ".json").getJSONObject("database");
        assertEquals(version, database.getInt("version"));
        Map<String, Set<String>> result = new HashMap<>();
        JSONArray entities = database.getJSONArray("entities");
        for (int index = 0; index < entities.length(); index++) {
            JSONObject entity = entities.getJSONObject(index);
            Set<String> fields = new HashSet<>();
            JSONArray definitions = entity.getJSONArray("fields");
            for (int field = 0; field < definitions.length(); field++)
                fields.add(definitions.getJSONObject(field).getString("columnName"));
            result.put(entity.getString("tableName"), fields);
        }
        return result;
    }

    private static Set<String> keys(JSONObject object) {
        Set<String> result = new HashSet<>();
        object.keys().forEachRemaining(result::add);
        return result;
    }

    private static File repositoryFile(String path) {
        File direct = new File(path);
        if (direct.isFile()) return direct;
        File fromModule = new File("..", path);
        if (fromModule.isFile()) return fromModule;
        throw new IllegalStateException("Missing repository file " + path);
    }
}
