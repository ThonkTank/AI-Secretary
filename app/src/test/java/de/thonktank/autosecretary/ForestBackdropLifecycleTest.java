package de.thonktank.autosecretary;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.provider.Settings;
import android.widget.FrameLayout;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowValueAnimator;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class ForestBackdropLifecycleTest {
    @After public void restoreAnimations() {
        ShadowValueAnimator.reset();
    }

    @Test public void breathingOnlyRunsWhileViewIsAttached() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        activity.setContentView(root);
        ForestBackdropView forest = new ForestBackdropView(activity);

        assertFalse(forest.isBreathing());
        root.addView(forest);
        assertTrue(forest.isBreathing());
        root.removeView(forest);
        assertFalse(forest.isBreathing());
    }

    @Test public void disabledSystemAnimationsPreventBreathing() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Settings.Global.putFloat(activity.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        FrameLayout root = new FrameLayout(activity);
        activity.setContentView(root);
        ForestBackdropView forest = new ForestBackdropView(activity);

        root.addView(forest);

        assertFalse(forest.isBreathing());
    }
}
