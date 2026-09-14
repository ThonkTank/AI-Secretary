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
}
