package de.thonktank.autosecretary.ui.today;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Intent;
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
    private TodayInteractionHarnessActivity activity;

    @After public void closeActivity() {
        if (activity != null) activity.finish();
    }

    @Test public void realLongPressDragDropAndEdgeScrollUseTheTodayStateMachine() {
        Harness harness = mount();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        AtomicReference<View> firstBody = new AtomicReference<>();
        AtomicReference<View> lastBody = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            firstBody.set(longClickable(harness.rows().get(0)));
            lastBody.set(longClickable(harness.rows().get(2)));
        });
        assertNotNull(firstBody.get());
        assertNotNull(lastBody.get());

        int[] start = centerOnScreen(firstBody.get());
        int[] end = centerOnScreen(lastBody.get());
        long down = SystemClock.uptimeMillis();
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down,
                MotionEvent.ACTION_DOWN, start[0], start[1], 0));
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 100L);
        long move = SystemClock.uptimeMillis();
        instrumentation.sendPointerSync(MotionEvent.obtain(down, move,
                MotionEvent.ACTION_MOVE, end[0], end[1], 0));
        SystemClock.sleep(150L);
        long up = SystemClock.uptimeMillis();
        instrumentation.sendPointerSync(MotionEvent.obtain(down, up,
                MotionEvent.ACTION_UP, end[0], end[1], 0));
        instrumentation.waitForIdleSync();

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

    private static int[] centerOnScreen(View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return new int[]{location[0] + view.getWidth() / 2,
                location[1] + view.getHeight() / 2};
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
