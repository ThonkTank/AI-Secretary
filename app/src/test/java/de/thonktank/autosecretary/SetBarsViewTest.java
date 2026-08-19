package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalTime;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class SetBarsViewTest {
    @Test public void savedAndOpenSetsStayOnOneScrollableLineAndAnimateFor180ms() {
        Context context = ApplicationProvider.getApplicationContext();
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT);
        SetBarsView bars = new SetBarsView(context);
        bars.bind("sets", 20, Collections.emptyList(), -1, palette, ignored -> { });
        bars.bind("sets", 20, Collections.singletonList(12), 0, palette, ignored -> { });
        bars.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        assertEquals(180L, bars.animationDurationForTest());
        assertEquals(0, bars.selectedIndexForTest());
        assertEquals(dp(context, 20 * 30 - 8), bars.getMeasuredWidth());
        assertEquals(dp(context, 44), bars.getMeasuredHeight());
        assertTrue(bars.getContentDescription().toString().contains("Satz 1: 12"));
        assertTrue(bars.getContentDescription().toString().contains("Satz 20 offen"));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
