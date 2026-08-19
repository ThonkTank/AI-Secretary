package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class RepStepperViewTest {
    @Test public void controlsRespectBoundsAndRepeatEveryThreeHundredMilliseconds() {
        Context context = ApplicationProvider.getApplicationContext();
        RepStepperView stepper = new RepStepperView(context);
        AtomicInteger changes = new AtomicInteger();
        stepper.bind(0, DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT),
                changes::addAndGet);
        View minus = stepper.findViewById(R.id.rep_stepper_decrement);
        View plus = stepper.findViewById(R.id.rep_stepper_increment);

        assertFalse(minus.isEnabled());
        assertTrue(plus.isEnabled());
        plus.dispatchTouchEvent(MotionEvent.obtain(0, 0,
                MotionEvent.ACTION_DOWN, 10, 10, 0));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(950));
        plus.dispatchTouchEvent(MotionEvent.obtain(0, 951,
                MotionEvent.ACTION_UP, 10, 10, 0));

        assertEquals(3, changes.get());
        stepper.bind(999, DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT),
                changes::addAndGet);
        assertFalse(plus.isEnabled());
    }

    @Test public void detachCancelsPendingRepeatGesture() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        RepStepperView stepper = new RepStepperView(activity);
        AtomicInteger changes = new AtomicInteger();
        stepper.bind(12, DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT),
                changes::addAndGet);
        root.addView(stepper);
        activity.setContentView(root);
        View plus = stepper.findViewById(R.id.rep_stepper_increment);

        plus.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1, 1, 0));
        root.removeView(stepper);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));

        assertEquals(0, changes.get());
        assertFalse(plus.isPressed());
    }
}
