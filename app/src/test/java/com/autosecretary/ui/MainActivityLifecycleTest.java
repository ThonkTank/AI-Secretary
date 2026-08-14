package com.autosecretary.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.Manifest;
import android.os.Looper;

import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.app.MainActivity;
import com.autosecretary.ui.editor.ObligationEditorDialogFragment;
import com.autosecretary.ui.editor.EditorViewModel;

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
        app.getSharedPreferences("waldmorgen_ui", AutoSecretaryApplication.MODE_PRIVATE)
                .edit().putBoolean("location_asked", true).commit();
        Shadows.shadowOf(app).denyPermissions(
                Manifest.permission.READ_CALENDAR, Manifest.permission.ACCESS_COARSE_LOCATION);

        var controller = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        MainViewModel initialViewModel = viewModel(activity);
        await(() -> initialViewModel.state().getValue() instanceof MainUiState.Ready);
        initialViewModel.selectSurface(Surface.ALL);
        initialViewModel.selectFilter(WorkItemFilter.ROUTINES);
        EditorViewModel initialEditor = editorViewModel(activity);
        initialEditor.open(false, null);
        var editor = initialEditor.editor();
        initialEditor.edit(editor.edit("Rohentwurf", "noch offen", "",
                "", true, "", "", List.of()));
        new ObligationEditorDialogFragment().show(
                activity.getSupportFragmentManager(), ObligationEditorDialogFragment.TAG);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        activity = controller.recreate().get();
        MainActivity recreated = activity;
        MainViewModel recreatedViewModel = viewModel(recreated);
        EditorViewModel recreatedEditor = editorViewModel(recreated);
        await(() -> recreatedViewModel.state().getValue() != null);

        assertEquals(Surface.ALL, recreatedViewModel.state().getValue().surface());
        assertEquals(WorkItemFilter.ROUTINES, recreatedViewModel.state().getValue().filter());
        assertEquals("Rohentwurf",
                recreatedEditor.editor().titleInput());
        assertEquals("noch offen",
                recreatedEditor.editor().durationInput());
        assertNotNull(recreated.getSupportFragmentManager().findFragmentByTag(
                ObligationEditorDialogFragment.TAG));
        controller.pause().stop().destroy();
        app.onTerminate();
    }

    private static MainViewModel viewModel(MainActivity activity) {
        return new ViewModelProvider(activity, activity.featureViewModelFactory())
                .get(MainViewModel.class);
    }

    private static EditorViewModel editorViewModel(MainActivity activity) {
        return new ViewModelProvider(activity, activity.featureViewModelFactory())
                .get(EditorViewModel.class);
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
