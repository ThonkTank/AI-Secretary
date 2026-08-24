package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.shadows.ShadowLooper.shadowMainLooper;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.RippleDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w412dp-h892dp-xhdpi")
public final class TaskEditorAdaptiveRobolectricTest {
    @Test public void layoutPolicyKeepsReferenceGeometryAndSelectsBothCompactTriggers() {
        TaskEditorLayoutPolicy standard = TaskEditorLayoutPolicy.from(
                configuredContext(412, 892, 1f).getResources());
        TaskEditorLayoutPolicy narrow = TaskEditorLayoutPolicy.from(
                configuredContext(320, 640, 1f).getResources());
        TaskEditorLayoutPolicy justBelowBoundary = TaskEditorLayoutPolicy.from(
                configuredContext(359, 640, 1f).getResources());
        TaskEditorLayoutPolicy atBoundary = TaskEditorLayoutPolicy.from(
                configuredContext(360, 640, 1f).getResources());
        TaskEditorLayoutPolicy largeText = TaskEditorLayoutPolicy.from(
                configuredContext(412, 892, 1.3f).getResources());

        assertFalse(standard.compact);
        assertEquals(60, standard.pageStartDp);
        assertEquals(22, standard.pageEndDp);
        assertEquals(80, standard.footerHeightDp);
        assertEquals(7, standard.weekdayColumns);
        assertTrue(narrow.compact);
        assertTrue(justBelowBoundary.compact);
        assertFalse(atBoundary.compact);
        assertTrue(largeText.compact);
        assertEquals(112, narrow.footerHeightDp);
        assertEquals(4, narrow.weekdayColumns);
    }

    @Test public void promptPolicyKeepsTallReferenceAndCentersLowScreensSafely() {
        TaskEditorLayoutPolicy standard = TaskEditorLayoutPolicy.from(
                configuredContext(412, 892, 1f).getResources());
        TaskEditorLayoutPolicy compact = TaskEditorLayoutPolicy.from(
                configuredContext(320, 640, 1.5f).getResources());

        assertEquals(250, standard.promptTopDp(892, 280));
        assertEquals(180, compact.promptTopDp(640, 280));
        assertEquals(16, compact.promptTopDp(300, 400));
    }

    @Test public void compactFooterUsesTwoRowsAndWeekdaysUseFourPlusThreeTargets() {
        Context context = configuredContext(320, 640, 1.5f);
        TaskEditorView editor = new TaskEditorView(context, new NoopListener());
        editor.bind(TaskEditorGoldenScenario.ALL.get(3).state(), palette(),
                TaskEditorGoldenScenario.TODAY);
        measure(editor, context, 320, 640);

        LinearLayout actions = editor.findViewById(R.id.task_editor_actions);
        assertEquals(LinearLayout.VERTICAL, actions.getOrientation());
        assertEquals(2, actions.getChildCount());
        assertEquals(dp(context, 112), actions.getMeasuredHeight());

        LinearLayout dependent = editor.findViewWithTag(TaskEditorView.DEPENDENT_TAG);
        assertNotNull(dependent);
        LinearLayout weekdays = (LinearLayout) dependent.getChildAt(0);
        assertEquals(LinearLayout.VERTICAL, weekdays.getOrientation());
        assertEquals(2, weekdays.getChildCount());
        assertEquals(4, ((ViewGroup) weekdays.getChildAt(0)).getChildCount());
        assertEquals(4, ((ViewGroup) weekdays.getChildAt(1)).getChildCount());
        for (TextView day : textDescendants(weekdays)) {
            assertTrue(day.getMeasuredWidth() >= dp(context, 48));
            assertTrue(day.getMeasuredHeight() >= dp(context, 48));
        }
    }

    @Test public void focusSheetScrollsExactlyWhenCompactContentOverflows() {
        Context context = configuredContext(320, 640, 1.5f);
        TaskEditorView editor = new TaskEditorView(context, new NoopListener());
        editor.bind(TaskEditorGoldenScenario.ALL.get(5).state(), palette(),
                TaskEditorGoldenScenario.TODAY);
        measure(editor, context, 320, 640);
        ScrollView scroll = editor.findViewById(R.id.task_editor_scroll);
        boolean overflows = scroll.getChildAt(0).getMeasuredHeight()
                > scroll.getMeasuredHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
        assertEquals(overflows, scroll.canScrollVertically(1));
        assertFalse(scroll.isFillViewport());

        editor.bind(TaskEditorGoldenScenario.ALL.get(0).state()
                        .withPage(EditorUiState.Page.STEPS, false),
                palette(), TaskEditorGoldenScenario.TODAY);
        measure(editor, context, 320, 640);
        assertTrue(scroll.getChildAt(0).getMeasuredHeight()
                <= scroll.getMeasuredHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom());
        assertFalse(scroll.canScrollVertically(1));
    }

    @Test public void editorControlsExposeRipplesRolesSelectionAndLocalizedDescriptions() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TaskEditorView editor = new TaskEditorView(activity, new NoopListener());
        activity.setContentView(editor);
        editor.bind(TaskEditorGoldenScenario.ALL.get(3).state(), palette(),
                TaskEditorGoldenScenario.TODAY);
        measure(editor, activity, 412, 892);
        shadowMainLooper().idle();

        TextView selected = text(editor, activity.getString(R.string.rhythm_weekdays));
        assertNotNull(selected);
        assertTrue(selected.getBackground() instanceof RippleDrawable);
        AccessibilityNodeInfo selectedInfo = AccessibilityNodeInfo.obtain();
        selected.onInitializeAccessibilityNodeInfo(selectedInfo);
        assertEquals(Button.class.getName(), selectedInfo.getClassName());
        assertTrue(selectedInfo.isCheckable());
        assertTrue(selectedInfo.isChecked());

        LinearLayout dependent = editor.findViewWithTag(TaskEditorView.DEPENDENT_TAG);
        LinearLayout weekdayRow = (LinearLayout) dependent.getChildAt(0);
        assertTrue(weekdayRow.getTouchDelegate() instanceof TaskEditorTouchDelegateGroup);
        for (TextView day : textDescendants(weekdayRow)) {
            AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
            day.onInitializeAccessibilityNodeInfo(info);
            Rect bounds = new Rect();
            info.getBoundsInParent(bounds);
            assertTrue(bounds.width() >= dp(activity, 48));
            assertTrue(bounds.height() >= dp(activity, 48));
            assertEquals(Button.class.getName(), info.getClassName());
            assertTrue(info.isCheckable());
            info.recycle();
        }
        selectedInfo.recycle();

        editor.bind(TaskEditorGoldenScenario.ALL.get(7).state(), palette(),
                TaskEditorGoldenScenario.TODAY);
        measure(editor, activity, 412, 892);
        LinearLayout leaf = editor.findViewById(R.id.task_editor_leaf);
        View summary = leaf.getChildAt(1);
        assertTrue(summary.getForeground() instanceof RippleDrawable);
        AccessibilityNodeInfo summaryInfo = AccessibilityNodeInfo.obtain();
        summary.onInitializeAccessibilityNodeInfo(summaryInfo);
        assertEquals(Button.class.getName(), summaryInfo.getClassName());
        assertTrue(summary.getContentDescription().toString().contains("Rhythmus"));
        summaryInfo.recycle();

        editor.bind(TaskEditorGoldenScenario.ALL.get(5).state(), palette(),
                TaskEditorGoldenScenario.TODAY);
        measure(editor, activity, 412, 892);
        TextView up = text(editor, activity.getString(R.string.editor_move_up_symbol));
        assertNotNull(up);
        assertTrue(up.getContentDescription().toString().contains("Liegestütze"));
        assertTrue(up.getContentDescription().toString().contains("oben"));

        editor.bind(TaskEditorGoldenScenario.ALL.get(6).state(), palette(),
                TaskEditorGoldenScenario.TODAY);
        measure(editor, activity, 412, 892);
        shadowMainLooper().idle();
        View number = editor.findViewWithTag("step:s0:sets");
        assertNotNull(number);
        assertTrue(((ViewGroup) number.getParent()).getTouchDelegate()
                instanceof TaskEditorTouchDelegateGroup);

        editor.bind(TaskEditorGoldenScenario.ALL.get(0).state(), palette(),
                TaskEditorGoldenScenario.TODAY);
        measure(editor, activity, 412, 892);
        shadowMainLooper().idle();
        View valueRow = described(editor, "für 6 Wochen");
        assertNotNull(valueRow);
        assertTrue(((ViewGroup) valueRow.getParent()).getTouchDelegate()
                instanceof TaskEditorTouchDelegateGroup);
    }

    @Test public void accessibilityTraversalFollowsHeaderContentAndFooter() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TaskEditorView editor = new TaskEditorView(activity, new NoopListener());
        activity.setContentView(editor);
        editor.bind(TaskEditorGoldenScenario.ALL.get(0).state(), palette(),
                TaskEditorGoldenScenario.TODAY);
        measure(editor, activity, 412, 892);

        TextView cancel = editor.findViewById(R.id.task_editor_cancel);
        TextView context = editor.findViewById(R.id.task_editor_context);
        LinearLayout leaf = editor.findViewById(R.id.task_editor_leaf);
        View question = leaf.getChildAt(0);
        View title = editor.findViewWithTag("task:title");
        Button primary = editor.findViewById(R.id.task_editor_save);

        assertEquals(cancel.getId(), context.getAccessibilityTraversalAfter());
        assertEquals(context.getId(), question.getAccessibilityTraversalAfter());
        assertEquals(question.getId(), title.getAccessibilityTraversalAfter());
        assertEquals(title.getId(), cancel.getNextFocusForwardId());
        assertTrue(primary.getAccessibilityTraversalAfter() != View.NO_ID);
        assertEquals(View.NO_ID, primary.getNextFocusForwardId());
    }

    @Test public void everyInteractiveTargetIsAtLeastFortyEightDpInBothLayouts() {
        int[] scenarios = {0, 3, 5, 6, 7};
        assertScenarioTargets(configuredContext(412, 892, 1f), 412, 892, scenarios);
        assertScenarioTargets(configuredContext(320, 640, 1.5f), 320, 640, scenarios);

        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TaskEditorView promptEditor = new TaskEditorView(activity, new NoopListener());
        activity.setContentView(promptEditor);
        promptEditor.bind(TaskEditorGoldenScenario.ALL.get(9).state(), palette(),
                TaskEditorGoldenScenario.TODAY);
        measure(promptEditor, activity, 412, 892);
        shadowMainLooper().idle();
        assertNotNull(promptEditor.promptForTest());
        assertInteractiveTargets(promptEditor.promptForTest().getWindow().getDecorView(),
                dp(activity, 48));
    }

    @Test public void motionUsesDesignTokenCurveAndDisabledAnimationsSettleImmediately() {
        assertEquals(240L, TaskEditorMotion.duration(palette()));
        assertEquals(.2f, TaskEditorMotion.EASE_X1, 0f);
        assertEquals(.7f, TaskEditorMotion.EASE_Y1, 0f);
        assertEquals(.3f, TaskEditorMotion.EASE_X2, 0f);
        assertEquals(1f, TaskEditorMotion.EASE_Y2, 0f);

        Context context = ApplicationProvider.getApplicationContext();
        View view = new View(context);
        view.setAlpha(.2f);
        view.setTranslationX(20f);
        view.setTranslationY(20f);
        TaskEditorMotion.enter(view, palette(), 6, new UiStyle(context), false);
        assertEquals(1f, view.getAlpha(), 0f);
        assertEquals(0f, view.getTranslationX(), 0f);
        assertEquals(0f, view.getTranslationY(), 0f);
        AtomicBoolean finished = new AtomicBoolean();
        TaskEditorMotion.fadeOut(view, palette(), () -> finished.set(true), false);
        assertEquals(0f, view.getAlpha(), 0f);
        assertTrue(finished.get());

        TaskEditorMotion.enter(view, palette(), 6, new UiStyle(context), true);
        assertEquals(0f, view.getAlpha(), 0f);
        assertEquals(dp(context, 6), view.getTranslationY(), 0f);
        TaskEditorMotion.cancel(view);
        TaskEditorMotion.settle(view);
        TaskEditorMotion.fadeIn(view, palette(), true);
        assertEquals(0f, view.getAlpha(), 0f);
        TaskEditorMotion.cancel(view);
        TaskEditorMotion.settle(view);
    }

    @Test public void overlappingExpandedTargetsRouteToTheClosestControl() {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout parent = new FrameLayout(context);
        AtomicInteger firstTouches = new AtomicInteger();
        AtomicInteger secondTouches = new AtomicInteger();
        View first = touchRecorder(context, firstTouches);
        View second = touchRecorder(context, secondTouches);
        parent.addView(first, new FrameLayout.LayoutParams(20, 20));
        parent.addView(second, new FrameLayout.LayoutParams(20, 20));
        parent.layout(0, 0, 100, 60);
        first.layout(20, 20, 40, 40);
        second.layout(60, 20, 80, 40);
        TaskEditorTouchDelegateGroup group = new TaskEditorTouchDelegateGroup(parent);
        group.add(new Rect(0, 0, 60, 60), first);
        group.add(new Rect(40, 0, 100, 60), second);

        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 58, 30, 0);
        MotionEvent up = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, 58, 30, 0);
        assertTrue(group.onTouchEvent(down));
        assertTrue(group.onTouchEvent(up));
        down.recycle();
        up.recycle();
        assertEquals(0, firstTouches.get());
        assertEquals(2, secondTouches.get());
    }

    @Test public void editorNightPaletteHasNoIndependentProductionColors() throws Exception {
        String[] files = {"TaskEditorView.java", "TaskEditorControlFactory.java",
                "TaskStepsEditorView.java", "TaskEditorLayoutPolicy.java",
                "TaskEditorMotion.java", "TaskEditorTouchDelegateGroup.java"};
        for (String name : files) {
            String source = new String(Files.readAllBytes(Path.of(
                    "src/main/java/de/thonktank/autosecretary", name)),
                    StandardCharsets.UTF_8);
            assertFalse(name, source.matches("(?s).*0x[0-9a-fA-F]{6,8}.*"));
            assertFalse(name, source.contains("Color.rgb("));
            assertFalse(name, source.contains("Color.parseColor("));
            assertFalse(name, source.contains("Color.BLACK"));
            assertFalse(name, source.contains("Color.WHITE"));
        }
    }

    private static Context configuredContext(int widthDp, int heightDp, float fontScale) {
        Context base = ApplicationProvider.getApplicationContext();
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.screenWidthDp = widthDp;
        configuration.screenHeightDp = heightDp;
        configuration.fontScale = fontScale;
        return base.createConfigurationContext(configuration);
    }

    private static void measure(View view, Context context, int widthDp, int heightDp) {
        int width = dp(context, widthDp);
        int height = dp(context, heightDp);
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void assertScenarioTargets(Context context, int widthDp, int heightDp,
                                              int[] scenarios) {
        TaskEditorView editor = new TaskEditorView(context, new NoopListener());
        for (int scenario : scenarios) {
            editor.bind(TaskEditorGoldenScenario.ALL.get(scenario).state(), palette(),
                    TaskEditorGoldenScenario.TODAY);
            measure(editor, context, widthDp, heightDp);
            shadowMainLooper().idle();
            try {
                assertInteractiveTargets(editor, dp(context, 48));
            } catch (AssertionError failure) {
                throw new AssertionError(widthDp + "x" + heightDp + " scenario "
                        + TaskEditorGoldenScenario.ALL.get(scenario).id + ": "
                        + failure.getMessage(), failure);
            }
        }
    }

    private static void assertInteractiveTargets(View view, int minimumPx) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view.isClickable() || view.isLongClickable()) {
            int width = view.getMeasuredWidth();
            int height = view.getMeasuredHeight();
            if (width < minimumPx || height < minimumPx) {
                if (view.isAttachedToWindow()) assertTrue(
                        "small visual target has no expanded touch delegate: "
                                + describe(view), view.getParent() instanceof ViewGroup
                                && ((ViewGroup) view.getParent()).getTouchDelegate()
                                instanceof TaskEditorTouchDelegateGroup);
                AccessibilityNodeInfo info = view.createAccessibilityNodeInfo();
                Rect bounds = new Rect();
                info.getBoundsInParent(bounds);
                width = Math.max(width, bounds.width());
                height = Math.max(height, bounds.height());
                info.recycle();
            }
            assertTrue("interactive width below 48dp: " + describe(view), width >= minimumPx);
            assertTrue("interactive height below 48dp: " + describe(view), height >= minimumPx);
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++)
            assertInteractiveTargets(group.getChildAt(index), minimumPx);
    }

    private static String describe(View view) {
        if (view instanceof TextView) return view.getClass().getSimpleName() + "["
                + ((TextView) view).getText() + ",measured=" + view.getMeasuredWidth()
                + "x" + view.getMeasuredHeight() + "]";
        return view.getClass().getSimpleName() + "[description="
                + view.getContentDescription() + ",id=" + view.getId() + ",measured="
                + view.getMeasuredWidth() + "x" + view.getMeasuredHeight() + "]";
    }

    private static View touchRecorder(Context context, AtomicInteger touches) {
        View view = new View(context) {
            @Override public boolean onTouchEvent(MotionEvent event) {
                touches.incrementAndGet();
                return true;
            }
        };
        view.setClickable(true);
        return view;
    }

    private static View described(View view, String fragment) {
        CharSequence description = view.getContentDescription();
        if (description != null && description.toString().contains(fragment)) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            View match = described(group.getChildAt(index), fragment);
            if (match != null) return match;
        }
        return null;
    }

    private static TextView text(View root, String value) {
        for (TextView candidate : textDescendants(root))
            if (value.contentEquals(candidate.getText())) return candidate;
        return null;
    }

    private static List<TextView> textDescendants(View root) {
        List<TextView> result = new ArrayList<>();
        collectText(root, result);
        return result;
    }

    private static void collectText(View view, List<TextView> output) {
        if (view instanceof TextView) output.add((TextView) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++)
            collectText(group.getChildAt(index), output);
    }

    private static DayPalette palette() {
        return DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT);
    }

    private static final class NoopListener implements TaskEditorView.Listener {
        @Override public void onDraftChanged(EditorUiState draft) { }
        @Override public void onSave(EditorUiState draft) { }
        @Override public void onDelete(String taskId) { }
        @Override public void onDismiss() { }
    }
}
