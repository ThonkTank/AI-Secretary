package com.autosecretary.platform.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.application.model.ModelDownloadProgress;
import com.autosecretary.application.model.ModelStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public final class LocalModelManagerTest {
    private Application context;

    @Before public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("validated_local_model", 0).edit().clear().commit();
        deleteKnownFiles();
    }

    @After public void tearDown() throws Exception { deleteKnownFiles(); }

    @Test public void adoptsAValidLegacyFileWithoutStartingANetworkDownload() throws Exception {
        byte[] bytes = "legacy-valid-model".getBytes(StandardCharsets.UTF_8);
        FakeDownloads downloads = new FakeDownloads(bytes);
        AtomicInteger validations = new AtomicInteger();
        LocalModelManager manager = manager(bytes, candidate -> validations.incrementAndGet(),
                downloads);
        Files.write(manager.file().toPath(), bytes);

        var ticket = manager.enqueue();
        manager.verifyAndActivate(ticket);

        assertEquals(0, ticket.id());
        assertEquals(0, downloads.enqueues);
        assertEquals(1, validations.get());
        assertTrue(manager.hasModel());
    }

    @Test public void persistedDownloadIsResumedAfterRepositoryRecreation() throws Exception {
        byte[] bytes = "resumable-model".getBytes(StandardCharsets.UTF_8);
        FakeDownloads downloads = new FakeDownloads(bytes);
        downloads.progress = new ModelDownloadProgress.Running(4, bytes.length);
        LocalModelManager first = manager(bytes, candidate -> { }, downloads);

        var ticket = first.enqueue();
        LocalModelManager restored = manager(bytes, candidate -> { }, downloads);

        assertTrue(restored.status() instanceof ModelStatus.Downloading);
        assertEquals(ticket, ((ModelStatus.Downloading) restored.status()).ticket());
        assertEquals(1, downloads.enqueues);
        downloads.progress = new ModelDownloadProgress.Complete();
        restored.verifyAndActivate(ticket);
        assertTrue(restored.hasModel());
    }

    @Test public void hashMismatchNeverActivatesAndRemovesThePartialDownload() throws Exception {
        byte[] expected = "expected-model".getBytes(StandardCharsets.UTF_8);
        byte[] corrupted = "corruptd-model".getBytes(StandardCharsets.UTF_8);
        FakeDownloads downloads = new FakeDownloads(corrupted);
        LocalModelManager manager = manager(expected, candidate -> { }, downloads);
        var ticket = manager.enqueue();

        assertThrows(SecurityException.class, () -> manager.verifyAndActivate(ticket));

        assertFalse(manager.file().exists());
        assertFalse(downloads.destination.exists());
        assertEquals(1, downloads.removals);
    }

    @Test public void failedInferenceLeavesThePreviousWorkingModelUntouched() throws Exception {
        byte[] previous = "previous-working-model".getBytes(StandardCharsets.UTF_8);
        byte[] replacement = "new-pinned-model".getBytes(StandardCharsets.UTF_8);
        FakeDownloads downloads = new FakeDownloads(replacement);
        LocalModelManager manager = manager(replacement, candidate -> {
            throw new IllegalArgumentException("inference failed");
        }, downloads);
        Files.write(manager.file().toPath(), previous);
        var ticket = manager.enqueue();

        assertThrows(IllegalArgumentException.class,
                () -> manager.verifyAndActivate(ticket));

        assertArrayEquals(previous, Files.readAllBytes(manager.file().toPath()));
        assertFalse(new File(context.getFilesDir(), "autosecretary-model.task.partial").exists());
    }

    @Test public void successfulActivationAtomicallyReplacesAndCleansObsoleteFiles()
            throws Exception {
        byte[] replacement = "new-valid-model".getBytes(StandardCharsets.UTF_8);
        FakeDownloads downloads = new FakeDownloads(replacement);
        LocalModelManager manager = manager(replacement, candidate -> { }, downloads);
        Files.write(manager.file().toPath(), "old".getBytes(StandardCharsets.UTF_8));
        File obsolete = new File(context.getFilesDir(), "obsolete.task");
        File stale = new File(context.getFilesDir(), "stale.partial");
        Files.write(obsolete.toPath(), new byte[] {1});
        Files.write(stale.toPath(), new byte[] {2});

        manager.verifyAndActivate(manager.enqueue());

        assertArrayEquals(replacement, Files.readAllBytes(manager.file().toPath()));
        assertTrue(manager.hasModel());
        assertFalse(obsolete.exists());
        assertFalse(stale.exists());
    }

    @Test public void cancellationRemovesDownloadAndPrivatePartialFile() throws Exception {
        byte[] bytes = "cancel-model".getBytes(StandardCharsets.UTF_8);
        FakeDownloads downloads = new FakeDownloads(bytes);
        downloads.progress = new ModelDownloadProgress.Running(1, bytes.length);
        LocalModelManager manager = manager(bytes, candidate -> { }, downloads);
        var ticket = manager.enqueue();
        File partial = new File(context.getFilesDir(), "autosecretary-model.task.partial");
        Files.write(partial.toPath(), new byte[] {1});

        manager.cancel(ticket);

        assertFalse(partial.exists());
        assertFalse(downloads.destination.exists());
        assertTrue(manager.status() instanceof ModelStatus.Missing);
    }

    private LocalModelManager manager(
            byte[] expected,
            LocalModelManager.ModelValidator validator,
            FakeDownloads downloads) throws Exception {
        return new LocalModelManager(context, new LocalModelManager.Manifest(
                1, "test-model", "revision-1",
                "https://example.invalid/revision-1/model.task",
                "autosecretary-model.task", expected.length, sha256(expected)),
                validator, downloads);
    }

    private void deleteKnownFiles() throws Exception {
        File[] privateFiles = context.getFilesDir().listFiles((ignored, name) ->
                name.endsWith(".task") || name.endsWith(".partial"));
        if (privateFiles != null) for (File file : privateFiles) Files.deleteIfExists(file.toPath());
        File external = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
        File modelDirectory = external == null ? null : new File(external, "models");
        File[] downloads = modelDirectory == null ? null : modelDirectory.listFiles();
        if (downloads != null) for (File file : downloads) Files.deleteIfExists(file.toPath());
    }

    private static String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder result = new StringBuilder();
        for (byte item : digest) result.append(String.format(Locale.ROOT, "%02x", item));
        return result.toString();
    }

    private static final class FakeDownloads implements LocalModelManager.DownloadAccess {
        final byte[] bytes;
        ModelDownloadProgress progress = new ModelDownloadProgress.Complete();
        File destination;
        int enqueues;
        int removals;

        FakeDownloads(byte[] bytes) { this.bytes = bytes; }

        @Override public long enqueue(String url, File destination) {
            try { Files.write(destination.toPath(), bytes); }
            catch (Exception error) { throw new AssertionError(error); }
            this.destination = destination;
            enqueues++;
            return 17;
        }

        @Override public ModelDownloadProgress query(long id) { return progress; }

        @Override public void remove(long id) {
            removals++;
            if (destination != null) {
                try { Files.deleteIfExists(destination.toPath()); }
                catch (Exception error) { throw new AssertionError(error); }
            }
        }
    }
}
