package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.app.Activity;
import android.view.View;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

import java.time.LocalTime;
import java.util.Collections;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class SetBarsViewTest {
    @Test public void savedAndOpenSetsStayOnOneScrollableLine() {
        Context context = ApplicationProvider.getApplicationContext();
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT);
        SetBarsView bars = new SetBarsView(context);
        bars.bind("sets", 20, Collections.emptyList(), -1, palette, ignored -> { });
        bars.bind("sets", 20, Collections.singletonList(12), 0, palette, ignored -> { });
        bars.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        assertEquals(dp(context, 20 * 30 - 8), bars.getMeasuredWidth());
        assertEquals(dp(context, 44), bars.getMeasuredHeight());
        assertTrue(bars.getContentDescription().toString().contains("Satz 1: 12"));
        assertTrue(bars.getContentDescription().toString().contains("Satz 20 offen"));
    }

    @Test public void everySavedSetIsAnIndependentAccessibilityAndKeyboardAction() {
        Activity context = Robolectric.buildActivity(Activity.class).setup().get();
        SetBarsView bars = new SetBarsView(context);
        AtomicInteger edited = new AtomicInteger(-1);
        bars.bind("sets", 3, Arrays.asList(12, 11), 1,
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT), edited::set);
        bars.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        bars.layout(0, 0, bars.getMeasuredWidth(), bars.getMeasuredHeight());
        context.setContentView(bars);

        AccessibilityNodeProvider provider = bars.getAccessibilityNodeProvider();
        assertNotNull(provider);
        AccessibilityNodeInfo first = provider.createAccessibilityNodeInfo(0);
        AccessibilityNodeInfo second = provider.createAccessibilityNodeInfo(1);
        assertNotNull(first);
        assertNotNull(second);
        assertTrue(first.getContentDescription().toString().contains("Satz 1: 12"));
        assertTrue(second.getContentDescription().toString().contains("Satz 2: 11"));
        assertTrue(second.isSelected());
        assertTrue(provider.performAction(1, AccessibilityNodeInfo.ACTION_CLICK, null));
        assertEquals(1, edited.get());

        edited.set(-1);
        bars.requestFocus();
        assertTrue(provider.performAction(0, AccessibilityNodeInfo.ACTION_FOCUS, null));
        bars.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        assertEquals("Enter must activate the keyboard-focused set", 0, edited.get());
        first.recycle();
        second.recycle();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
