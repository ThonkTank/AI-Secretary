package com.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.domain.CompletionStats;
import com.autosecretary.domain.Task;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

/** Two-phase state probe invoked around a real adb install -r by tools/verify-data-migration. */
@RunWith(AndroidJUnit4.class)
public final class DataMigrationDeviceTest {
    private static final String PHASE = "migrationPhase";
    private static final String PREFERENCES = "migration-n-to-n-plus-one";
    private static final String TASK_ID = "00000000-0000-0000-0000-000000000035";
    private static final byte[] MODEL = "preserved-model-fixture".getBytes(StandardCharsets.UTF_8);

    @Test public void seedOrVerifyPersistentApplicationState() throws Exception {
        Bundle arguments = InstrumentationRegistry.getArguments();
        String phase = arguments.getString(PHASE, "");
        Assume.assumeTrue("seed".equals(phase) || "verify".equals(phase));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AutoSecretaryApplication application = (AutoSecretaryApplication) context.getApplicationContext();
        if ("seed".equals(phase)) seed(context, application);
        else verify(context, application);
    }

    private static void seed(Context context, AutoSecretaryApplication application) throws Exception {
        int uid = context.getApplicationInfo().uid;
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putInt("uid", uid)
                .putInt("version", BuildConfig.VERSION_CODE)
                .putString("setting", "bleibt-erhalten")
                .commit();
        LocalDateTime created = LocalDateTime.of(2026, 8, 14, 8, 0);
        application.graph().workItems().save(new Task(
                TASK_ID, "Migrationsbeleg", 30, null, null, true, List.of(),
                created, false, CompletionStats.empty(), 0));
        application.graph().workItems().complete(TASK_ID, created.plusHours(1));
        assertNotNull(application.graph().workItems().latestUndoLabel());
        Files.write(modelFile(context).toPath(), MODEL);
        assertTrue(modelFile(context).isFile());
    }

    private static void verify(Context context, AutoSecretaryApplication application) throws Exception {
        var preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        int previousVersion = preferences.getInt("version", -1);
        assertEquals(previousVersion + 1, BuildConfig.VERSION_CODE);
        assertEquals(preferences.getInt("uid", -1), context.getApplicationInfo().uid);
        assertEquals("bleibt-erhalten", preferences.getString("setting", null));
        assertEquals("com.autosecretary", context.getPackageName());
        assertEquals(35, application.databaseVersion());
        assertNotNull(application.graph().workItems().find(TASK_ID));
        assertNotNull(application.graph().workItems().latestUndoLabel());
        assertTrue(java.util.Arrays.equals(MODEL, Files.readAllBytes(modelFile(context).toPath())));
    }

    private static File modelFile(Context context) {
        return new File(context.getFilesDir(), "autosecretary-model.task");
    }
}
