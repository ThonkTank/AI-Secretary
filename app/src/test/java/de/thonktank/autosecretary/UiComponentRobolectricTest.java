package de.thonktank.autosecretary;

import de.thonktank.autosecretary.ui.today.*;
import de.thonktank.autosecretary.ui.leaf.WoodGrainView;

import de.thonktank.autosecretary.presentation.alltasks.AllTasksAction;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksUiState;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksView;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksViewModel;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.presentation.today.TodayActionSink;

import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.RepetitionProgressUiModel;

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
import androidx.lifecycle.ViewModelProvider;

import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.calendar.CalendarResult;
import de.thonktank.autosecretary.ui.leaf.LeafShape;
import de.thonktank.autosecretary.ui.leaf.LeafSurface;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowValueAnimator;

import java.time.LocalDate;
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

    @Test public void editorLaunchIntentTargetsTheDedicatedStateOwner() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent launch = new Intent(context, MainActivity.class)
                .putExtra(MainActivity.OPEN_EDITOR, true);

        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class, launch)) {
            MainActivity activity = controller.setup().get();
            AppContainer container = AutoSecretaryApplication.from(activity).container();
            TaskEditorViewModel editor = new ViewModelProvider(activity,
                    new TaskEditorViewModel.Factory(container)).get(TaskEditorViewModel.class);

            ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

            assertTrue(editor.state().getValue().content.open);
            assertFalse(activity.getIntent().hasExtra(MainActivity.OPEN_EDITOR));
        }
    }

    @Test public void laterLaunchIntentCannotReplaceAnAlreadyOpenEditorDraft() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            AppContainer container = AutoSecretaryApplication.from(activity).container();
            TaskEditorViewModel editor = new ViewModelProvider(activity,
                    new TaskEditorViewModel.Factory(container)).get(TaskEditorViewModel.class);
            editor.dispatch(TaskEditorAction.openNew());
            editor.dispatch(TaskEditorAction.draftChanged(
                    de.thonktank.autosecretary.editor.TaskEditorStateReducer.updateTitle(
                            editor.state().getValue().content, "Erhaltener Entwurf")));
            activity.getIntent().putExtra(MainActivity.OPEN_EDITOR, true);

            activity = controller.recreate().get();
            editor = new ViewModelProvider(activity, new TaskEditorViewModel.Factory(container))
                    .get(TaskEditorViewModel.class);

            assertEquals("Erhaltener Entwurf", editor.state().getValue().content.title);
            assertFalse(activity.getIntent().hasExtra(MainActivity.OPEN_EDITOR));
        }
    }

    @Test public void recreationDoesNotRepeatAConsumedConfirmationIntent() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent launch = new Intent(context, MainActivity.class)
                .putExtra(MainActivity.CONFIRM_TASK, "ongoing")
                .putExtra(MainActivity.CONFIRM_TASK_TITLE, "Praktikum");

        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class, launch)) {
            MainActivity activity = controller.setup().get();
            AlertDialog confirmation = ShadowAlertDialog.getLatestAlertDialog();

            assertNotNull(confirmation);
            ShadowAlertDialog shadowConfirmation = Shadow.extract(confirmation);
            assertEquals(activity.getString(R.string.close_task_title),
                    shadowConfirmation.getTitle());
            assertFalse(activity.getIntent().hasExtra(MainActivity.CONFIRM_TASK));
            assertFalse(activity.getIntent().hasExtra(MainActivity.CONFIRM_TASK_TITLE));

            ShadowAlertDialog.reset();
            controller.recreate();

            assertNull(ShadowAlertDialog.getLatestAlertDialog());
        }
    }

    @Test public void managementConfirmationRemainsVisibleAcrossRecreationUntilAcknowledged() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();
            AppContainer container = AutoSecretaryApplication.from(activity).container();
            AllTasksViewModel management = new ViewModelProvider(activity,
                    new AllTasksViewModel.Factory(container, destination -> { }))
                    .get(AllTasksViewModel.class);

            management.dispatch(AllTasksAction.deleteRequested(
                    TaskId.of("pending-delete"), "Offene Aufgabe"));
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

            AlertDialog first = ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(first);
            assertNotNull(management.state().getValue().firstRequest());

            ShadowAlertDialog.reset();
            activity = controller.recreate().get();
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

            AlertDialog recreated = ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(recreated);
            assertFalse(first == recreated);
            recreated.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
            assertNull(management.state().getValue().firstRequest());
        }
    }

    @Test public void validatorCoversEveryEditorConstraint() {
        TaskEditorValidator validator = new TaskEditorValidator();
        EditorUiState base = EditorUiState.create();
        assertTrue(validator.issues(base, LocalDate.of(2026, 8, 21))
                .contains(ValidationIssue.task(ValidationIssue.Field.TITLE)));
        EditorUiState weekdays = base.withDraft("Routine", TaskSlot.MORNING,
                Recurrence.WEEKDAYS, 1, 0, Collections.emptyList());
        assertTrue(validator.issues(weekdays, LocalDate.of(2026, 8, 21))
                .contains(ValidationIssue.task(ValidationIssue.Field.WEEKDAYS)));
        EditorUiState valid = base.withDraft("Vorhaben", TaskSlot.LATER,
                Recurrence.ONCE, 1, 0, Collections.emptyList());
        assertTrue(validator.issues(valid, LocalDate.of(2026, 8, 21)).isEmpty());
    }

    @Test public void rendererReusesTheMountedViewTreeForNormalUpdates() {
        Context context = ApplicationProvider.getApplicationContext();
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        DashboardRenderer renderer = new DashboardRenderer(context, scroll, content,
                event -> { }, action -> { }, "1.0", new RewardAnchorRegistry(),
                new AllTasksView.Listener() { });
        DayPalette morning = DayPalette.at(LocalTime.of(8, 0), DayPalette.Mode.AUTO);
        DashboardUiState first = state(morning);

        renderer.render(first, AllTasksUiState.empty());
        View focus = content.findViewById(R.id.dashboard_focus);
        focus.setFocusableInTouchMode(true);
        focus.requestFocus();

        renderer.render(state(DayPalette.at(LocalTime.of(8, 1), DayPalette.Mode.AUTO)),
                AllTasksUiState.empty());

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
        focus.bind(DashboardFixtures.taskWithSteps(), true,
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO), event -> { });
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

    @Test public void vesselRendersMultipliedResultAndLocalizedBreakdownForAllRanges() {
        Context context = ApplicationProvider.getApplicationContext();
        XpVesselView vessel = new XpVesselView(context);
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO);
        de.thonktank.autosecretary.presentation.today.RewardTextFormatter formatter =
                new de.thonktank.autosecretary.presentation.today.RewardTextFormatter(
                        java.util.Locale.GERMANY);
        vessel.setPalette(palette);

        vessel.bind(de.thonktank.autosecretary.presentation.today.XpVesselUiModel.of(
                de.thonktank.autosecretary.domain.model.RewardBreakdown.fromStage(15, 1),
                1, 3, false, formatter));
        assertEquals(23, vessel.renderedResult());
        assertEquals("15 × 1,5", vessel.renderedBreakdown());
        assertTrue(vessel.getContentDescription().toString().contains("23 XP erntbar"));
        assertTrue(vessel.getContentDescription().toString().contains("15 XP mal Faktor 1,5"));

        vessel.bind(de.thonktank.autosecretary.presentation.today.XpVesselUiModel.of(
                de.thonktank.autosecretary.domain.model.RewardBreakdown.fromStage(0, 5),
                0, 3, false, formatter));
        assertEquals(0, vessel.renderedResult());
        assertEquals("0 × 3,5", vessel.renderedBreakdown());

        vessel.bind(de.thonktank.autosecretary.presentation.today.XpVesselUiModel.of(
                de.thonktank.autosecretary.domain.model.RewardBreakdown.fromStage(25, 8),
                3, 3, true, formatter));
        assertEquals(125, vessel.renderedResult());
        assertEquals("25 × 5", vessel.renderedBreakdown());
        assertTrue(vessel.getContentDescription().toString().contains("125 XP erntbar"));
    }

    @Test public void headerCornerAndFocusDecorationShareTheirExactLeafGeometry() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        UiStyle style = new UiStyle(activity);
        DayPalette palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT);
        FrameLayout root = new FrameLayout(activity);
        activity.setContentView(root);
        HeaderView header = new HeaderView(activity, () -> { });
        root.addView(header, new FrameLayout.LayoutParams(style.dp(412), style.dp(82)));
        root.measure(View.MeasureSpec.makeMeasureSpec(style.dp(412), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(style.dp(82), View.MeasureSpec.EXACTLY));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
        header.bind(LocalTime.of(9, 40), palette,
                new de.thonktank.autosecretary.domain.model.XpProgress(70));
        ShadowLooper.shadowMainLooper().idle();
        LeafSurface headerLeaf = first(header, LeafSurface.class);
        assertNotNull(headerLeaf);
        assertEquals(new LeafShape(8, 56, 8, 56), headerLeaf.shape());

        FocusTaskView focus = new FocusTaskView(activity);
        focus.bind(DashboardFixtures.taskWithSteps(), false, palette, event -> { });
        focus.measure(View.MeasureSpec.makeMeasureSpec(style.dp(330), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(style.dp(800), View.MeasureSpec.EXACTLY));
        focus.layout(0, 0, focus.getMeasuredWidth(), focus.getMeasuredHeight());
        FocusCardView card = first(focus, FocusCardView.class);
        WoodGrainView focusGrain = first(focus, WoodGrainView.class);
        LeafSurface surface = first(focus, LeafSurface.class);
        assertNotNull(card);
        assertNotNull(focusGrain);
        assertNotNull(surface);
        assertEquals(new LeafShape(10, 64, 10, 64), surface.shape());
        assertSame(surface.front(), card.getParent());
        assertSame(surface, surface.front().getParent());
        assertEquals(0f, card.getRotation(), 0f);
        assertEquals(0f, focusGrain.getRotation(), 0f);
        assertEquals(-.7f, surface.getRotation(), 0f);
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
        vessel.setPalette(palette);
        vessel.bind(de.thonktank.autosecretary.presentation.today.XpVesselUiModel.of(
                de.thonktank.autosecretary.domain.model.RewardBreakdown.fromStage(30, 5),
                3, 3, true,
                new de.thonktank.autosecretary.presentation.today.RewardTextFormatter(
                        java.util.Locale.GERMANY)));
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

    @Test public void repetitionStepperRoundTripsThroughReducerBeforeRendering() {
        Context context = ApplicationProvider.getApplicationContext();
        UiStyle style = new UiStyle(context);
        FocusStepUiModel set = FocusTaskFixtures.step("set-step", "Beinpresse")
                .amount("3 × 12").note("23 kg")
                .repetition(RepetitionProgressUiModel.sets(
                        3, 12, Collections.singletonList(10))).combo(1).build();
        de.thonktank.autosecretary.presentation.today.FocusTaskUiModel task =
                FocusTaskFixtures.task("training", "Training")
                        .occurrence("training-today").slot(TaskSlot.MORNING)
                        .recurrence(Recurrence.DAILY).combo(2).rewardBase(5)
                        .steps(Collections.singletonList(set)).build();
        AtomicReference<RepetitionInputState> input =
                new AtomicReference<>(RepetitionInputState.idle());
        AtomicReference<RepetitionInputReducer.Submission> submitted = new AtomicReference<>();
        TodayUiModel dashboard = new TodayUiModel(
                new de.thonktank.autosecretary.domain.model.XpProgress(0),
                task, Collections.emptyList(), Collections.emptyList());
        RepetitionInputReducer reducer = new RepetitionInputReducer();
        FocusTaskView focus = new FocusTaskView(context);
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO);
        TodayActionSink events = event -> {
            RepetitionInputReducer.Result result = reducer.reduce(input.get(), dashboard, event);
            input.set(result.state);
            if (result.submission != null) submitted.set(result.submission);
        };
        focus.bind(task, false, palette, FocusStepLimit.AUTO,
                input.get(), events);
        focus.measure(View.MeasureSpec.makeMeasureSpec(style.dp(330), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(style.dp(600), View.MeasureSpec.AT_MOST));
        focus.layout(0, 0, focus.getMeasuredWidth(), focus.getMeasuredHeight());
        View plus = focus.findViewById(R.id.rep_stepper_increment);
        assertNotNull(plus);
        plus.performClick();
        assertEquals(13, input.get().valueFor(set));
        assertEquals("12", ((TextView) focus.findViewById(R.id.rep_stepper_value))
                .getText().toString());
        focus.bind(task, false, palette, FocusStepLimit.AUTO, input.get(), events);
        assertEquals("13", ((TextView) focus.findViewById(R.id.rep_stepper_value))
                .getText().toString());
        DewDotView dew = firstDew(focus);
        assertNotNull(dew);
        dew.performClick();
        assertEquals(13, submitted.get().value);
        assertFalse(submitted.get().correction());
        assertNull(input.get().stepId);

        focus.bind(task, false, palette, FocusStepLimit.AUTO,
                RepetitionInputState.idle(), events);
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
        assertEquals(0, submitted.get().editingIndex);
        assertEquals(11, submitted.get().value);
    }

    @Test public void completedStepsCollapseIntoTheDoneStatus() {
        Context context = ApplicationProvider.getApplicationContext();
        java.util.List<Integer> full = Arrays.asList(10, 11, 12);
        FocusStepUiModel set = FocusTaskFixtures.step("set-step", "Beinpresse")
                .amount("3 × 12").note("23 kg").done(true)
                .repetition(RepetitionProgressUiModel.sets(3, 12, full))
                .combo(1).earnedXp(15).build();
        FocusStepUiModel next = FocusStepUiModel.of("next", "Duschen", false);
        de.thonktank.autosecretary.presentation.today.FocusTaskUiModel task =
                FocusTaskFixtures.task("training", "Training")
                        .occurrence("training-today").slot(TaskSlot.MORNING)
                        .recurrence(Recurrence.DAILY).combo(2).rewardBase(5)
                        .harvestReady(true).steps(Arrays.asList(set, next)).build();
        FocusTaskView focus = new FocusTaskView(context);
        focus.bind(task, false, DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO),
                event -> { });
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

    private static <T extends View> T first(View root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                T match = first(group.getChildAt(index), type);
                if (match != null) return match;
            }
        }
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
                CalendarPermissionStatus.GRANTED, false, Collections.emptySet());
    }
}
