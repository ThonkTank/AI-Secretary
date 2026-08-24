package de.thonktank.autosecretary.ui.today;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
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
import de.thonktank.autosecretary.PresentationAwaiter;
import de.thonktank.autosecretary.PresentationTrace;
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
    private static final String TAG = "TodayGestureTest";

    private TodayInteractionHarnessActivity activity;
    private Harness currentHarness;
    private TouchGestureDriver currentGesture;
    private String currentGeometry = "geometry=not-initialized";

    @Before public void clearTrace() { PresentationTrace.clear(); }

    @Rule public final TestWatcher failureDiagnostics = new TestWatcher() {
        @Override protected void failed(Throwable error, Description description) {
            Log.e(TAG, "FAILED " + description.getMethodName() + "\n" + diagnostics(), error);
        }
    };

    @After public void closeActivity() {
        if (currentGesture != null) {
            try {
                // A system drag owns the pointer after long-press. Releasing the finger ends
                // that drag reliably on API 26; an injected ACTION_CANCEL can leave it stale.
                currentGesture.up();
            } catch (RuntimeException | AssertionError error) {
                Log.e(TAG, "Could not release the active test gesture", error);
            }
        }
        if (activity != null) activity.finish();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test public void longPressStartsReorder() {
        GestureScenario scenario = gestureScenario();

        scenario.beginReorder();

        assertTrue(scenario.harness.has(TodayAction.Kind.BEGIN_REORDER));
    }

    @Test public void dragPreviewsAndDropPersistsReorder() {
        GestureScenario scenario = gestureScenario();
        scenario.beginReorder();

        logPhase("move-to-row");
        scenario.gesture.moveTo(scenario.rowTarget);
        awaitCondition("Drag did not preview the reordered steps",
                () -> scenario.harness.has(TodayAction.Kind.PREVIEW_REORDER));

        logPhase("drop");
        scenario.gesture.up();
        awaitCondition("Drop did not persist the reordered steps",
                () -> scenario.harness.has(TodayAction.Kind.DROP_REORDER)
                        && !scenario.harness.commands.isEmpty());

        assertEquals(TodayCommand.Kind.PERSIST_REORDER,
                scenario.harness.commands.get(0).kind);
    }

    @Test public void holdingAtBottomEdgeScrolls() {
        GestureScenario scenario = gestureScenario();
        scenario.beginReorder();

        logPhase("move-to-bottom-edge");
        scenario.gesture.moveTo(scenario.bottomEdge);
        scenario.gesture.holdAtEdge(scenario.bottomEdge);
        awaitCondition("Holding the drag at the bottom edge did not scroll",
                () -> scenario.harness.scrollHost.distance != 0);

        assertTrue(scenario.harness.scrollHost.distance > 0);
    }

    @Test public void accessibilityReorderPersists() {
        Harness harness = mount();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        logPhase("accessibility-move-to-front");
        instrumentation.runOnMainSync(() -> {
            View secondBody = longClickable(harness.rows().get(1));
            assertNotNull(secondBody);
            assertTrue(secondBody.performAccessibilityAction(
                    R.id.action_today_step_front, null));
        });
        awaitCondition("Accessibility reorder did not persist",
                () -> harness.has(TodayAction.Kind.BEGIN_REORDER)
                        && harness.has(TodayAction.Kind.PREVIEW_REORDER)
                        && harness.has(TodayAction.Kind.DROP_REORDER)
                        && !harness.commands.isEmpty());

        assertEquals(TodayCommand.Kind.PERSIST_REORDER, harness.commands.get(0).kind);
    }

    @Test public void recreationCancelsActiveReorder() {
        Harness harness = mount();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        logPhase("begin-reorder-before-recreation");
        instrumentation.runOnMainSync(() -> harness.coordinator.emit(
                TodayAction.beginReorder("a", Arrays.asList("a", "b", "c"))));
        assertEquals(TodayFeatureState.Reorder.Phase.DRAGGING,
                harness.state.get().reorder.phase);

        logPhase("recreate-and-rebind");
        instrumentation.runOnMainSync(activity::recreate);
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> harness.coordinator.rebind(harness.today));
        awaitCondition("Recreation did not cancel the active reorder",
                () -> harness.state.get().reorder.phase
                        == TodayFeatureState.Reorder.Phase.IDLE
                        && harness.state.get().feedback
                        == TodayFeatureState.Feedback.REORDER_INTERRUPTED);

        assertEquals(TodayFeatureState.Feedback.REORDER_INTERRUPTED,
                harness.state.get().feedback);
    }

    private GestureScenario gestureScenario() {
        Harness harness = mount();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        AtomicReference<View> firstBody = new AtomicReference<>();
        AtomicReference<View> lastBody = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            List<FocusStepRowView> rows = harness.rows();
            firstBody.set(longClickable(rows.get(0)));
            lastBody.set(longClickable(rows.get(rows.size() - 1)));
        });
        assertNotNull(firstBody.get());
        assertNotNull(lastBody.get());

        Rect listBounds = awaitInteractiveBounds(harness.list);
        Rect sourceBounds = awaitInteractiveBounds(firstBody.get());
        Rect targetBounds = awaitInteractiveBounds(lastBody.get());
        int[] start = {sourceBounds.centerX(), sourceBounds.centerY()};
        int[] rowTarget = {targetBounds.centerX(), targetBounds.centerY()};
        int edgeInset = Math.max(2, Math.round(8f
                * activity.getResources().getDisplayMetrics().density));
        int[] bottomEdge = {start[0], listBounds.bottom - edgeInset};
        assertTrue(listBounds.contains(start[0], start[1]));
        assertTrue(listBounds.contains(rowTarget[0], rowTarget[1]));
        assertTrue(listBounds.contains(bottomEdge[0], bottomEdge[1]));

        TouchGestureDriver gesture = new TouchGestureDriver(instrumentation, firstBody.get());
        currentGesture = gesture;
        currentGeometry = "list=" + listBounds + " source=" + sourceBounds
                + " target=" + targetBounds + " start=" + point(start)
                + " rowTarget=" + point(rowTarget) + " bottomEdge=" + point(bottomEdge);
        return new GestureScenario(harness, gesture, start, rowTarget, bottomEdge);
    }

    private Harness mount() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(),
                TodayInteractionHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = (TodayInteractionHarnessActivity) instrumentation.startActivitySync(intent);
        Harness harness = new Harness(activity);
        currentHarness = harness;
        instrumentation.runOnMainSync(() -> {
            activity.setContentView(harness.list);
            harness.render();
        });
        instrumentation.waitForIdleSync();
        return harness;
    }

    private Rect awaitInteractiveBounds(View view) {
        Rect result = new Rect();
        awaitCondition("Target view never became interactive", () -> {
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
        });
        return result;
    }

    private void awaitCondition(String message, BooleanSupplier condition) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        PresentationAwaiter.await(instrumentation, message + "\n" + diagnostics(), condition,
                activity == null ? null : activity.getWindow().getDecorView(),
                currentHarness == null ? null : currentHarness.list);
    }

    private void logPhase(String phase) {
        Log.i(TAG, "PHASE " + phase + "\n" + diagnostics());
    }

    private String diagnostics() {
        String harness = currentHarness == null ? "harness=not-mounted"
                : currentHarness.describe();
        String gesture = currentGesture == null ? "gesture=not-created"
                : currentGesture.describe();
        return currentGeometry + "\n" + gesture + "\n" + harness
                + "\nPresentation trace:\n" + PresentationTrace.describe();
    }

    private static String point(int[] point) {
        return "(" + point[0] + "," + point[1] + ")";
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

    private final class GestureScenario {
        final Harness harness;
        final TouchGestureDriver gesture;
        final int[] start;
        final int[] rowTarget;
        final int[] bottomEdge;

        GestureScenario(Harness harness, TouchGestureDriver gesture, int[] start,
                        int[] rowTarget, int[] bottomEdge) {
            this.harness = harness;
            this.gesture = gesture;
            this.start = start;
            this.rowTarget = rowTarget;
            this.bottomEdge = bottomEdge;
        }

        void beginReorder() {
            logPhase("long-press");
            gesture.down(start);
            gesture.holdForLongPress();
            awaitCondition("Long press did not start step reordering",
                    () -> harness.has(TodayAction.Kind.BEGIN_REORDER)
                            && harness.state.get().reorder.phase
                            == TodayFeatureState.Reorder.Phase.DRAGGING);
        }
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

        String describe() {
            List<String> actionKinds = new ArrayList<>();
            for (TodayAction action : actions) actionKinds.add(action.kind.name());
            List<String> commandKinds = new ArrayList<>();
            for (TodayCommand command : commands) commandKinds.add(command.kind.name());
            TodayFeatureState value = state.get();
            return "actions=" + actionKinds + " commands=" + commandKinds
                    + " reorderPhase=" + value.reorder.phase
                    + " feedback=" + value.feedback
                    + " scrollDistance=" + scrollHost.distance;
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
