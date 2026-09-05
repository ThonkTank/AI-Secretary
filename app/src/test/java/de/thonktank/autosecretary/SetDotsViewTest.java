package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.ui.today.SetDotsView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class SetDotsViewTest {
    @Test public void threeEightAndTwentySetsUseOneResponsiveRow() {
        assertRows(3, 1);
        assertRows(8, 1);
        assertRows(20, 1);
    }

    @Test public void twentyOneAndFortySetsWrapAtTwentyWithoutScrolling() {
        assertRows(21, 2);
        assertRows(40, 2);
    }

    @Test public void descriptionExposesProgressCurrentAndSelectedCorrection() {
        Context context = ApplicationProvider.getApplicationContext();
        SetDotsView dots = new SetDotsView(context);
        dots.bind("sets", 20, Arrays.asList(12, 11, 10), 1,
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT));

        String description = dots.getContentDescription().toString();
        assertTrue(description.contains("3 von 20 Sätzen abgeschlossen"));
        assertTrue(description.contains("Satz 4 ist aktuell"));
        assertTrue(description.contains("Satz 2 mit 11 Wiederholungen ausgewählt"));

        AccessibilityNodeInfo info = dots.createAccessibilityNodeInfo();
        assertEquals(android.widget.ProgressBar.class.getName(), info.getClassName());
        assertEquals(3f, info.getRangeInfo().getCurrent(), 0f);
        assertTrue(info.getActionList().stream().noneMatch(action ->
                action.getId() == AccessibilityNodeInfo.ACTION_CLICK));
        info.recycle();
    }

    private static void assertRows(int sets, int rows) {
        Context context = ApplicationProvider.getApplicationContext();
        SetDotsView dots = new SetDotsView(context);
        dots.bind("sets-" + sets, sets, Collections.emptyList(), -1,
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT));
        int width = dp(context, 320);
        dots.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        assertEquals(width, dots.getMeasuredWidth());
        assertEquals(dp(context, Math.max(24, rows * 20 + 4)), dots.getMeasuredHeight());
        assertTrue(dots.getParent() == null);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
