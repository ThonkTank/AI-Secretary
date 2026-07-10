package com.autosecretary.app.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class SettingsDataServiceCharacterizationTest extends AutoSecretaryRobolectricTest {
    @Test
    public void restoreBackupKeepsCloseSidecarCopyOpenSequenceInvariant() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File databaseFile = context.getDatabasePath(AppDatabase.DB_NAME);
        File parent = databaseFile.getParentFile();
        if (parent != null) {
            assertTrue(parent.mkdirs() || parent.isDirectory());
        }
        try (FileOutputStream output = new FileOutputStream(databaseFile)) {
            output.write("old".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        createSidecar(databaseFile, "-wal");
        createSidecar(databaseFile, "-shm");
        File backup = File.createTempFile("autos-secretary-restore", ".db");
        try (FileOutputStream output = new FileOutputStream(backup)) {
            output.write("new".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        RecordingLifecycle lifecycle = new RecordingLifecycle();
        SettingsDataService service = new SettingsDataService(context, lifecycle);

        assertTrue(service.restoreBackup(backup));

        assertEquals(List.of("close", "open"), lifecycle.calls);
        assertEquals("new", readFile(databaseFile));
        assertEquals(false, new File(databaseFile.getAbsolutePath() + "-wal").exists());
        assertEquals(false, new File(databaseFile.getAbsolutePath() + "-shm").exists());
    }

    private static void createSidecar(File databaseFile, String suffix) throws Exception {
        try (FileOutputStream output = new FileOutputStream(databaseFile.getAbsolutePath() + suffix)) {
            output.write("sidecar".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static String readFile(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static final class RecordingLifecycle implements SettingsDataService.DatabaseLifecycle {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void runDatabaseCheckpoint() {
            calls.add("checkpoint");
        }

        @Override
        public void closeDatabaseForFileReplacement() {
            calls.add("close");
        }

        @Override
        public void openDatabaseAfterFileReplacement() {
            calls.add("open");
        }
    }
}
