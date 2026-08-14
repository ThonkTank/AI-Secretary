package com.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.data.FocusDatabase;
import com.autosecretary.app.MainActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/** A real emulator smoke test; building the test APK alone cannot satisfy this check. */
@RunWith(AndroidJUnit4.class)
public final class AppDeviceSmokeTest {
    @Test public void activityStartsAndStableDataBoundariesRemainAccessible() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.autosecretary", target.getPackageName());
        assertEquals(35, FocusDatabase.VERSION);
        target.getSharedPreferences("migration-smoke", Context.MODE_PRIVATE)
                .edit().putString("preserved", "yes").commit();
        File legacyModel = new File(target.getFilesDir(), "autosecretary-model.task");
        if (!legacyModel.exists()) assertTrue(legacyModel.createNewFile());

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity.findViewById(
                        com.autosecretary.presentation.R.id.FeatureHost));
                assertEquals(35, ((AutoSecretaryApplication) activity.getApplication())
                        .databaseVersion());
            });
        }

        assertEquals("yes", target.getSharedPreferences(
                "migration-smoke", Context.MODE_PRIVATE).getString("preserved", null));
        assertTrue(legacyModel.exists());
    }
}
