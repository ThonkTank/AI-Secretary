package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Fast contract checks for the one supported rolling production upgrade fixture. */
@RunWith(RobolectricTestRunner.class)
public final class ProductionUpgradeFixtureContractTest {
    private static final String FIXTURE = "v0.2.80.json";

    @Test public void fixtureTargetsTheCentralDatabaseContract() throws Exception {
        JSONObject fixture = fixture();
        assertFixtureVersions(fixture);
        JSONObject source = fixture.getJSONObject("source");
        assertEquals("0.2.80", source.getString("versionName"));
        assertEquals(1_008_001, source.getInt("versionCode"));

        Properties release = new Properties();
        try (InputStream input = Files.newInputStream(repositoryFile("release/release.properties").toPath())) {
            release.load(input);
        }
        assertEquals("forest-android-" + source.getInt("versionCode"),
                release.getProperty("supportedUpgradeTag"));
    }

    @Test public void everySeedValueMatchesTheExportedSourceSchemaAndCoversEveryColumn()
            throws Exception {
        assertSeedCoverage(fixture());
    }

    @Test(expected = AssertionError.class)
    public void wrongTargetSchemaFailsTheFastContract() throws Exception {
        JSONObject broken = fixture();
        broken.put("targetDatabaseVersion", DatabaseContract.VERSION + 1);
        assertFixtureVersions(broken);
    }

    @Test(expected = AssertionError.class)
    public void missingFixtureColumnFailsTheFastContract() throws Exception {
        JSONObject broken = fixture();
        broken.getJSONObject("tables").getJSONObject("tasks").remove("title");
        assertSeedCoverage(broken);
    }

    private static void assertFixtureVersions(JSONObject fixture) throws Exception {
        assertEquals(DatabaseContract.PRODUCTION_UPGRADE_SOURCE_VERSION,
                fixture.getJSONObject("source").getInt("databaseVersion"));
        assertEquals(DatabaseContract.VERSION, fixture.getInt("targetDatabaseVersion"));
    }

    private static void assertSeedCoverage(JSONObject fixture) throws Exception {
        int version = fixture.getJSONObject("source").getInt("databaseVersion");
        Map<String, Set<String>> schema = columns(version);
        JSONObject tables = fixture.getJSONObject("tables");
        for (Iterator<String> names = tables.keys(); names.hasNext();) {
            String table = names.next();
            assertNotNull("Fixture table is absent from schema " + version, schema.get(table));
            assertEquals("Fixture must explicitly declare every source column in " + table,
                    schema.get(table), keys(tables.getJSONObject(table)));
        }
    }

    @Test public void everyExpectedTargetValueExistsInTheExportedTargetSchema() throws Exception {
        JSONObject fixture = fixture();
        Map<String, Set<String>> schema = columns(fixture.getInt("targetDatabaseVersion"));
        JSONObject expected = fixture.getJSONObject("expectedTarget");
        for (Iterator<String> names = expected.keys(); names.hasNext();) {
            String table = names.next();
            assertNotNull("Expected target table is absent", schema.get(table));
            assertTrue("Expected target columns are absent from " + table,
                    schema.get(table).containsAll(keys(expected.getJSONObject(table))));
        }
    }

    private static JSONObject fixture() throws Exception {
        try (InputStream input = ProductionUpgradeFixtureContractTest.class.getClassLoader()
                .getResourceAsStream(FIXTURE)) {
            assertNotNull("Missing production upgrade fixture " + FIXTURE, input);
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static Map<String, Set<String>> columns(int version) throws Exception {
        JSONObject database = new JSONObject(new String(Files.readAllBytes(repositoryFile(
                "app/schemas/" + AppDatabase.class.getName() + "/" + version + ".json").toPath()),
                StandardCharsets.UTF_8))
                .getJSONObject("database");
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
