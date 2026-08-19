package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.update.presentation.UpdateUiState;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class OptionsViewTest {
    @Test public void everyFocusLimitIsSelectableAndTheBoundValueIsExposed() {
        Context context = ApplicationProvider.getApplicationContext();
        AtomicReference<FocusStepLimit> selected = new AtomicReference<>();
        OptionsView view = new OptionsView(context, event -> {
            if (event instanceof DashboardEvent.FocusStepLimitSelected)
                selected.set(((DashboardEvent.FocusStepLimitSelected) event).limit);
        });
        view.bind(DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT), UiThemeMode.AUTO,
                FocusStepLimit.THREE, CalendarPermissionStatus.GRANTED,
                CalendarUiState.empty(), "test", UpdateUiState.idle());
        int width = dp(context, 320);
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(context, 2_000), View.MeasureSpec.AT_MOST));
        view.layout(0, 0, width, view.getMeasuredHeight());

        List<View> buttons = focusLimitButtons(view);
        assertEquals(FocusStepLimit.values().length, buttons.size());
        for (View button : buttons) {
            FocusStepLimit limit = (FocusStepLimit) button.getTag();
            assertEquals(limit == FocusStepLimit.THREE, button.isSelected());
            assertTrue(button.getWidth() >= dp(context, 48));
            assertTrue(button.getHeight() >= dp(context, 48));
            assertTrue(button.performClick());
            assertEquals(limit, selected.get());
        }

        view.bind(DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT), UiThemeMode.AUTO,
                FocusStepLimit.AUTO, CalendarPermissionStatus.GRANTED,
                CalendarUiState.empty(), "test", UpdateUiState.idle());
        for (View button : focusLimitButtons(view)) {
            FocusStepLimit limit = (FocusStepLimit) button.getTag();
            if (limit == FocusStepLimit.AUTO) assertTrue(button.isSelected());
            else assertFalse(button.isSelected());
        }
    }

    private static List<View> focusLimitButtons(View root) {
        List<View> result = new ArrayList<>();
        collect(root, result);
        return result;
    }

    private static void collect(View view, List<View> result) {
        if (view.getTag() instanceof FocusStepLimit) result.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++)
            collect(group.getChildAt(index), result);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
