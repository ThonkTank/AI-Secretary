package com.autosecretary.platform.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class ReleaseMetadataCompatibilityTest {
    private static final String APK = "https://github.com/ThonkTank/AI-Secretary/"
            + "releases/download/android-2001202/AutoSecretary.apk";
    private final UrlTrustPolicy trust = new UrlTrustPolicy("ThonkTank", "AI-Secretary");
    private final ReleaseMetadataParser parser = new ReleaseMetadataParser();

    @Test public void newConsumerReadsLegacyAndSchemaOneFixtures() throws Exception {
        assertEquals(0, parser.parse(fixture("legacy-release-metadata.json"), APK, trust)
                .schemaVersion());
        assertEquals(1, parser.parse(fixture("schema-1-release-metadata.json"), APK, trust)
                .schemaVersion());
    }

    @Test public void legacyConsumerStillReadsSchemaOneTopLevelFields() throws Exception {
        JSONObject value = new JSONObject(fixture("schema-1-release-metadata.json"));
        // This intentionally models the old consumer: it ignores every unknown field.
        assertEquals(2001202, value.getInt("versionCode"));
        assertEquals("com.autosecretary", value.getString("packageName"));
        assertEquals("AutoSecretary.apk", value.getString("apkAsset"));
    }

    @Test public void unknownSchemaIsExplicitlyRejected() throws Exception {
        JSONObject value = new JSONObject(fixture("schema-1-release-metadata.json"))
                .put("schemaVersion", 2);
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(value.toString(), APK, trust));
    }

    private static String fixture(String name) throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        if (!Files.isDirectory(root.resolve("release"))) root = root.getParent();
        return new String(Files.readAllBytes(root.resolve("release").resolve("fixtures")
                .resolve(name)), java.nio.charset.StandardCharsets.UTF_8);
    }
}
