package de.thonktank.autosecretary;

import static org.junit.Assert.*;
import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.presentation.today.*;
import de.thonktank.autosecretary.ui.today.FocusTaskView;
import de.thonktank.autosecretary.ui.today.XpVesselView;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.ui.leaf.WoodGrainRenderPipeline;
import java.io.File;
import java.time.LocalTime;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;

/** The shared actionable sheet with compact waits, final Tau and no top-level vessel. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w320dp-h700dp-mdpi")
public final class GraphFlowSheetGoldenRobolectricTest {
    @Test public void actionableLaundryAndWaitsFitSmallScreensAndLargeText() throws Exception {
        AssertionError failure = null;
        for (float scale : new float[]{1f, 1.6f}) try { render(scale); }
        catch (AssertionError error) { if (failure == null) failure = error; }
        if (failure != null) throw failure;
    }

    private void render(float scale) throws Exception {
        var controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        Configuration original = new Configuration(activity.getResources().getConfiguration());
        Configuration changed = new Configuration(original); changed.fontScale = scale;
        activity.getResources().updateConfiguration(changed, activity.getResources().getDisplayMetrics());
        try {
            List<FocusStepUiModel> rows = List.of(
                    flowStep("white-hang", "Weißwäsche: Aufhängen", "", 0),
                    flowStep("towels-store", "Handtücher: Wegräumen", "30 Tau", 30));
            FocusTaskUiModel base = FocusTaskFixtures.task("laundry", "Wäsche")
                    .steps(rows).flowTaskSheet(true).allowDefer(true).build();
            TaskActionTarget target = TaskActionTarget.of("laundry",
                    TodayItemTarget.flowTaskSheet("laundry"), "Wäsche",
                    TaskSlot.MORNING, true, false);
            FocusTaskUiModel task = FocusTaskUiModel.builder(target).steps(rows, 2)
                    .allowDefer(true).allowBulkComplete(false).reward(base.reward, base.vessel)
                    .waits(List.of(new FlowWaitUiModel("colors", "colors-dry", "Buntwäsche: Aufhängen",
                                    System.currentTimeMillis() + 7_230_000),
                            new FlowWaitUiModel("white", "white-wash", "Weißwäsche", null, true))).build();
            DayPalette palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT);
            TodayActionRecorder events = new TodayActionRecorder();
            FocusTaskView view = new FocusTaskView(activity);
            view.bind(FocusCardTestModels.of(task, palette, FocusStepLimit.AUTO, RepetitionInputState.idle()),
                    false, events);
            FrameLayout root = new FrameLayout(activity); root.setBackgroundColor(palette.background);
            root.addView(view, new FrameLayout.LayoutParams(-1, -1)); activity.setContentView(root);
            root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 320, 700);
            ShadowLooper.shadowMainLooper().idle(); WoodGrainRenderPipeline.awaitIdleForTest();
            ShadowLooper.shadowMainLooper().idle();
            assertFalse(hasVisibleVessel(root));
            List<de.thonktank.autosecretary.ui.today.FocusStepRowView> rendered = new ArrayList<>();
            collectRows(root, rendered);
            assertEquals(2, rendered.size());
            assertTrue(rendered.get(1).rewardAnchor().performClick());
            assertEquals("towels-store", events.lastToday(TodayAction.Kind.COMPLETE_FLOW_STEP).id);
            assertNull(events.lastToday(TodayAction.Kind.ADVANCE_STEP));
            Bitmap bitmap = Bitmap.createBitmap(320, 700, Bitmap.Config.ARGB_8888);
            try {
                root.draw(new Canvas(bitmap));
                String name = scale == 1f ? "laundry-320" : "laundry-320-large";
                GoldenAssertions.compare(getClass(), "/golden/graph-flow-sheet/" + name + ".png",
                        new File("src/test/resources/golden/graph-flow-sheet", name + ".png"),
                        new File("build/reports/goldens/graph-flow-sheet", name), bitmap, 0, 0,
                        "UPDATE_GRAPH_FLOW_SHEET_GOLDENS");
            } finally { bitmap.recycle(); }
        } finally {
            activity.getResources().updateConfiguration(original, activity.getResources().getDisplayMetrics());
            controller.pause().stop().destroy();
        }
    }

    private boolean hasVisibleVessel(View view) {
        if (view.getVisibility() != View.VISIBLE) return false;
        if (view instanceof XpVesselView) return true;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++)
            if (hasVisibleVessel(group.getChildAt(i))) return true;
        return false;
    }

    private static FocusStepUiModel flowStep(String id, String title, String amount, int tau) {
        return FocusStepUiModel.executable(id, title, amount, "", false,
                StepExecutionUiAction.toggleFlowRunStep(id), null,
                RewardBreakdown.fromStage(tau, 0), 0);
    }

    private static void collectRows(View view, List<de.thonktank.autosecretary.ui.today.FocusStepRowView> rows) {
        if (view instanceof de.thonktank.autosecretary.ui.today.FocusStepRowView row) rows.add(row);
        else if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++)
            collectRows(group.getChildAt(i), rows);
    }
}
