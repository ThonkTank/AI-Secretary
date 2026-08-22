package de.thonktank.autosecretary.ui.today;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.RepetitionInputState;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.domain.model.RewardBreakdown;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.XpProgress;
import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.RewardTextFormatter;
import de.thonktank.autosecretary.presentation.today.TaskActionTarget;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayCommand;
import de.thonktank.autosecretary.presentation.today.TodayCoordinator;
import de.thonktank.autosecretary.presentation.today.TodayFeatureState;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.presentation.today.XpVesselUiModel;

@RunWith(AndroidJUnit4.class)
public final class TodayInteractionInstrumentationTest {
    private static final long UI_TIMEOUT_MILLIS = 5_000L;
    private static final long UI_POLL_MILLIS = 16L;

    private TodayInteractionHarnessActivity activity;

    @After public void closeActivity() {
        if (activity != null) activity.finish();
    }

    @Test public void realLongPressDragDropAndEdgeScrollUseTheTodayStateMachine() {
        Harness harness = mount();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        AtomicReference<View> firstBody = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            firstBody.set(longClickable(harness.rows().get(0)));
        });
        assertNotNull(firstBody.get());

        Rect listBounds = awaitInteractiveBounds(instrumentation, harness.list);
        Rect sourceBounds = awaitInteractiveBounds(instrumentation, firstBody.get());
        int[] start = new int[]{sourceBounds.centerX(), sourceBounds.centerY()};
        int edgeInset = Math.max(2, Math.round(32f
                * activity.getResources().getDisplayMetrics().density));
        int[] end = new int[]{start[0], listBounds.bottom - edgeInset};
        assertTrue(listBounds.contains(start[0], start[1]));
        assertTrue(listBounds.contains(end[0], end[1]));

        long down = SystemClock.uptimeMillis();
        sendPointer(instrumentation, down, down, MotionEvent.ACTION_DOWN, start);
        awaitCondition(instrumentation, "Long press did not start step reordering",
                () -> harness.has(TodayAction.Kind.BEGIN_REORDER),
                ViewConfiguration.getLongPressTimeout() + UI_TIMEOUT_MILLIS);

        long move = SystemClock.uptimeMillis();
        sendPointer(instrumentation, down, move, MotionEvent.ACTION_MOVE, end);
        awaitCondition(instrumentation, "Drag did not preview and edge-scroll",
                () -> harness.has(TodayAction.Kind.PREVIEW_REORDER)
                        && harness.scrollHost.distance != 0,
                UI_TIMEOUT_MILLIS);

        long up = SystemClock.uptimeMillis();
        sendPointer(instrumentation, down, up, MotionEvent.ACTION_UP, end);
        awaitCondition(instrumentation, "Drop did not persist the reordered steps",
                () -> harness.has(TodayAction.Kind.DROP_REORDER)
                        && !harness.commands.isEmpty(), UI_TIMEOUT_MILLIS);

        assertTrue(harness.has(TodayAction.Kind.BEGIN_REORDER));
        assertTrue(harness.has(TodayAction.Kind.PREVIEW_REORDER));
        assertTrue(harness.has(TodayAction.Kind.DROP_REORDER));
        assertTrue(harness.scrollHost.distance != 0);
        assertEquals(TodayCommand.Kind.PERSIST_REORDER,
                harness.commands.get(0).kind);
    }

    @Test public void accessibilityAndRecreationShareTheSameReorderContract() {
        Harness harness = mount();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(() -> {
            View secondBody = longClickable(harness.rows().get(1));
            assertNotNull(secondBody);
            assertTrue(secondBody.performAccessibilityAction(
                    R.id.action_today_step_front, null));
        });
        assertTrue(harness.has(TodayAction.Kind.BEGIN_REORDER));
        assertTrue(harness.has(TodayAction.Kind.PREVIEW_REORDER));
        assertTrue(harness.has(TodayAction.Kind.DROP_REORDER));

        activity.finish();
        instrumentation.waitForIdleSync();
        Harness recreation = mount();
        recreation.coordinator.emit(TodayAction.beginReorder("a",
                Arrays.asList("a", "b", "c")));
        assertEquals(TodayFeatureState.Reorder.Phase.DRAGGING,
                recreation.state.get().reorder.phase);
        instrumentation.runOnMainSync(activity::recreate);
        instrumentation.waitForIdleSync();
        recreation.coordinator.rebind(recreation.today);

        assertEquals(TodayFeatureState.Reorder.Phase.IDLE,
                recreation.state.get().reorder.phase);
        assertEquals(TodayFeatureState.Feedback.REORDER_INTERRUPTED,
                recreation.state.get().feedback);
    }

    private Harness mount() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(),
                TodayInteractionHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = (TodayInteractionHarnessActivity) instrumentation.startActivitySync(intent);
        Harness harness = new Harness(activity);
        instrumentation.runOnMainSync(() -> {
            activity.setContentView(harness.list);
            harness.render();
        });
        instrumentation.waitForIdleSync();
        return harness;
    }

    private Rect awaitInteractiveBounds(Instrumentation instrumentation, View view) {
        Rect result = new Rect();
        awaitCondition(instrumentation, "Target view never became interactive", () -> {
            Rect visible = new Rect();
            Rect window = new Rect();
            View decor = activity.getWindow().getDecorView();
            decor.getWindowVisibleDisplayFrame(window);
            if (!activity.hasWindowFocus() || !view.isAttachedToWindow() || !view.isShown()
                    || !view.getGlobalVisibleRect(visible)
                    || window.isEmpty() || !visible.intersect(window)
                    || visible.width() < 3 || visible.height() < 3) return false;
            result.set(visible);
            return true;
        }, UI_TIMEOUT_MILLIS);
        return result;
    }

    private static void sendPointer(Instrumentation instrumentation, long downTime,
                                    long eventTime, int action, int[] location) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action,
                location[0], location[1], 0);
        try {
            UiAutomation automation = instrumentation.getUiAutomation();
            assertNotNull("UI automation is required for system drag injection", automation);
            assertTrue("Pointer event injection failed",
                    automation.injectInputEvent(event, true));
        } finally {
            event.recycle();
        }
    }

    private static void awaitCondition(Instrumentation instrumentation, String message,
                                       BooleanSupplier condition, long timeoutMillis) {
        long deadline = SystemClock.uptimeMillis() + timeoutMillis;
        AtomicReference<Boolean> matched = new AtomicReference<>(false);
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> matched.set(condition.getAsBoolean()));
            if (matched.get()) return;
            SystemClock.sleep(UI_POLL_MILLIS);
        }
        fail(message);
    }

    private static View longClickable(View view) {
        if (view.isLongClickable()) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            View result = longClickable(group.getChildAt(index));
            if (result != null) return result;
        }
        return null;
    }

    private static final class Harness {
        final FocusTaskUiModel focus;
        final TodayUiModel today;
        final List<TodayAction> actions = new ArrayList<>();
        final List<TodayCommand> commands = new ArrayList<>();
        final AtomicReference<TodayFeatureState> state = new AtomicReference<>();
        final RecordingScrollHost scrollHost;
        final FocusStepListLayout list;
        final TodayCoordinator coordinator;

        Harness(TodayInteractionHarnessActivity activity) {
            focus = focus();
            today = new TodayUiModel(new XpProgress(0), focus,
                    Collections.emptyList(), Collections.emptyList());
            coordinator = new TodayCoordinator(today, commands::add, state::set);
            state.set(coordinator.state());
            scrollHost = new RecordingScrollHost(activity.getWindow().getDecorView());
            list = new FocusStepListLayout(activity, action -> {
                actions.add(action);
                coordinator.emit(action);
                render();
            }, scrollHost);
        }

        void render() {
            TodayFeatureState value = state.get();
            list.bind(new FocusCardUiModel(value.today.focus,
                    DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT),
                    FocusStepLimit.AUTO, RepetitionInputState.idle(), value.reorder));
        }

        List<FocusStepRowView> rows() {
            List<FocusStepRowView> result = new ArrayList<>();
            for (int index = 0; index < list.getChildCount(); index++)
                if (list.getChildAt(index) instanceof FocusStepRowView)
                    result.add((FocusStepRowView) list.getChildAt(index));
            return result;
        }

        boolean has(TodayAction.Kind kind) {
            for (TodayAction action : actions) if (action.kind == kind) return true;
            return false;
        }
    }

    private static FocusTaskUiModel focus() {
        RewardBreakdown reward = RewardBreakdown.fromStage(30, 0);
        List<FocusStepUiModel> steps = Arrays.asList(
                FocusStepUiModel.of("a", "Erster Schritt", false),
                FocusStepUiModel.of("b", "Zweiter Schritt", false),
                FocusStepUiModel.of("c", "Dritter Schritt", false));
        XpVesselUiModel vessel = XpVesselUiModel.of(reward, 0, 3, false,
                new RewardTextFormatter(Locale.GERMANY));
        return FocusTaskUiModel.builder(TaskActionTarget.of("task", "occurrence", "Routine",
                        TaskSlot.MORNING, true, false))
                .nextAction("Routine fortsetzen")
                .steps(steps, 3)
                .ongoing(true)
                .allowDefer(true)
                .reward(reward, vessel)
                .build();
    }

    private static final class RecordingScrollHost implements EdgeAutoScroller.ScrollHost {
        private final View scheduler;
        int distance;

        RecordingScrollHost(View scheduler) { this.scheduler = scheduler; }

        @Override public void scrollBy(int dy) { distance += dy; }
        @Override public void postOnAnimation(Runnable frame) {
            scheduler.postOnAnimation(frame);
        }
        @Override public void removeCallbacks(Runnable frame) {
            scheduler.removeCallbacks(frame);
        }
    }
}
