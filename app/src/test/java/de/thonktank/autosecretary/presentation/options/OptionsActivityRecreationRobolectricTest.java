package de.thonktank.autosecretary.presentation.options;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.AlertDialog;

import androidx.lifecycle.ViewModelProvider;

import de.thonktank.autosecretary.AppContainer;
import de.thonktank.autosecretary.AutoSecretaryApplication;
import de.thonktank.autosecretary.MainActivity;
import de.thonktank.autosecretary.update.domain.UpdateFailure;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Method;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class OptionsActivityRecreationRobolectricTest {
    @Test public void pendingOptionsPromptSurvivesActivityRecreationUntilAcknowledged()
            throws Exception {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            AppContainer container = AutoSecretaryApplication.from(activity).container();
            OptionsViewModel options = new ViewModelProvider(activity,
                    new OptionsViewModel.Factory(container)).get(OptionsViewModel.class);
            Method enqueue = OptionsViewModel.class.getDeclaredMethod(
                    "enqueue", OptionsRequest.class);
            enqueue.setAccessible(true);
            enqueue.invoke(options, OptionsRequest.error("options:activity-recreation",
                    UpdateFailure.Kind.NETWORK, "Offener Updatefehler"));
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

            AlertDialog first = ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(first);
            assertNotNull(options.state().getValue().firstRequest());

            ShadowAlertDialog.reset();
            activity = controller.recreate().get();
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

            AlertDialog recreated = ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(recreated);
            assertFalse(first == recreated);
            recreated.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
            assertNull(options.state().getValue().firstRequest());
        }
    }
}
