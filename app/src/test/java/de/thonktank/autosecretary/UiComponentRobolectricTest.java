package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.TaskStepUiModel;
import de.thonktank.autosecretary.presentation.RepetitionProgressUiModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.provider.Settings;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.calendar.CalendarResult;
import de.thonktank.autosecretary.update.presentation.UpdateUiState;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowValueAnimator;

import java.time.LocalTime;
import java.util.Collections;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class UiComponentRobolectricTest {
    @Test public void activityCreatesTheComponentShell() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            assertEquals(false, activity.isFinishing());
        }
    }

    @Test public void recreationDoesNotRepeatAConsumedConfirmationIntent() {
        Context context = ApplicationProvider.getApplicationContext();
        AutoSecretaryApplication.from(context).legacyStateCleaner().acknowledgeResetNotice();
        Intent launch = new Intent(context, MainActivity.class)
                .putExtra(MainActivity.CONFIRM_TASK, "ongoing")
                .putExtra(MainActivity.CONFIRM_TASK_TITLE, "Praktikum");

        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class, launch)) {
            MainActivity activity = controller.setup().get();
            AlertDialog confirmation = ShadowAlertDialog.getLatestAlertDialog();

            assertNotNull(confirmation);
            assertEquals(activity.getString(R.string.close_task_title),
                    Shadows.shadowOf(confirmation).getTitle());
            assertFalse(activity.getIntent().hasExtra(MainActivity.CONFIRM_TASK));
            assertFalse(activity.getIntent().hasExtra(MainActivity.CONFIRM_TASK_TITLE));

            ShadowAlertDialog.reset();
            controller.recreate();

            assertNull(ShadowAlertDialog.getLatestAlertDialog());
        }
    }

    @Test public void validatorCoversEveryEditorConstraint() {
        TaskEditorValidator validator = new TaskEditorValidator();
        EditorUiState base = EditorUiState.create();
        assertEquals(TaskEditorValidator.Error.TITLE, validator.validate(base));
        EditorUiState weekdays = base.withDraft("Routine", TaskSlot.MORNING,
                Recurrence.WEEKDAYS, 1, 0, Collections.emptyList(), false, "");
        assertEquals(TaskEditorValidator.Error.WEEKDAYS, validator.validate(weekdays));
        EditorUiState ongoing = base.withDraft("Vorhaben", TaskSlot.LATER,
                Recurrence.ONCE, 1, 0, Collections.emptyList(), true, "");
        assertEquals(TaskEditorValidator.Error.CONDITION, validator.validate(ongoing));
        assertEquals(TaskEditorValidator.Error.NONE, validator.validate(
                ongoing.withDraft("Vorhaben", TaskSlot.LATER, Recurrence.ONCE, 1, 0,
                        Collections.emptyList(), true, "Vertrag unterschrieben")));
    }

    @Test public void rendererReusesTheMountedViewTreeForNormalUpdates() {
        Context context = ApplicationProvider.getApplicationContext();
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        DashboardRenderer renderer = new DashboardRenderer(context, scroll, content,
                new NoOpActions(), "1.0");
        DayPalette morning = DayPalette.at(LocalTime.of(8, 0), DayPalette.Mode.AUTO);
        DashboardUiState first = state(morning);

        renderer.render(first, UiThemeMode.AUTO, UpdateUiState.idle());
        View focus = content.findViewById(R.id.dashboard_focus);
        focus.setFocusableInTouchMode(true);
        focus.requestFocus();

        renderer.render(state(DayPalette.at(LocalTime.of(8, 1), DayPalette.Mode.AUTO)),
                UiThemeMode.AUTO, UpdateUiState.idle());

        assertSame(focus, content.findViewById(R.id.dashboard_focus));
        assertSame(focus, content.findFocus());
    }

    @Test public void primaryNavigationControlsMeetAccessibilityContracts() {
        Context context = ApplicationProvider.getApplicationContext();
        int target = context.getResources().getDimensionPixelSize(R.dimen.touch_target);
        UiStyle style = new UiStyle(context);
        assertEquals(style.dp(82), context.getResources().getDimensionPixelSize(R.dimen.header_height));
        assertEquals(style.dp(80), context.getResources().getDimensionPixelSize(R.dimen.footer_height));
        HeaderView header = new HeaderView(context, () -> { });
        View add = header.findViewById(R.id.header_add_task);
        assertTrue(add.getLayoutParams().width >= target);
        assertTrue(add.getLayoutParams().height >= target);
        assertEquals(context.getString(R.string.content_add_task), add.getContentDescription());
        View addVisual = header.findViewById(R.id.header_add_task_visual);
        assertEquals(style.dp(40), addVisual.getLayoutParams().width);
        assertEquals(style.dp(40), addVisual.getLayoutParams().height);

        FooterNavigationView footer = new FooterNavigationView(context, destination -> { });
        footer.measure(View.MeasureSpec.makeMeasureSpec(style.dp(412), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(style.dp(80), View.MeasureSpec.EXACTLY));
        footer.layout(0, 0, footer.getMeasuredWidth(), footer.getMeasuredHeight());
        for (int index = 0; index < footer.getChildCount(); index++) {
            View item = footer.getChildAt(index);
            android.widget.TextView text = (android.widget.TextView) item;
            assertTrue(text.getMinHeight() >= target);
            android.graphics.Rect touchBounds = footer.effectiveTouchBounds(item);
            assertTrue(touchBounds.width() >= target);
            assertTrue(touchBounds.height() >= target);
            assertTrue(text.getText().length() > 0);
        }
    }

    @Test public void narrowFooterLabelReceivesClicksAcrossItsExpandedTouchTarget() {
        Context context = ApplicationProvider.getApplicationContext();
        UiStyle style = new UiStyle(context);
        AtomicReference<NavigationDestination> clicked = new AtomicReference<>();
        FooterNavigationView footer = new FooterNavigationView(context, clicked::set);
        footer.measure(View.MeasureSpec.makeMeasureSpec(style.dp(412), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(style.dp(80), View.MeasureSpec.EXACTLY));
        footer.layout(0, 0, footer.getMeasuredWidth(), footer.getMeasuredHeight());
        View today = footer.findViewById(R.id.navigation_today);
        android.graphics.Rect target = footer.effectiveTouchBounds(today);
        float x = target.left + 1;
        float y = target.centerY();
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, x, y, 0);
        footer.dispatchTouchEvent(down);
        footer.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        assertEquals(NavigationDestination.TODAY, clicked.get());
    }

    @Test public void valueDewAndVesselExposeLabelsAndFortyEightDpTouchTargets() {
        Context context = ApplicationProvider.getApplicationContext();
        UiStyle style = new UiStyle(context);
        FocusTaskView focus = new FocusTaskView(context);
        NoOpActions actions = new NoOpActions();
        focus.bind(DashboardFixtures.taskWithSteps(), true, true,
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO), actions, actions);
        focus.measure(View.MeasureSpec.makeMeasureSpec(style.dp(330), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(style.dp(500), View.MeasureSpec.AT_MOST));
        focus.layout(0, 0, focus.getMeasuredWidth(), focus.getMeasuredHeight());
        int minimum = style.dp(48);
        int dewCount = 0;
        for (View view : descendants(focus)) {
            if (!(view instanceof DewDotView) && !(view instanceof XpVesselView)) continue;
            if (view.getVisibility() != View.VISIBLE) continue;
            assertTrue(view.getWidth() >= minimum);
            assertTrue(view.getHeight() >= minimum);
            assertNotNull(view.getContentDescription());
            assertTrue(view.getContentDescription().length() > 0);
            dewCount++;
        }
        assertEquals(2, dewCount);
    }

    @Test public void reducedMotionDisablesPulseAndGlintWithoutChangingState() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Settings.Global.putFloat(activity.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        FrameLayout root = new FrameLayout(activity);
        activity.setContentView(root);
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO);
        XpVesselView vessel = new XpVesselView(activity);
        root.addView(vessel, new FrameLayout.LayoutParams(104, 104));
        vessel.bind(30, 3, 3, true, 5, palette);
        HeaderView header = new HeaderView(activity, () -> { });
        root.addView(header, new FrameLayout.LayoutParams(600, 164));
        header.bind(LocalTime.NOON, palette,
                new de.thonktank.autosecretary.domain.model.XpProgress(70));
        header.playRewardGlint(palette);

        assertFalse(vessel.isPulsing());
        assertEquals(1f, vessel.fillFraction(), 0f);
        assertFalse(header.isGlintVisible());
        assertTrue(vessel.isEnabled());

        Settings.Global.putFloat(activity.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        ShadowValueAnimator.reset();
    }

    @Test public void repetitionStepperConfirmsAndEditsSavedSetsInline() {
        Context context = ApplicationProvider.getApplicationContext();
        UiStyle style = new UiStyle(context);
        TaskStepUiModel set = new TaskStepUiModel("set-step", "Beinpresse",
                "3 × 12 Wdh. · 23 kg", "3 × 12", "23 kg", false,
                RepetitionProgressUiModel.sets(3, 12, Collections.singletonList(10)),
                2, 15, 0);
        TaskSnapshot task = new TaskSnapshot("training", "training-today", "Training",
                TaskSlot.MORNING, "", "Beinpresse", Recurrence.DAILY,
                Collections.singletonList(set), 1, false, false, false, false,
                2, 1_000L, 15, 0, 0, false);
        AtomicReference<Integer> confirmed = new AtomicReference<>();
        AtomicReference<Integer> editedIndex = new AtomicReference<>();
        AtomicReference<Integer> editedValue = new AtomicReference<>();
        AtomicReference<RepetitionInputState> input =
                new AtomicReference<>(RepetitionInputState.idle());
        FocusTaskView focus = new FocusTaskView(context);
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO);
        NoOpActions actions = new NoOpActions() {
            @Override public void onConfirmRepetitions(String stepId, int repetitions) {
                confirmed.set(repetitions);
            }
            @Override public void onEditRepetition(String stepId, int index,
                                                   int repetitions) {
                editedIndex.set(index);
                editedValue.set(repetitions);
            }
            @Override public void onRepetitionInputStateChanged(RepetitionInputState state) {
                input.set(state);
            }
        };
        focus.bind(task, false, false, palette, FocusStepLimit.AUTO,
                input.get(), actions, actions);
        focus.measure(View.MeasureSpec.makeMeasureSpec(style.dp(330), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(style.dp(600), View.MeasureSpec.AT_MOST));
        focus.layout(0, 0, focus.getMeasuredWidth(), focus.getMeasuredHeight());
        View plus = focus.findViewById(R.id.rep_stepper_increment);
        assertNotNull(plus);
        plus.performClick();
        DewDotView dew = firstDew(focus);
        assertNotNull(dew);
        dew.performClick();
        assertEquals(Integer.valueOf(13), confirmed.get());
        assertNull(input.get().stepId);

        focus.bind(task, false, false, palette, FocusStepLimit.AUTO,
                RepetitionInputState.idle(), actions, actions);
        focus.measure(View.MeasureSpec.makeMeasureSpec(style.dp(330), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(style.dp(600), View.MeasureSpec.AT_MOST));
        focus.layout(0, 0, focus.getMeasuredWidth(), focus.getMeasuredHeight());
        SetBarsView bars = focus.findViewById(R.id.set_bars);
        assertNotNull(bars);
        float x = style.dp(11), y = style.dp(20);
        bars.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0));
        bars.dispatchTouchEvent(MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, x, y, 0));
        focus.findViewById(R.id.rep_stepper_increment).performClick();
        firstDew(focus).performClick();
        assertEquals(Integer.valueOf(0), editedIndex.get());
        assertEquals(Integer.valueOf(11), editedValue.get());
    }

    @Test public void completedStepsCollapseIntoTheDoneStatus() {
        Context context = ApplicationProvider.getApplicationContext();
        java.util.List<Integer> full = Arrays.asList(10, 11, 12);
        TaskStepUiModel set = new TaskStepUiModel("set-step", "Beinpresse",
                "3 × 12 Wdh. · 23 kg", "3 × 12", "23 kg", true,
                RepetitionProgressUiModel.sets(3, 12, full), 2, 15, 15);
        TaskStepUiModel next = new TaskStepUiModel("next", "Duschen", false);
        TaskSnapshot task = new TaskSnapshot("training", "training-today", "Training",
                TaskSlot.MORNING, "", "", Recurrence.DAILY,
                Arrays.asList(set, next), 1, false, false, false, false,
                2, 1_000L, 15, 15, 0, true);
        FocusTaskView focus = new FocusTaskView(context);
        NoOpActions actions = new NoOpActions();
        focus.bind(task, false, false, DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO),
                actions, actions);
        java.util.List<String> texts = new java.util.ArrayList<>();
        for (View view : descendants(focus))
            if (view.getVisibility() == View.VISIBLE && view instanceof TextView)
                texts.add(((TextView) view).getText().toString());
        assertTrue(texts.contains("1 fertig"));
        assertFalse(texts.contains("Beinpresse"));
        assertTrue(texts.contains("Duschen"));
    }

    private static DewDotView firstDew(View root) {
        for (View view : descendants(root))
            if (view instanceof DewDotView && view.getVisibility() == View.VISIBLE)
                return (DewDotView) view;
        return null;
    }

    private static java.util.List<View> descendants(View root) {
        java.util.List<View> result = new java.util.ArrayList<>();
        if (!(root instanceof ViewGroup)) return result;
        ViewGroup group = (ViewGroup) root;
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            result.add(child);
            result.addAll(descendants(child));
        }
        return result;
    }

    private static DashboardUiState state(DayPalette palette) {
        TodayUiModel dashboard = TodayUiModel.compose(
                DashboardFixtures.fullDashboard(), DashboardFixtures.calendarEvents());
        return new DashboardUiState(NavigationDestination.TODAY, dashboard,
                CalendarUiState.from(new CalendarResult.Success(DashboardFixtures.calendarEvents())), palette,
                CalendarPermissionStatus.GRANTED, false, Collections.emptySet(),
                EditorUiState.closed());
    }

    private static class NoOpActions extends FocusTestActions
            implements DashboardRenderer.Actions {
        @Override public void onAddTask() { }
        @Override public void onTaskAction(TaskSnapshot task) { }
        @Override public void onTaskMenu(TaskSnapshot task) { }
        @Override public void onTheme(UiThemeMode mode) { }
        @Override public void onFocusStepLimit(FocusStepLimit limit) { }
        @Override public void onCalendarPermission() { }
        @Override public void onUpdates() { }
    }
}
