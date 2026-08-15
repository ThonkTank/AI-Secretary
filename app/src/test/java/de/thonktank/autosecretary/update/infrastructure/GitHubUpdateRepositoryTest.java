package de.thonktank.autosecretary.update.infrastructure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import de.thonktank.autosecretary.update.application.VerifiedUpdate;
import de.thonktank.autosecretary.update.domain.PackageEvidence;
import de.thonktank.autosecretary.update.domain.ReleaseMetadata;
import de.thonktank.autosecretary.update.domain.UpdateCheckResult;
import de.thonktank.autosecretary.update.domain.UpdateFailure;
import de.thonktank.autosecretary.update.domain.UpdateInfo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public final class GitHubUpdateRepositoryTest {
    private static final String PACKAGE = "de.thonktank.autosecretary";
    private static final String SIGNER = repeat('a', 64);
    private static final String COMMIT = repeat('b', 40);
    private static final String FEED = "https://api.github.com/repos/ThonkTank/AI-Secretary/releases?per_page=30";
    private static final String METADATA_URL = "https://github.com/metadata";
    private static final String APK_URL = "https://github.com/app";

    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void checkSelectsHighestCompatibleStableRelease() throws Exception {
        byte[] apk = "signed-apk".getBytes(StandardCharsets.UTF_8);
        FakeHttp http = feed(1_000_101L, apk, PACKAGE, SIGNER, COMMIT);
        GitHubUpdateRepository repository = repository(http,
                new FakePackages(2, 1_000_101L, PACKAGE, SIGNER));

        UpdateCheckResult result = repository.check();
        UpdateInfo update = result.availableUpdate();

        assertTrue(result.isAvailable());
        assertEquals(1_000_101L, update.versionCode);
        assertEquals("0.2.1", update.versionName);
        assertEquals(apk.length, update.sizeBytes);
    }

    @Test public void checkReturnsCurrentWithoutACompatibilityReleaseOrNewerVersion()
            throws Exception {
        FakeHttp empty = new FakeHttp();
        empty.responses.put(FEED, "[]".getBytes(StandardCharsets.UTF_8));
        assertFalse(repository(empty, new FakePackages(2, 2, PACKAGE, SIGNER))
                .check().isAvailable());

        byte[] apk = "apk".getBytes(StandardCharsets.UTF_8);
        FakeHttp current = feed(2, apk, PACKAGE, SIGNER, COMMIT);
        assertFalse(repository(current, new FakePackages(2, 2, PACKAGE, SIGNER))
                .check().isAvailable());
        assertFalse(current.requestedMetadata);
    }

    @Test public void checkReturnsTypedPackageSignerAndCommitFailures() throws Exception {
        byte[] apk = "apk".getBytes(StandardCharsets.UTF_8);
        assertRejected(feed(5, apk, "wrong.package", SIGNER, COMMIT),
                UpdateFailure.Kind.INCOMPATIBLE_RELEASE);
        assertRejected(feed(5, apk, PACKAGE, repeat('c', 64), COMMIT),
                UpdateFailure.Kind.SIGNATURE_MISMATCH);
        assertRejected(feed(5, apk, PACKAGE, SIGNER, repeat('d', 40)),
                UpdateFailure.Kind.INVALID_RELEASE);
    }

    @Test public void downloadVerifiesHashPackageVersionAndSignerBeforeFinalizing()
            throws Exception {
        byte[] apk = "verified-signed-apk".getBytes(StandardCharsets.UTF_8);
        FakeHttp http = feed(1_000_201L, apk, PACKAGE, SIGNER, COMMIT);
        GitHubUpdateRepository repository = repository(http,
                new FakePackages(2, 1_000_201L, PACKAGE, SIGNER));
        UpdateInfo info = repository.check().availableUpdate();

        VerifiedUpdate verified = repository.download(info, progress -> { });

        assertTrue(verified.apk.isFile());
        assertEquals("verified-signed-apk", new String(
                Files.readAllBytes(verified.apk.toPath()), StandardCharsets.UTF_8));
        assertFalse(new File(temporary.getRoot(), "update-1000201.partial").exists());
    }

    @Test public void failedDownloadIsRemovedAndTyped() throws Exception {
        byte[] expected = "expected".getBytes(StandardCharsets.UTF_8);
        FakeHttp http = feed(9, expected, PACKAGE, SIGNER, COMMIT);
        GitHubUpdateRepository repository = repository(http,
                new FakePackages(2, 9, PACKAGE, SIGNER));
        UpdateInfo info = repository.check().availableUpdate();
        http.download = "tampered".getBytes(StandardCharsets.UTF_8);

        try {
            repository.download(info, progress -> { });
            fail("tampered APK must be rejected");
        } catch (UpdateFailure error) {
            assertEquals(UpdateFailure.Kind.CHECKSUM_MISMATCH, error.kind());
            assertEquals(0, temporary.getRoot().listFiles().length);
        }
    }

    @Test public void metadataParserRejectsUnknownSchemaAndOversizedContracts()
            throws Exception {
        String valid = metadata(3, new byte[]{1}, PACKAGE, SIGNER, COMMIT);
        assertEquals(3, new ReleaseMetadataJsonParser().parse(valid).versionCode);
        assertMetadataRejected(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"));
        assertMetadataRejected(valid.replace("\"apkSizeBytes\":1",
                "\"apkSizeBytes\":10485761"));
        assertMetadataRejected(valid.substring(0, valid.length() - 1) + ",\"extra\":true}");
    }

    private void assertRejected(FakeHttp http, UpdateFailure.Kind expected) throws Exception {
        try {
            repository(http, new FakePackages(2, 5, PACKAGE, SIGNER)).check();
            fail("incompatible release must be rejected");
        } catch (UpdateFailure error) {
            assertEquals(expected, error.kind());
        }
    }

    private static void assertMetadataRejected(String value) throws Exception {
        try {
            new ReleaseMetadataJsonParser().parse(value);
            fail("invalid metadata must be rejected");
        } catch (UpdateFailure error) {
            assertEquals(UpdateFailure.Kind.INVALID_RELEASE, error.kind());
        }
    }

    private GitHubUpdateRepository repository(FakeHttp http, PackageEvidenceReader packages) {
        return new GitHubUpdateRepository(PACKAGE, "ThonkTank", "AI-Secretary",
                "release-metadata.json", "AutoSecretary.apk", "forest-android-",
                temporary.getRoot(), http, packages);
    }

    private static FakeHttp feed(long version, byte[] apk, String packageName,
                                 String signer, String metadataCommit) throws Exception {
        FakeHttp http = new FakeHttp();
        String feed = "[{\"tag_name\":\"forest-android-" + version
                + "\",\"draft\":false,\"prerelease\":false,\"target_commitish\":\""
                + COMMIT + "\",\"assets\":[{\"name\":\"release-metadata.json\","
                + "\"browser_download_url\":\"" + METADATA_URL + "\"},{\"name\":"
                + "\"AutoSecretary.apk\",\"browser_download_url\":\"" + APK_URL + "\"}]}]";
        http.responses.put(FEED, feed.getBytes(StandardCharsets.UTF_8));
        http.responses.put(METADATA_URL, metadata(version, apk, packageName, signer,
                metadataCommit).getBytes(StandardCharsets.UTF_8));
        http.download = apk;
        return http;
    }

    private static String metadata(long version, byte[] apk, String packageName,
                                   String signer, String commit) throws Exception {
        return "{\"schemaVersion\":1,\"versionCode\":" + version
                + ",\"versionName\":\"0.2.1\",\"packageName\":\"" + packageName
                + "\",\"apkAsset\":\"AutoSecretary.apk\",\"apkSizeBytes\":" + apk.length
                + ",\"sha256\":\"" + sha256(apk) + "\",\"signerSha256\":\""
                + signer + "\",\"commitSha\":\"" + commit + "\"}";
    }

    private static String sha256(byte[] value) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte item : MessageDigest.getInstance("SHA-256").digest(value))
            result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        return result.toString();
    }

    private static String repeat(char value, int count) {
        return String.join("", Collections.nCopies(count, String.valueOf(value)));
    }

    private static final class FakeHttp implements HttpTransport {
        final Map<String, byte[]> responses = new HashMap<>();
        byte[] download;
        boolean requestedMetadata;

        @Override public byte[] get(String url, int maxBytes) {
            if (METADATA_URL.equals(url)) requestedMetadata = true;
            byte[] value = responses.get(url);
            if (value == null) throw new AssertionError("Unexpected URL " + url);
            return value;
        }

        @Override public void download(String url, File destination, long expectedBytes,
                                       long maxBytes, IntConsumer progress) throws UpdateFailure {
            assertEquals(APK_URL, url);
            try {
                Files.write(destination.toPath(), download);
            } catch (IOException error) {
                throw new UpdateFailure(UpdateFailure.Kind.STORAGE,
                        "Could not write fake download", error);
            }
            progress.accept(99);
        }
    }

    private static final class FakePackages implements PackageEvidenceReader {
        private final PackageEvidence installed;
        private final PackageEvidence archive;

        FakePackages(long installedVersion, long archiveVersion, String archivePackage,
                     String signer) throws UpdateFailure {
            Set<String> signers = Collections.singleton(signer);
            installed = PackageEvidence.of(PACKAGE, installedVersion,
                    Collections.singleton(SIGNER));
            archive = PackageEvidence.of(archivePackage, archiveVersion, signers);
        }

        @Override public PackageEvidence installed() { return installed; }
        @Override public PackageEvidence archive(File apk) { return archive; }
    }
}
