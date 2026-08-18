package de.thonktank.autosecretary;

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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class AccessibilityLayoutMatrixRobolectricTest {
    private static final int[] WIDTHS_DP = {320, 412, 600};
    private static final float[] FONT_SCALES = {1f, 1.3f, 2f};

    @Test public void todayAndInlineEditorFitEveryRequiredWidthAndFontScale() {
        for (int widthDp : WIDTHS_DP) for (float fontScale : FONT_SCALES) {
            Context context = configuredContext(widthDp, fontScale);
            renderToday(context, widthDp, fontScale);
            renderInlineEditor(context, widthDp, fontScale);
        }
    }

    @Test public void talkBackOrderRolesStatesAndKeyboardFollowTheVisualFlow() {
        Context context = configuredContext(412, 1f);
        AtomicInteger changes = new AtomicInteger();
        FocusTaskView focus = new FocusTaskView(context);
        TaskSnapshot task = setTask(false);
        focus.bind(task, false, true, palette(), expanded(), new Actions() {
            @Override public void onSetProgressEditorStateChanged(SetProgressEditorState state) {
                changes.incrementAndGet();
            }
        });
        measure(focus, dp(context, 360), dp(context, 2_400));

        XpVesselView vessel = first(focus, XpVesselView.class);
        DewDotView dot = first(focus, DewDotView.class);
        EditText input = focus.findViewById(R.id.set_progress_input);
        TextView save = focus.findViewById(R.id.set_progress_save);
        TextView toggle = focus.findViewById(R.id.set_progress_toggle_done);
        assertNotNull(vessel); assertNotNull(dot); assertNotNull(input);
        assertNotNull(save); assertNotNull(toggle);
        List<View> order = descendants(focus);
        assertTrue(order.indexOf(vessel) < order.indexOf(dot));
        assertTrue(order.indexOf(dot) < order.indexOf(input));
        assertTrue(order.indexOf(input) < order.indexOf(save));
        assertTrue(order.indexOf(save) < order.indexOf(toggle));

        AccessibilityNodeInfo dotInfo = AccessibilityNodeInfo.obtain();
        dot.onInitializeAccessibilityNodeInfo(dotInfo);
        assertEquals(android.widget.Button.class.getName(), dotInfo.getClassName());
        assertTrue(dotInfo.isCheckable());
        assertFalse(dotInfo.isChecked());
        assertTrue(dot.getContentDescription().toString().contains("Beinpresse"));
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
    }

    private static void renderToday(Context context, int widthDp, float fontScale) {
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        DashboardRenderer renderer = new DashboardRenderer(context, scroll, content,
                new Actions(), "test");
        renderer.render(new DashboardUiState(NavigationDestination.TODAY,
                        DashboardFixtures.fullDashboard(), CalendarUiState.empty(), palette(),
                        CalendarPermissionStatus.GRANTED, false, Collections.emptySet(),
                        EditorUiState.closed()), UiThemeMode.AUTO, UpdateUiState.idle());
        measure(scroll, dp(context, widthDp), dp(context, 8_000));
        View focus = content.findViewById(R.id.dashboard_focus);
        assertNotNull(label(widthDp, fontScale), focus);
        assertTrue(label(widthDp, fontScale), focus.getMeasuredWidth() > 0);
        assertTrue(label(widthDp, fontScale), focus.getMeasuredHeight() > 0);
        assertHorizontalBounds(content, label(widthDp, fontScale));
    }

    private static void renderInlineEditor(Context context, int widthDp, float fontScale) {
        FocusTaskView focus = new FocusTaskView(context);
        focus.bind(setTask(false), false, true, palette(), expanded(), new Actions());
        int horizontalPagePadding = context.getResources().getDimensionPixelSize(
                R.dimen.page_start) + context.getResources().getDimensionPixelSize(R.dimen.page_end);
        int available = dp(context, widthDp) - horizontalPagePadding;
        measure(focus, available, dp(context, 4_000));
        SetProgressEditorView editor = focus.findViewById(R.id.set_progress_editor);
        EditText input = focus.findViewById(R.id.set_progress_input);
        TextView save = focus.findViewById(R.id.set_progress_save);
        TextView toggle = focus.findViewById(R.id.set_progress_toggle_done);
        String message = label(widthDp, fontScale);
        assertNotNull(message, editor); assertEquals(message, View.VISIBLE, editor.getVisibility());
        assertHorizontalBounds(editor, message);
        int target = dp(context, 48);
        for (View control : Arrays.asList(input, save, toggle)) {
            assertNotNull(message, control);
            assertTrue(message + " width", control.getWidth() >= target);
            assertTrue(message + " height", control.getHeight() >= target);
        }
        assertTextFits(save, message);
        assertTextFits(toggle, message);
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
            assertTrue(message + " left " + child.getClass().getSimpleName(), child.getLeft() >= 0);
            assertTrue(message + " right " + child.getClass().getSimpleName(),
                    child.getRight() <= group.getWidth());
            assertHorizontalBounds(child, message);
        }
    }

    private static TaskSnapshot setTask(boolean done) {
        TaskStepSnapshot step = new TaskStepSnapshot("set-step", "Beinpresse", done,
                StepAmountKind.SETS_REPS, 3, 12, null, "23 kg",
                Collections.singletonList(10), 2, 15, done ? 15 : 0);
        return new TaskSnapshot("training", "training-today", "Training mit langem Titel",
                TaskSlot.MORNING, "", "Beinpresse", Recurrence.DAILY,
                Collections.singletonList(step), done ? 0 : 1, false, false, false, false,
                2, 1_000L, 15, done ? 15 : 0, 0, done);
    }

    private static SetProgressEditorState expanded() {
        return SetProgressEditorState.closed().toggle("set-step", "10");
    }

    private static Context configuredContext(int widthDp, float fontScale) {
        Context base = ApplicationProvider.getApplicationContext();
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.screenWidthDp = widthDp;
        configuration.screenHeightDp = 2_400;
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

    private static class Actions implements DashboardRenderer.Actions {
        @Override public void onAddTask() { }
        @Override public void onTaskAction(TaskSnapshot task) { }
        @Override public void onTaskMenu(TaskSnapshot task) { }
        @Override public void onComplete(TaskSnapshot task) { }
        @Override public void onDefer(TaskSnapshot task) { }
        @Override public void onToggleStep(TaskStepSnapshot step) { }
        @Override public void onTheme(UiThemeMode mode) { }
        @Override public void onCalendarPermission() { }
        @Override public void onUpdates() { }
    }
}
