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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.presentation.options.OptionsAction;
import de.thonktank.autosecretary.presentation.options.OptionsScreenState;
import de.thonktank.autosecretary.update.presentation.UpdateUiState;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class OptionsViewTest {
    @Test public void everyFocusLimitIsSelectableAndTheBoundValueIsExposed() {
        Context context = ApplicationProvider.getApplicationContext();
        AtomicReference<FocusStepLimit> selected = new AtomicReference<>();
        OptionsView view = new OptionsView(context, event -> {
            if (event instanceof OptionsAction.FocusStepLimitSelected)
                selected.set(((OptionsAction.FocusStepLimitSelected) event).limit);
        });
        view.bind(state(FocusStepLimit.THREE), "test");
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

        view.bind(state(FocusStepLimit.AUTO), "test");
        for (View button : focusLimitButtons(view)) {
            FocusStepLimit limit = (FocusStepLimit) button.getTag();
            if (limit == FocusStepLimit.AUTO) assertTrue(button.isSelected());
            else assertFalse(button.isSelected());
        }
    }

    @Test public void restTimerDefaultUsesTheOptionsActionBoundary() {
        Context context = ApplicationProvider.getApplicationContext();
        AtomicReference<Integer> selected = new AtomicReference<>();
        OptionsView view = new OptionsView(context, action -> {
            if (action instanceof OptionsAction.RestTimerDefaultChanged)
                selected.set(((OptionsAction.RestTimerDefaultChanged) action).seconds);
        });
        view.bind(state(FocusStepLimit.AUTO), "test");

        View less = findByDescription(view,
                context.getString(R.string.timer_less_description));
        View more = findByDescription(view,
                context.getString(R.string.timer_more_description));
        assertTrue(less != null);
        assertTrue(more != null);
        assertTrue(less.performClick());
        assertEquals(Integer.valueOf(45), selected.get());
        assertTrue(more.performClick());
        assertEquals(Integer.valueOf(75), selected.get());
    }

    private static OptionsScreenState state(FocusStepLimit limit) {
        return new OptionsScreenState(DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT),
                UiThemeMode.AUTO, limit, 60, CalendarPermissionStatus.GRANTED,
                CalendarUiState.empty(), UpdateUiState.idle(), Collections.emptyList());
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

    private static View findByDescription(View root, String description) {
        CharSequence contentDescription = root.getContentDescription();
        if (contentDescription != null && description.contentEquals(contentDescription))
            return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = findByDescription(group.getChildAt(index), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
