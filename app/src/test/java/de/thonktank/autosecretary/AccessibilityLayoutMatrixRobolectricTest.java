package de.thonktank.autosecretary;

import de.thonktank.autosecretary.ui.today.*;

import de.thonktank.autosecretary.presentation.alltasks.AllTasksUiState;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksView;

import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.RepetitionProgressUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;

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
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class AccessibilityLayoutMatrixRobolectricTest {
    private static final LayoutCase[] REPRESENTATIVE_LAYOUTS = {
            new LayoutCase(320, 1f, LocalTime.of(9, 40)),
            new LayoutCase(320, 2f, LocalTime.of(23, 50)),
            new LayoutCase(412, 1.3f, LocalTime.of(19, 35)),
            new LayoutCase(412, 2f, LocalTime.of(9, 40)),
            new LayoutCase(600, 1f, LocalTime.of(23, 50)),
            new LayoutCase(600, 1.3f, LocalTime.of(19, 35))
    };

    @Test public void todayAndInlineEditorFitEveryRequiredWidthAndFontScale() {
        for (LayoutCase value : REPRESENTATIVE_LAYOUTS) {
            Context context = configuredContext(value.widthDp, value.fontScale);
            DayPalette palette = DayPalette.at(value.time, DayPalette.Mode.AUTO);
            renderToday(context, value.widthDp, value.fontScale, palette);
            renderRepetitionControls(context, value.widthDp, value.fontScale, palette);
            renderDynamicLimit(context, value.widthDp, value.fontScale, palette);
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
        DashboardEventRecorder events = new DashboardEventRecorder();
        FocusTaskView focus = new FocusTaskView(context);
        FocusTaskUiModel task = setTask(false);
        focus.bind(task, false, palette(), FocusStepLimit.AUTO,
                RepetitionInputState.idle(), events);
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
        assertEquals("set-step", events.lastToday(
                de.thonktank.autosecretary.presentation.today.TodayAction.Kind
                        .SUBMIT_REPETITION).id);

        AccessibilityNodeProvider setNodes = bars.getAccessibilityNodeProvider();
        assertNotNull(setNodes);
        AccessibilityNodeInfo firstSet = setNodes.createAccessibilityNodeInfo(0);
        assertNotNull(firstSet);
        assertEquals(android.widget.Button.class.getName(), firstSet.getClassName());
        assertTrue(firstSet.getContentDescription().toString().contains("Satz 1: 10"));
        android.graphics.Rect setBounds = new android.graphics.Rect();
        firstSet.getBoundsInParent(setBounds);
        assertTrue(setBounds.width() >= dp(context, 44));
        assertTrue(setBounds.height() >= dp(context, 44));
        assertTrue(setNodes.performAction(0, AccessibilityNodeInfo.ACTION_CLICK, null));
        assertEquals(0, events.lastToday(
                de.thonktank.autosecretary.presentation.today.TodayAction.Kind
                        .EDIT_REPETITION).value);
        firstSet.recycle();
    }

    private static void renderToday(Context context, int widthDp, float fontScale,
                                    DayPalette palette) {
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        DashboardRenderer renderer = new DashboardRenderer(context, scroll, content,
                event -> { }, action -> { }, "test", new RewardAnchorRegistry(),
                new AllTasksView.Listener() { });
        renderer.render(new DashboardUiState(NavigationDestination.TODAY,
                        DashboardFixtures.fullDashboard(), CalendarUiState.empty(), palette,
                        CalendarPermissionStatus.GRANTED, false, Collections.emptySet()),
                AllTasksUiState.empty());
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
        focus.bind(setTask(false), false, palette, FocusStepLimit.AUTO,
                RepetitionInputState.idle(), event -> { });
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
        focus.bind(longTask(), false, palette, FocusStepLimit.FIVE,
                RepetitionInputState.idle(), event -> { });
        int horizontalPagePadding = context.getResources().getDimensionPixelSize(
                R.dimen.page_start) + context.getResources().getDimensionPixelSize(R.dimen.page_end);
        int availableWidth = dp(context, widthDp) - horizontalPagePadding;
        int availableHeight = dp(context, 540);
        measureExactly(focus, availableWidth, availableHeight);
        String message = label(widthDp, fontScale);
        assertTrue(message + " numeric limit",
                ViewTestQueries.visibleFollowingStepRows(focus) <= 5);
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

    private static FocusTaskUiModel setTask(boolean done) {
        FocusStepUiModel step = FocusTaskFixtures.step("set-step", "Beinpresse")
                .amount("3 × 12").note("23 kg").done(done)
                .repetition(RepetitionProgressUiModel.sets(
                        3, 12, Collections.singletonList(10)))
                .combo(1).earnedXp(done ? 15 : 0).build();
        return FocusTaskFixtures.task("training", "Training mit langem Titel")
                .occurrence("training-today").slot(TaskSlot.MORNING)
                .recurrence(Recurrence.DAILY).allowDefer(true).combo(2)
                .rewardBase(done ? 15 : 5).harvestReady(done)
                .steps(Collections.singletonList(step)).build();
    }

    private static FocusTaskUiModel longTask() {
        List<FocusStepUiModel> steps = new ArrayList<>();
        steps.add(FocusTaskFixtures.step("active", "Kniebeugen")
                .amount("3 × 12").note("Hantel 10 kg, langsam runter")
                .repetition(RepetitionProgressUiModel.sets(
                        3, 12, Collections.singletonList(12))).build());
        for (int index = 1; index <= 5; index++)
            steps.add(FocusTaskFixtures.step("future-" + index,
                            "Ein ausgesprochen langer Folgeschritt " + index)
                    .amount("12 Wdh.")
                    .note("Lange Notiz, die bei großer Schrift zwei Zeilen benötigt")
                    .repetition(RepetitionProgressUiModel.single(
                            12, Collections.emptyList())).build());
        return FocusTaskFixtures.task("long", "Routine mit langem Titel")
                .occurrence("long-today").slot(TaskSlot.MORNING)
                .recurrence(Recurrence.DAILY).allowDefer(true).combo(1)
                .steps(steps).build();
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
        focus.bind(longTask(), false, palette(), FocusStepLimit.AUTO,
                RepetitionInputState.idle(), event -> { });
        measureExactly(focus, dp(context, 330), dp(context, 540));
        return ViewTestQueries.visibleFollowingStepRows(focus);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static String label(int width, float scale) {
        return width + "dp at font scale " + scale;
    }

    private static final class LayoutCase {
        final int widthDp;
        final float fontScale;
        final LocalTime time;

        LayoutCase(int widthDp, float fontScale, LocalTime time) {
            this.widthDp = widthDp;
            this.fontScale = fontScale;
            this.time = time;
        }
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
}
