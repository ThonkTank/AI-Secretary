package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.RepetitionProgressUiModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.update.presentation.UpdateUiState;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class AccessibilityLayoutMatrixRobolectricTest {
    private static final int[] WIDTHS_DP = {320, 412, 600};
    private static final float[] FONT_SCALES = {1f, 1.3f, 2f};
    private static final LocalTime[] PALETTE_TIMES = {
            LocalTime.of(9, 40), LocalTime.of(19, 35), LocalTime.of(23, 50)};

    @Test public void todayAndInlineEditorFitEveryRequiredWidthAndFontScale() {
        for (int widthDp : WIDTHS_DP) for (float fontScale : FONT_SCALES) {
            Context context = configuredContext(widthDp, fontScale);
            for (LocalTime time : PALETTE_TIMES) {
                DayPalette palette = DayPalette.at(time, DayPalette.Mode.AUTO);
                renderToday(context, widthDp, fontScale, palette);
                renderRepetitionControls(context, widthDp, fontScale, palette);
                renderDynamicLimit(context, widthDp, fontScale, palette);
            }
        }
    }

    @Test public void largeSystemTextNeverShowsMoreFollowingRowsThanDefaultText() {
        int normal = visibleFollowingRows(configuredContext(412, 1f));
        int large = visibleFollowingRows(configuredContext(412, 2f));

        assertTrue("default text should leave room for following steps", normal > 0);
        assertTrue("large text must not increase the visible row count", large <= normal);
    }

    @Test public void talkBackOrderRolesStatesAndKeyboardFollowTheVisualFlow() {
        Context context = configuredContext(412, 1f);
        AtomicInteger changes = new AtomicInteger();
        AtomicReference<Integer> correction = new AtomicReference<>();
        FocusTaskView focus = new FocusTaskView(context);
        TaskSnapshot task = setTask(false);
        Actions actions = new Actions() {
            @Override public void onSubmitRepetition(String stepId) {
                changes.incrementAndGet();
            }

            @Override public void onEditRepetition(String stepId, int index) {
                correction.set(index);
            }
        };
        focus.bind(task, false, true, palette(), FocusStepLimit.AUTO,
                RepetitionInputState.idle(), actions);
        measure(focus, dp(context, 360), dp(context, 2_400));

        XpVesselView vessel = first(focus, XpVesselView.class);
        DewDotView dot = first(focus, DewDotView.class);
        View minus = focus.findViewById(R.id.rep_stepper_decrement);
        TextView input = focus.findViewById(R.id.rep_stepper_value);
        View plus = focus.findViewById(R.id.rep_stepper_increment);
        SetBarsView bars = focus.findViewById(R.id.set_bars);
        assertNotNull(vessel); assertNotNull(dot); assertNotNull(input);
        assertNotNull(minus); assertNotNull(plus); assertNotNull(bars);
        List<View> order = descendants(focus);
        assertTrue(order.indexOf(vessel) < order.indexOf(dot));
        assertTrue(order.indexOf(dot) < order.indexOf(input));
        assertTrue(order.indexOf(minus) < order.indexOf(input));
        assertTrue(order.indexOf(input) < order.indexOf(plus));
        assertTrue(order.indexOf(plus) < order.indexOf(bars));

        AccessibilityNodeInfo dotInfo = AccessibilityNodeInfo.obtain();
        dot.onInitializeAccessibilityNodeInfo(dotInfo);
        assertEquals(android.widget.Button.class.getName(), dotInfo.getClassName());
        assertTrue(dotInfo.isCheckable());
        assertFalse(dotInfo.isChecked());
        assertTrue(dot.getContentDescription().toString()
                .contains("Satz 2 mit 12 Wiederholungen"));
        dotInfo.recycle();

        AccessibilityNodeInfo vesselInfo = AccessibilityNodeInfo.obtain();
        vessel.onInitializeAccessibilityNodeInfo(vesselInfo);
        assertEquals(android.widget.Button.class.getName(), vesselInfo.getClassName());
        assertTrue(vessel.getContentDescription().length() > 0);
        vesselInfo.recycle();

        assertTrue(dot.isFocusable());
        assertTrue(dot.onKeyDown(KeyEvent.KEYCODE_ENTER,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)));
        assertTrue(dot.onKeyUp(KeyEvent.KEYCODE_ENTER,
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)));
        assertEquals(1, changes.get());

        AccessibilityNodeInfo barsInfo = AccessibilityNodeInfo.obtain();
        bars.onInitializeAccessibilityNodeInfo(barsInfo);
        assertTrue(barsInfo.getContentDescription().toString().contains("Satz 1: 10"));
        AccessibilityNodeInfo.AccessibilityAction edit = barsInfo.getActionList().stream()
                .filter(action -> action.getLabel() != null
                        && action.getLabel().toString().contains("Satz 1"))
                .findFirst().orElseThrow(AssertionError::new);
        assertTrue(bars.performAccessibilityAction(edit.getId(), null));
        assertEquals(Integer.valueOf(0), correction.get());
        barsInfo.recycle();
    }

    private static void renderToday(Context context, int widthDp, float fontScale,
                                    DayPalette palette) {
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        DashboardRenderer renderer = new DashboardRenderer(context, scroll, content,
                new Actions(), "test");
        renderer.render(new DashboardUiState(NavigationDestination.TODAY,
                        DashboardFixtures.fullDashboard(), CalendarUiState.empty(), palette,
                        CalendarPermissionStatus.GRANTED, false, Collections.emptySet(),
                        EditorUiState.closed()), UiThemeMode.AUTO, UpdateUiState.idle());
        measure(scroll, dp(context, widthDp), dp(context, 8_000));
        View focus = content.findViewById(R.id.dashboard_focus);
        assertNotNull(label(widthDp, fontScale), focus);
        assertTrue(label(widthDp, fontScale), focus.getMeasuredWidth() > 0);
        assertTrue(label(widthDp, fontScale), focus.getMeasuredHeight() > 0);
        assertHorizontalBounds(content, label(widthDp, fontScale));
    }

    private static void renderRepetitionControls(Context context, int widthDp, float fontScale,
                                                 DayPalette palette) {
        FocusTaskView focus = new FocusTaskView(context);
        Actions actions = new Actions();
        focus.bind(setTask(false), false, true, palette, FocusStepLimit.AUTO,
                RepetitionInputState.idle(), actions);
        int horizontalPagePadding = context.getResources().getDimensionPixelSize(
                R.dimen.page_start) + context.getResources().getDimensionPixelSize(R.dimen.page_end);
        int available = dp(context, widthDp) - horizontalPagePadding;
        measure(focus, available, dp(context, 4_000));
        View minus = focus.findViewById(R.id.rep_stepper_decrement);
        TextView input = focus.findViewById(R.id.rep_stepper_value);
        View plus = focus.findViewById(R.id.rep_stepper_increment);
        SetBarsView bars = focus.findViewById(R.id.set_bars);
        String message = label(widthDp, fontScale);
        assertNotNull(message, minus); assertNotNull(message, input);
        assertNotNull(message, plus); assertNotNull(message, bars);
        int target = dp(context, 44);
        for (View control : Arrays.asList(minus, plus, bars)) {
            assertNotNull(message, control);
            assertTrue(message + " height", control.getHeight() >= target);
        }
        assertTrue(message + " minus width", minus.getWidth() >= target);
        assertTrue(message + " plus width", plus.getWidth() >= target);
        assertTextFits(input, message);
    }

    private static void renderDynamicLimit(Context context, int widthDp, float fontScale,
                                           DayPalette palette) {
        FocusTaskView focus = new FocusTaskView(context);
        Actions actions = new Actions();
        focus.bind(longTask(), false, true, palette, FocusStepLimit.FIVE,
                RepetitionInputState.idle(), actions);
        int horizontalPagePadding = context.getResources().getDimensionPixelSize(
                R.dimen.page_start) + context.getResources().getDimensionPixelSize(R.dimen.page_end);
        int availableWidth = dp(context, widthDp) - horizontalPagePadding;
        int availableHeight = dp(context, 540);
        measureExactly(focus, availableWidth, availableHeight);
        String message = label(widthDp, fontScale);
        assertTrue(message + " card extent", focus.cardExtentForTest() <= availableHeight);
        assertTrue(message + " numeric limit", focus.visibleFollowingStepsForTest() <= 5);
        assertHorizontalBounds(focus, message);
    }

    private static void assertTextFits(TextView view, String message) {
        assertNotNull(message, view.getLayout());
        int contentHeight = view.getHeight() - view.getCompoundPaddingTop()
                - view.getCompoundPaddingBottom();
        assertTrue(message + " text height", view.getLayout().getHeight() <= contentHeight);
    }

    private static void assertHorizontalBounds(View root, String message) {
        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child.getVisibility() != View.VISIBLE) continue;
            boolean scrollContent = group instanceof HorizontalScrollView;
            if (!scrollContent) {
                assertTrue(message + " left " + child.getClass().getSimpleName(),
                        child.getLeft() >= 0);
                assertTrue(message + " right " + child.getClass().getSimpleName(),
                        child.getRight() <= group.getWidth());
                assertHorizontalBounds(child, message);
            }
        }
    }

    private static TaskSnapshot setTask(boolean done) {
        FocusStepUiModel step = new FocusStepUiModel("set-step", "Beinpresse",
                "3 × 12", "23 kg", done,
                RepetitionProgressUiModel.sets(3, 12, Collections.singletonList(10)),
                2, 15, done ? 15 : 0);
        return new TaskSnapshot("training", "training-today", "Training mit langem Titel",
                TaskSlot.MORNING, "", "Beinpresse", Recurrence.DAILY,
                Collections.singletonList(step), done ? 0 : 1, false, false, false, false,
                2, 1_000L, 15, done ? 15 : 0, 0, done);
    }

    private static TaskSnapshot longTask() {
        List<FocusStepUiModel> steps = new ArrayList<>();
        steps.add(new FocusStepUiModel("active", "Kniebeugen", "3 × 12",
                "Hantel 10 kg, langsam runter", false,
                RepetitionProgressUiModel.sets(3, 12, Collections.singletonList(12)),
                1, 10, 0));
        for (int index = 1; index <= 5; index++)
            steps.add(new FocusStepUiModel("future-" + index,
                    "Ein ausgesprochen langer Folgeschritt " + index,
                    "12 Wdh.", "Lange Notiz, die bei großer Schrift zwei Zeilen benötigt",
                    false, RepetitionProgressUiModel.single(12, Collections.emptyList()),
                    0, 10, 0));
        return new TaskSnapshot("long", "long-today", "Routine mit langem Titel",
                TaskSlot.MORNING, "", "Kniebeugen", Recurrence.DAILY, steps, steps.size(),
                false, false, false, false, 1, 1_000L);
    }

    private static Context configuredContext(int widthDp, float fontScale) {
        Context base = ApplicationProvider.getApplicationContext();
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.screenWidthDp = widthDp;
        configuration.screenHeightDp = 720;
        configuration.smallestScreenWidthDp = Math.min(widthDp, 600);
        configuration.fontScale = fontScale;
        return base.createConfigurationContext(configuration);
    }

    private static DayPalette palette() {
        return DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO);
    }

    private static void measure(View view, int width, int maximumHeight) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(maximumHeight, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static void measureExactly(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static int visibleFollowingRows(Context context) {
        FocusTaskView focus = new FocusTaskView(context);
        Actions actions = new Actions();
        focus.bind(longTask(), false, true, palette(), FocusStepLimit.AUTO,
                RepetitionInputState.idle(), actions);
        measureExactly(focus, dp(context, 330), dp(context, 540));
        return focus.visibleFollowingStepsForTest();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static String label(int width, float scale) {
        return width + "dp at font scale " + scale;
    }

    private static List<View> descendants(View root) {
        List<View> result = new ArrayList<>();
        if (!(root instanceof ViewGroup)) return result;
        ViewGroup group = (ViewGroup) root;
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            result.add(child);
            result.addAll(descendants(child));
        }
        return result;
    }

    private static <T extends View> T first(View root, Class<T> type) {
        for (View child : descendants(root))
            if (child.getVisibility() == View.VISIBLE && type.isInstance(child))
                return type.cast(child);
        return null;
    }

    private static class Actions extends FocusTestActions {
        @Override public void onAddTask() { }
        @Override public void onTaskAction(TimelineTaskUiModel task) { }
        @Override public void onTaskMenu(TimelineTaskUiModel task) { }
        @Override public void onTheme(UiThemeMode mode) { }
        @Override public void onFocusStepLimit(FocusStepLimit limit) { }
        @Override public void onCalendarPermission() { }
        @Override public void onUpdates() { }
    }
}
