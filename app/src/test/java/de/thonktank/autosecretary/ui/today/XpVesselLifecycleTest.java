package de.thonktank.autosecretary.ui.today;

import static org.junit.Assert.*;
import android.app.Activity;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import de.thonktank.autosecretary.*;
import de.thonktank.autosecretary.presentation.today.*;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = {26, 35})
public final class XpVesselLifecycleTest {
    private ActivityController<Activity> host;
    private FrameLayout root;
    private final DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT);

    @Before public void setup() {
        ShadowChoreographer.setPaused(true);
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
        host = Robolectric.buildActivity(Activity.class).setup();
        root = new FrameLayout(host.get()); host.get().setContentView(root);
        motion(1f); window(true);
    }
    @After public void close() {
        host.pause().stop().destroy(); motion(1f); ShadowValueAnimator.reset(); ShadowChoreographer.reset();
    }

    @Test public void readyPulseRequiresAttachmentAndResumesWithoutRebinding() {
        XpVesselView vessel = ready();
        assertFalse("Binding an unattached vessel must not start an infinite animator", vessel.isPulsing());
        root.addView(vessel, new FrameLayout.LayoutParams(80, 80)); layout();
        assertTrue(visibility(vessel), vessel.isPulsing());
        root.removeView(vessel); assertFalse(vessel.isPulsing());
        root.addView(vessel, new FrameLayout.LayoutParams(80, 80)); layout();
        assertTrue("Reattachment resumes the same ready model", vessel.isPulsing());
    }

    @Test public void horizontalClippingStopsHiddenChainsAndScrollResumesThem() {
        FlowChainStripView strip = new FlowChainStripView(host.get());
        List<TodayAction> actions = new ArrayList<>();
        strip.bind(List.of(chain("first"), chain("second"), chain("third")), palette, actions::add);
        root.addView(strip, new FrameLayout.LayoutParams(new UiStyle(host.get()).dp(80), 180)); layout();
        XpVesselView first = vessel(strip, 0), third = vessel(strip, 2);
        assertTrue(visibility(first), first.isPulsing()); assertFalse("Clipped chain must not pulse", third.isPulsing());
        strip.scrollTo(strip.getChildAt(0).getWidth(), 0); scrollFrame();
        assertFalse(first.isPulsing()); assertTrue(third.isPulsing());
        strip.scrollTo(0, 0); scrollFrame();
        assertTrue(visibility(first), first.isPulsing()); assertFalse(third.isPulsing()); assertTrue(actions.isEmpty());
    }

    @Test public void ancestorScrollControlsOrdinaryVesselsToo() {
        ScrollView viewport = new ScrollView(host.get());
        LinearLayout content = new LinearLayout(host.get()); content.setOrientation(LinearLayout.VERTICAL);
        XpVesselView vessel = ready();
        content.addView(vessel, new LinearLayout.LayoutParams(80, 80));
        content.addView(new View(host.get()), new LinearLayout.LayoutParams(80, 500));
        viewport.addView(content); root.addView(viewport, new FrameLayout.LayoutParams(100, 100)); layout();
        assertTrue(visibility(vessel), vessel.isPulsing());
        viewport.scrollTo(0, 300); scrollFrame(); assertFalse(vessel.isPulsing());
        viewport.scrollTo(0, 0); scrollFrame(); assertTrue(visibility(vessel), vessel.isPulsing());
    }

    @Test public void hiddenParentAndWindowStopPulseAndRespectReducedMotionOnReturn() {
        XpVesselView vessel = ready(); root.addView(vessel, new FrameLayout.LayoutParams(80, 80)); layout();
        assertTrue(visibility(vessel), vessel.isPulsing());
        root.setVisibility(View.GONE); assertFalse(vessel.isPulsing());
        root.setVisibility(View.VISIBLE); layout(); assertTrue(visibility(vessel), vessel.isPulsing());
        window(false);
        assertFalse(vessel.isPulsing());
        motion(0f);
        window(true); layout();
        assertFalse(vessel.isPulsing()); assertTrue(vessel.isEnabled()); assertEquals(1f, vessel.fillFraction(), 0f);
    }

    @Test public void hiddenReadyContainerHasNoContinuingFrameInvalidations() {
        XpVesselView vessel = ready(); root.addView(vessel, new FrameLayout.LayoutParams(80, 80)); layout();
        shadow(vessel).clearWasInvalidated();
        frames(100);
        assertTrue("Visible ready container requests animation frames", shadow(vessel).wasInvalidated());
        root.setVisibility(View.INVISIBLE);
        frames(100);
        shadow(vessel).clearWasInvalidated();
        frames(100);
        assertFalse("Hidden ready container must not request recurring frames", shadow(vessel).wasInvalidated());
    }

    @Test public void countdownInvalidationsStopWithHiddenParentAndResumeWhenShown() {
        long now = 4_000_000;
        XpVesselView vessel = new XpVesselView(host.get(), () -> now);
        vessel.setPalette(palette);
        vessel.bindChain(FlowChainFixtures.chain("timer", "Buntwäsche", FlowChainUiModel.Mode.COUNTDOWN, now + 60_000));
        root.addView(vessel, new FrameLayout.LayoutParams(80, 80)); layout();
        assertFalse(vessel.isPulsing());
        shadow(vessel).clearWasInvalidated();
        frames(120);
        assertTrue("Visible clock requests a redraw", shadow(vessel).wasInvalidated());
        root.setVisibility(View.INVISIBLE); ShadowLooper.idleMainLooper();
        shadow(vessel).clearWasInvalidated();
        frames(1_100);
        assertFalse("Hidden clock has no repeating redraw", shadow(vessel).wasInvalidated());
        root.setVisibility(View.VISIBLE); layout(); shadow(vessel).clearWasInvalidated();
        frames(120);
        assertTrue(shadow(vessel).wasInvalidated());
    }

    @Test public void finiteFillAnimationRemainsIndependentOfReadyPulse() {
        XpVesselView vessel = new XpVesselView(host.get()); vessel.setPalette(palette);
        vessel.bind(FlowChainFixtures.chain("progress", "Wäsche", FlowChainUiModel.Mode.TAU, 4_000_000).vessel);
        root.addView(vessel, new FrameLayout.LayoutParams(80, 80)); layout();
        assertFalse(vessel.isPulsing());
        vessel.bind(chain("ready").vessel); assertTrue(vessel.isFillAnimating()); assertTrue(visibility(vessel), vessel.isPulsing());
        frames(palette.motion.vesselFillDurationMs + 100);
        assertFalse(vessel.isFillAnimating()); assertEquals(1f, vessel.fillFraction(), 0f);
    }

    private String visibility(View view) {
        android.graphics.Rect rect = new android.graphics.Rect();
        String result = view.getClass().getSimpleName() + ": attached=" + view.isAttachedToWindow()
                + " shown=" + view.isShown() + " window=" + view.getWindowVisibility()
                + " rect=" + view.getGlobalVisibleRect(rect) + ":" + rect
                + " dimensions=" + view.getWidth() + "x" + view.getHeight()
                + " motion=" + android.animation.ValueAnimator.areAnimatorsEnabled();
        return view.getParent() instanceof View ? result + "\n" + visibility((View) view.getParent()) : result;
    }

    private ShadowView shadow(View view) { return org.robolectric.shadow.api.Shadow.extract(view); }

    private FlowChainUiModel chain(String id) {
        return FlowChainFixtures.chain(id, "Wäsche", FlowChainUiModel.Mode.READY, 4_000_000);
    }
    private XpVesselView ready() {
        XpVesselView view = new XpVesselView(host.get()); view.setPalette(palette); view.bind(chain("ready").vessel);
        return view;
    }
    private XpVesselView vessel(FlowChainStripView strip, int index) {
        return (XpVesselView) ((ViewGroup) ((ViewGroup) strip.getChildAt(0)).getChildAt(index)).getChildAt(0);
    }
    private void layout() {
        ShadowLooper.idleMainLooper();
        View decor = host.get().getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 480, 800);
        // Deliver the framework traversal signal after the explicitly measured test viewport.
        root.getViewTreeObserver().dispatchOnGlobalLayout(); ShadowLooper.idleMainLooper();
    }
    private void scrollFrame() {
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(
                root.getViewTreeObserver(), "dispatchOnScrollChanged");
        ShadowLooper.idleMainLooper();
    }
    private void frames(long millis) {
        // Deliver each simulated vsync; one large clock jump is only one paused frame.
        for (long elapsed = 0; elapsed < millis; elapsed += 16)
            ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(Math.min(16, millis - elapsed)));
    }
    private void window(boolean visible) {
        // Plain Robolectric activities have no window-manager app-visibility event.
        // Deliver that framework event, preserving the real component/window predicate.
        Object viewRoot = host.get().getWindow().getDecorView().getParent();
        assertNotNull("Activity decor must have a window root", viewRoot);
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(viewRoot, "handleAppVisibility",
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(boolean.class, visible));
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(16));
    }
    private void motion(float scale) {
        Settings.Global.putFloat(host.get().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, scale);
    }
}
