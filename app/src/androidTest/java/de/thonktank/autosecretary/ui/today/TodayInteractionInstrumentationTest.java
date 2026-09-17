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
import de.thonktank.autosecretary.presentation.today.TodayItemTarget;
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
        if (activity != null) {
            activity.finish();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }

    @Test public void titleTouchAndAccessibleLaterChangeActualFocusAndPersistOnRecreation() {
        new TodayProductInteractionScenario().execute();
    }

    @Test public void hiddenFlowReappearsAtDeadlineInTheProductActivity() {
        new FlowDeadlineProductScenario().execute(false);
    }

    @Test public void hiddenFlowReappearsWhenTheProductReturnsAfterDeadline() {
        new FlowDeadlineProductScenario().execute(true);
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

    @Test public void flowContainersSwipeWithoutCollectingAndTapTheCorrectRun() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(),
                TodayInteractionHarnessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = (TodayInteractionHarnessActivity) instrumentation.startActivitySync(intent);
        AtomicReference<FlowChainStripView> mounted = new AtomicReference<>();
        List<TodayAction> recorded = new java.util.concurrent.CopyOnWriteArrayList<>();
        instrumentation.runOnMainSync(() -> {
            FlowChainStripView strip = new FlowChainStripView(activity);
            strip.setOnTouchListener((view, event) -> {
                Log.i(TAG, "flow-scroll touch=" + event.getActionMasked() + " scroll=" + strip.getScrollX());
                return false;
            });
            strip.bind(Arrays.asList(
                    de.thonktank.autosecretary.FlowChainFixtures.chain("first", "Wäsche",
                            de.thonktank.autosecretary.presentation.today.FlowChainUiModel.Mode.READY, 4_000_000),
                    de.thonktank.autosecretary.FlowChainFixtures.chain("second", "Wäsche",
                            de.thonktank.autosecretary.presentation.today.FlowChainUiModel.Mode.READY, 4_000_000),
                    de.thonktank.autosecretary.FlowChainFixtures.chain("third", "Wäsche",
                            de.thonktank.autosecretary.presentation.today.FlowChainUiModel.Mode.READY, 4_000_000)),
                    DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT), action -> {
                        recorded.add(action);
                        PresentationTrace.emit("flow-test", "action", action.kind.name());
                    });
            android.widget.FrameLayout root = new android.widget.FrameLayout(activity);
            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                    new de.thonktank.autosecretary.UiStyle(activity).dp(100), -2);
            params.topMargin = new de.thonktank.autosecretary.UiStyle(activity).dp(80);
            root.addView(strip, params); activity.setContentView(root); mounted.set(strip);
        });
        FlowChainStripView strip = mounted.get(); Rect bounds = awaitInteractiveBounds(strip);
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            instrumentation.runOnMainSync(() -> {
                ViewGroup first = (ViewGroup) ((ViewGroup) strip.getChildAt(0)).getChildAt(0);
                assertTrue("Ready vessels must keep their real animation during the gesture",
                        ((XpVesselView) first.getChildAt(0)).isPulsing());
            });
        }
        logPhase("flow-drag");
        currentGesture = new TouchGestureDriver(instrumentation, strip);
        currentGesture.down(new int[]{bounds.right - 8, bounds.centerY()});
        currentGesture.moveTo(new int[]{bounds.left + 8, bounds.centerY()});
        currentGesture.settleDragVelocity(); currentGesture.up();
        // Ready vessels pulse forever. Await the outcome, never global main-loop idleness.
        awaitCondition("The native drag must actually scroll", () -> strip.getScrollX() > 0);
        assertTrue("Swiping must not collect", recorded.isEmpty());
        AtomicReference<View> last = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            strip.scrollTo(strip.getChildAt(0).getWidth(), 0);
            last.set(((ViewGroup) strip.getChildAt(0)).getChildAt(2));
            last.get().setOnTouchListener((view, event) -> {
                Log.i(TAG, "flow-container touch=" + event.getActionMasked() + " clickable=" + view.isClickable());
                return false;
            });
        });
        Rect target = awaitInteractiveBounds(last.get());
        logPhase("flow-collect");
        currentGesture.tap(new int[]{target.centerX(), target.centerY()});
        awaitCondition("Tapping the last container must collect", () -> !recorded.isEmpty());
        assertEquals(1, recorded.size()); assertEquals(TodayAction.Kind.COLLECT_FLOW, recorded.get(0).kind);
        assertEquals("third", recorded.get(0).id);
    }


    @Test public void readyFlowLongPressAndAccessibleWaitEditNeverCollect() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        activity = (TodayInteractionHarnessActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), TodayInteractionHarnessActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        AtomicReference<View> target = new AtomicReference<>();
        List<TodayAction> recorded = new java.util.concurrent.CopyOnWriteArrayList<>();
        instrumentation.runOnMainSync(() -> {
            FlowChainStripView strip = new FlowChainStripView(activity, () -> 1_000_000L);
            strip.bind(List.of(de.thonktank.autosecretary.FlowChainFixtures.editableReadyChain("ready")),
                    DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT), recorded::add);
            activity.setContentView(strip);
            target.set(((ViewGroup) strip.getChildAt(0)).getChildAt(0));
        });
        Rect bounds = awaitInteractiveBounds(target.get());
        currentGesture = new TouchGestureDriver(instrumentation, target.get());
        accessibleAfter(() -> {
            currentGesture.down(new int[]{bounds.centerX(), bounds.centerY()});
            currentGesture.holdForLongPress(); currentGesture.up();
        }, node -> "android.widget.EditText".contentEquals(node.getClassName()));
        assertTrue(recorded.isEmpty());
        assertTrue(currentAccessibleNode(node -> activity.getString(android.R.string.cancel)
                .equalsIgnoreCase(String.valueOf(node.getText()))).performAction(
                android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));
        awaitCondition("Cancelled dialog must restore the container window", activity::hasWindowFocus);
        var input = accessibleAfter(() -> instrumentation.runOnMainSync(() -> {
            var info = target.get().createAccessibilityNodeInfo();
            assertTrue(info.getActionList().stream().anyMatch(action -> action.getId()
                    == android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK
                    && activity.getString(R.string.flow_chain_adjust).contentEquals(action.getLabel())));
            assertTrue(target.get().performAccessibilityAction(
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK, null));
        }), node -> "android.widget.EditText".contentEquals(node.getClassName()));
        android.os.Bundle value = new android.os.Bundle();
        value.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "3");
        assertTrue(input.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, value));
        assertTrue(currentAccessibleNode(node -> activity.getString(R.string.flow_delay_confirm)
                .equalsIgnoreCase(String.valueOf(node.getText()))).performAction(
                android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));
        awaitCondition("Accessible confirmation must emit one wait change", () -> recorded.size() == 1);
        assertEquals(TodayAction.Kind.ADJUST_FLOW_WAIT, recorded.get(0).kind);
        assertEquals("ready", recorded.get(0).id);
        assertEquals(de.thonktank.autosecretary.FlowChainFixtures.editableReadyChain("ready")
                .editableWaits.get(0).waitId, recorded.get(0).relatedId);
        assertEquals(1_180_000L, recorded.get(0).longValue);
    }

    private android.view.accessibility.AccessibilityNodeInfo accessibleAfter(Runnable action,
            java.util.function.Predicate<android.view.accessibility.AccessibilityNodeInfo> match) {
        var automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        AtomicReference<android.view.accessibility.AccessibilityNodeInfo> result = new AtomicReference<>();
        try {
            automation.executeAndWaitForEvent(action, event -> {
                result.set(accessibleNode(automation.getRootInActiveWindow(), match));
                return result.get() != null;
            }, 5_000);
        } catch (java.util.concurrent.TimeoutException timeout) {
            throw new AssertionError("Dialog did not emit the expected accessibility state\n" + diagnostics(), timeout);
        }
        return result.get();
    }

    private android.view.accessibility.AccessibilityNodeInfo currentAccessibleNode(
            java.util.function.Predicate<android.view.accessibility.AccessibilityNodeInfo> match) {
        var node = accessibleNode(InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .getRootInActiveWindow(), match);
        assertNotNull("Expected action in the accessible dialog", node);
        return node;
    }

    private android.view.accessibility.AccessibilityNodeInfo accessibleNode(
            android.view.accessibility.AccessibilityNodeInfo node,
            java.util.function.Predicate<android.view.accessibility.AccessibilityNodeInfo> match) {
        if (node == null) return null;
        if (node.isVisibleToUser() && match.test(node)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            var found = accessibleNode(node.getChild(i), match);
            if (found != null) return found;
        }
        return null;
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
            boolean hasVisibleRect = view.getGlobalVisibleRect(visible);
            Rect clipped = new Rect(visible);
            boolean intersectsWindow = !window.isEmpty() && hasVisibleRect
                    && clipped.intersect(window);
            currentGeometry = "pending windowFocus=" + activity.hasWindowFocus()
                    + " attached=" + view.isAttachedToWindow()
                    + " shown=" + view.isShown()
                    + " visible=" + visible + " window=" + window
                    + " clipped=" + clipped;
            if (!activity.hasWindowFocus() || !view.isAttachedToWindow() || !view.isShown()
                    || !hasVisibleRect || !intersectsWindow
                    || clipped.width() < 3 || clipped.height() < 3) return false;
            result.set(clipped);
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
            list.bind(new FocusCardUiModel(value.today.focus, value.focus,
                    DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT),
                    FocusStepLimit.AUTO, RepetitionInputState.idle(), value.reorder,
                    de.thonktank.autosecretary.timer.TimerManager.Snapshot.empty()));
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
        return FocusTaskUiModel.builder(TaskActionTarget.of("task",
                        TodayItemTarget.occurrence("occurrence"), "Routine",
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
