package com.autosecretary.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.Manifest;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.ui.editor.ObligationEditorDialogFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.function.BooleanSupplier;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = AutoSecretaryApplication.class)
public final class MainActivityLifecycleTest {
    @Test
    public void recreationKeepsNavigationFilterDialogAndRawEditorInput() {
        AutoSecretaryApplication app = ApplicationProvider.getApplicationContext();
        app.deleteDatabase("autosecretary.db");
        if (app.legacyImports().requiresUserDecision()) app.legacyImports().chooseEmptyDatabase();
        app.getSharedPreferences("waldmorgen_ui", AutoSecretaryApplication.MODE_PRIVATE)
                .edit().putBoolean("location_asked", true).commit();
        Shadows.shadowOf(app).denyPermissions(
                Manifest.permission.READ_CALENDAR, Manifest.permission.ACCESS_COARSE_LOCATION);

        var controller = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        MainActivity initial = activity;
        await(() -> initial.mainViewModel() != null
                && initial.mainViewModel().state().getValue() != null
                && initial.mainViewModel().state().getValue().dashboard() != null);
        activity.mainViewModel().selectSurface("all");
        activity.mainViewModel().selectFilter("routines");
        activity.mainViewModel().openEditor(false, null);
        var editor = activity.mainViewModel().state().getValue().editor();
        activity.mainViewModel().editEditor(editor.edit("Rohentwurf", "noch offen", "",
                "", true, "", "", List.of()));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        activity = controller.recreate().get();
        MainActivity recreated = activity;
        await(() -> recreated.mainViewModel() != null
                && recreated.mainViewModel().state().getValue() != null);

        assertEquals("all", recreated.mainViewModel().state().getValue().surface());
        assertEquals("routines", recreated.mainViewModel().state().getValue().filter());
        assertEquals("Rohentwurf",
                recreated.mainViewModel().state().getValue().editor().titleInput());
        assertEquals("noch offen",
                recreated.mainViewModel().state().getValue().editor().durationInput());
        assertNotNull(recreated.getSupportFragmentManager().findFragmentByTag(
                ObligationEditorDialogFragment.TAG));
        controller.pause().stop().destroy();
        app.onTerminate();
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (condition.getAsBoolean()) return;
            try { Thread.sleep(10); }
            catch (InterruptedException error) { throw new AssertionError(error); }
        }
        throw new AssertionError("Activity-Zustand wurde nicht rechtzeitig aufgebaut");
    }
}
