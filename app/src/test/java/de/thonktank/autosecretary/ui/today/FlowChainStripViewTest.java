package de.thonktank.autosecretary.ui.today;

import static org.junit.Assert.*;
import android.app.Activity;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import java.time.LocalTime;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import de.thonktank.autosecretary.*;
import de.thonktank.autosecretary.presentation.today.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public final class FlowChainStripViewTest {
    @Test public void sameTitleKeepsRunIdentityAndSwipeDoesNotCollect() {
        var controller = Robolectric.buildActivity(Activity.class).setup();
        try {
            Activity activity = controller.get(); List<TodayAction> actions = new ArrayList<>();
            FlowChainStripView strip = new FlowChainStripView(activity);
            strip.bind(List.of(FlowChainFixtures.chain("first", "Wäsche", FlowChainUiModel.Mode.READY, 4_000_000),
                    FlowChainFixtures.chain("second", "Wäsche", FlowChainUiModel.Mode.READY, 4_000_000)),
                    DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT), actions::add);
            activity.setContentView(strip);
            strip.measure(View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.AT_MOST));
            strip.layout(0, 0, 80, strip.getMeasuredHeight());
            strip.scrollTo(80, 0); assertTrue(actions.isEmpty());
            ViewGroup content = (ViewGroup) strip.getChildAt(0);
            assertFalse(content.getChildAt(1).isLongClickable());
            content.getChildAt(1).performClick();
            assertEquals(1, actions.size()); assertEquals(TodayAction.Kind.COLLECT_FLOW, actions.get(0).kind);
            assertEquals("second", actions.get(0).id);
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void waitingContainerOpensExistingDurationDialogAndCancelEmitsNothing() {
        var controller = Robolectric.buildActivity(Activity.class).setup();
        try {
            Activity activity = controller.get(); List<TodayAction> actions = new ArrayList<>();
            FlowChainStripView strip = new FlowChainStripView(activity);
            strip.bind(List.of(FlowChainFixtures.chain("wait", "Buntwäsche", FlowChainUiModel.Mode.COUNTDOWN,
                    System.currentTimeMillis() + 60_000)), DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT), actions::add);
            activity.setContentView(strip);
            View group = ((ViewGroup) strip.getChildAt(0)).getChildAt(0);
            assertTrue(group.performClick());
            assertNotNull(ShadowAlertDialog.getLatestAlertDialog());
            ShadowAlertDialog.getLatestAlertDialog().cancel(); assertTrue(actions.isEmpty());
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void confirmingSingleWaitUsesConfirmationTimeAndPersistsOnlyThatWait() {
        withPersistedStrip((fixture, strip) -> {
            fixture.start(false); bind(fixture, strip);
            FlowWaitUiModel wait = fixture.chains().get(0).editableWaits.get(0);
            group(strip, 0).performClick();
            fixture.now += 5_000; // Dialog-open time must not shorten the selected duration.
            confirmMinutes(7);
            assertEquals(1, fixture.actions.size());
            TodayAction action = fixture.actions.get(0);
            assertEquals(wait.runId, action.id); assertEquals(wait.waitId, action.relatedId);
            assertEquals(fixture.now + 420_000, action.longValue);
            assertEquals(Long.valueOf(action.longValue), fixture.deadlines().get(wait.runId + "/" + wait.waitId));
            assertEquals(0, fixture.xp());
        });
    }

    @Test public void selectingSecondWaitOfSameNamedRunPreservesAllOtherDeadlines() {
        withPersistedStrip((fixture, strip) -> {
            fixture.start(true); fixture.start(true); bind(fixture, strip);
            var before = fixture.deadlines();
            FlowWaitUiModel selected = fixture.chains().get(1).editableWaits.get(1);
            group(strip, 1).performClick();
            android.app.AlertDialog chooser = ShadowAlertDialog.getLatestAlertDialog();
            assertEquals(2, chooser.getListView().getCount());
            chooser.getListView().performItemClick(null, 1, 1);
            confirmMinutes(9);
            before.put(selected.runId + "/" + selected.waitId, fixture.now + 540_000);
            assertEquals(before, fixture.deadlines());
            assertEquals(1, fixture.actions.size()); assertEquals(0, fixture.xp());
        });
    }

    @Test public void cancellingChooserAndChosenDurationLeavesDatabaseUntouched() {
        withPersistedStrip((fixture, strip) -> {
            fixture.start(true); bind(fixture, strip); var before = fixture.deadlines();
            group(strip, 0).performClick(); ShadowAlertDialog.getLatestAlertDialog().cancel();
            assertEquals(before, fixture.deadlines()); assertTrue(fixture.actions.isEmpty());
            group(strip, 0).performClick();
            ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null, 1, 1);
            descendant(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(), EditText.class).setText("17");
            ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing());
            assertEquals(before, fixture.deadlines()); assertTrue(fixture.actions.isEmpty());
            assertEquals(0, fixture.xp());
        });
    }

    @Test public void readyContainerLongClickAndNamedAccessibleActionEditWithoutCollecting() {
        withPersistedStrip((fixture, strip) -> {
            fixture.start(false); fixture.expire(); bind(fixture, strip);
            assertEquals(FlowChainUiModel.Mode.READY, fixture.chains().get(0).mode);
            View target = group(strip, 0);
            assertTrue(target.performLongClick());
            ShadowAlertDialog.getLatestAlertDialog().cancel();
            assertTrue(fixture.actions.isEmpty());
            var info = target.createAccessibilityNodeInfo();
            assertTrue(info.getActionList().stream().anyMatch(action -> action.getId()
                    == android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK
                    && target.getContext().getString(R.string.flow_chain_adjust).contentEquals(action.getLabel())));
            assertTrue(target.performAccessibilityAction(
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK, null));
            confirmMinutes(3);
            assertEquals(1, fixture.actions.size()); assertEquals(0, fixture.xp());
            assertEquals(FlowChainUiModel.Mode.COUNTDOWN, fixture.chains().get(0).mode);
        });
    }

    private void withPersistedStrip(java.util.function.BiConsumer<FlowWaitPersistenceFixture, FlowChainStripView> test) {
        var controller = Robolectric.buildActivity(Activity.class).setup();
        try (var fixture = new FlowWaitPersistenceFixture()) {
            FlowChainStripView strip = new FlowChainStripView(controller.get(), () -> fixture.now);
            controller.get().setContentView(strip); test.accept(fixture, strip);
        } finally { controller.pause().stop().destroy(); }
    }

    private void bind(FlowWaitPersistenceFixture fixture, FlowChainStripView strip) {
        strip.bind(fixture.chains(), DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT), fixture::accept);
    }
    private View group(FlowChainStripView strip, int index) {
        return ((ViewGroup) strip.getChildAt(0)).getChildAt(index);
    }
    private void confirmMinutes(int minutes) {
        android.app.AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        // Dispatch the queued onShow callback at the current virtual time only.
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        View root = dialog.getWindow().getDecorView();
        descendant(root, EditText.class).setText(String.valueOf(minutes));
        descendant(root, Spinner.class).setSelection(0);
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(dialog.isShowing());
    }
    private static <T extends View> T descendant(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            T match = descendant(((ViewGroup) view).getChildAt(i), type);
            if (match != null) return match;
        }
        return null;
    }

}
