package com.autosecretary.platform.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class GitHubReleaseFeedTest {
    private static final String OWNER = "ThonkTank";
    private static final String REPOSITORY = "AI-Secretary";
    private static final String PACKAGE = "com.autosecretary";
    private static final String SIGNER = "a".repeat(64);
    private static final String COMMIT = "b".repeat(40);
    private static final String APK_URL = "https://github.com/ThonkTank/AI-Secretary/"
            + "releases/download/android-2001202/AutoSecretary.apk";
    private static final String METADATA_URL = "https://github.com/ThonkTank/AI-Secretary/"
            + "releases/download/android-2001202/release-metadata.json";

    @Test
    public void fullFeedReturnsTheCanonicalNewerSchemaOneRelease() throws Exception {
        FakeHttp http = feedResponses(metadata(1));
        GitHubReleaseFeed feed = feed(http);

        var update = feed.latest(2001201, PACKAGE, Set.of(SIGNER));

        assertEquals(2001202, update.versionCode());
        assertEquals(60_000_000, update.apkSizeBytes());
    }

    @Test
    public void equalLatestReleaseMeansCurrent() throws Exception {
        assertNull(feed(feedResponses(metadata(1)))
                .latest(2001202, PACKAGE, Set.of(SIGNER)));
    }

    @Test
    public void legacyMetadataWithoutSchemaRemainsReadable() throws Exception {
        JSONObject legacy = metadata(0);
        legacy.remove("schemaVersion");
        legacy.remove("apkSizeBytes");
        legacy.remove("commitSha");

        var update = feed(feedResponses(legacy))
                .latest(2001201, PACKAGE, Set.of(SIGNER));

        assertEquals(2001202, update.versionCode());
        assertEquals(0, update.apkSizeBytes());
    }

    @Test
    public void rejectsUnsupportedSchemaAndCommitMismatch() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> feed(feedResponses(metadata(2)))
                .latest(2001201, PACKAGE, Set.of(SIGNER)));
        JSONObject wrongCommit = release();
        wrongCommit.put("target_commitish", "c".repeat(40));
        FakeHttp http = new FakeHttp(Map.of(latestUrl(), wrongCommit.toString(),
                METADATA_URL, metadata(1).toString()));
        assertThrows(SecurityException.class, () -> feed(http)
                .latest(2001201, PACKAGE, Set.of(SIGNER)));
    }

    @Test
    public void structuredUrlPolicyRejectsLookalikeHostsAndPaths() {
        UrlTrustPolicy trust = new UrlTrustPolicy(OWNER, REPOSITORY);
        assertThrows(SecurityException.class, () -> trust.requireReleaseAsset(
                "https://github.com.evil.example/ThonkTank/AI-Secretary/releases/download/"
                        + "android-2001202/AutoSecretary.apk", "AutoSecretary.apk"));
        assertThrows(SecurityException.class, () -> trust.requireRedirect(
                "https://githubusercontent.com.evil.example/object"));
    }

    private static GitHubReleaseFeed feed(FakeHttp http) {
        UrlTrustPolicy trust = new UrlTrustPolicy(OWNER, REPOSITORY);
        return new GitHubReleaseFeed(OWNER, REPOSITORY, http, trust);
    }

    private static FakeHttp feedResponses(JSONObject metadata) throws Exception {
        Map<String, String> values = new HashMap<>();
        values.put(latestUrl(), release().toString());
        values.put(METADATA_URL, metadata.toString());
        return new FakeHttp(values);
    }

    private static String latestUrl() {
        return "https://api.github.com/repos/" + OWNER + "/" + REPOSITORY + "/releases/latest";
    }

    private static JSONObject release() throws Exception {
        return new JSONObject().put("tag_name", "android-2001202")
                .put("target_commitish", COMMIT).put("draft", false).put("prerelease", false)
                .put("assets", new JSONArray()
                        .put(asset("AutoSecretary.apk", APK_URL))
                        .put(asset("release-metadata.json", METADATA_URL)));
    }

    private static JSONObject metadata(int schema) throws Exception {
        return new JSONObject().put("schemaVersion", schema).put("versionCode", 2001202)
                .put("versionName", "2.1.1").put("packageName", PACKAGE)
                .put("apkAsset", "AutoSecretary.apk").put("apkSizeBytes", 60_000_000)
                .put("sha256", "d".repeat(64)).put("signerSha256", SIGNER)
                .put("commitSha", COMMIT);
    }

    private static JSONObject asset(String name, String url) throws Exception {
        return new JSONObject().put("name", name).put("browser_download_url", url);
    }

    private record FakeHttp(Map<String, String> values) implements HttpTransport {
        @Override public String get(String url, int byteLimit) {
            String result = values.get(url);
            if (result == null) throw new IllegalStateException("unexpected URL " + url);
            return result;
        }
    }
}
