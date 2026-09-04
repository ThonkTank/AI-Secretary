package de.thonktank.autosecretary;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import de.thonktank.autosecretary.domain.model.RewardBreakdown;
import de.thonktank.autosecretary.presentation.today.RewardTextFormatter;
import de.thonktank.autosecretary.presentation.today.XpVesselUiModel;
import de.thonktank.autosecretary.ui.leaf.GrainOcclusion;
import de.thonktank.autosecretary.ui.leaf.GrainSpec;
import de.thonktank.autosecretary.ui.leaf.LeafShape;
import de.thonktank.autosecretary.ui.leaf.LeafSurface;
import de.thonktank.autosecretary.ui.leaf.WoodGrainRenderPipeline;
import de.thonktank.autosecretary.ui.today.XpVesselView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowValueAnimator;

import java.io.File;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "mdpi")
public final class FocusRenderingGoldenRobolectricTest {
    @Test public void visibleTextLinesOwnOnlyTheirRenderedGrainArea() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        DayPalette palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT);
        LeafSurface leaf = new LeafSurface(activity, new LeafShape(12, 52, 12, 52));
        leaf.bindSurface(palette, palette.leaf1, palette.leaf1Edge, 0f, 0f);
        activity.setContentView(leaf, new FrameLayout.LayoutParams(420, 340));
        View anchor = new View(activity);
        leaf.front().addView(anchor, positioned(330, 135, 48, 48));

        List<GrainOcclusion> occlusions = new ArrayList<>();
        TextView shortLine = text(activity, palette, "Kurz");
        leaf.front().addView(shortLine, positioned(20, 18, 300, 40));
        occlusions.add(GrainOcclusion.text(shortLine));

        TextView weighted = text(activity, palette, "3 × 12");
        leaf.front().addView(weighted, positioned(20, 66, 300, 40));
        occlusions.add(GrainOcclusion.text(weighted));

        TextView blankLine = text(activity, palette, "Alpha\n\nBeta");
        leaf.front().addView(blankLine, positioned(20, 110, 150, 88));
        occlusions.add(GrainOcclusion.text(blankLine));

        TextView multiline = text(activity, palette, "Mehrzeiliger Text bricht sichtbar um");
        multiline.setMaxLines(3);
        leaf.front().addView(multiline, positioned(20, 205, 150, 105));
        occlusions.add(GrainOcclusion.text(multiline));

        TextView ellipsized = text(activity, palette,
                "Diese Zeile wird am sichtbaren Ende ellipsiert");
        ellipsized.setSingleLine(true);
        ellipsized.setEllipsize(TextUtils.TruncateAt.END);
        leaf.front().addView(ellipsized, positioned(205, 245, 155, 42));
        occlusions.add(GrainOcclusion.text(ellipsized));

        leaf.setGrainSpec(GrainSpec.anchors(Collections.singletonList(
                GrainSpec.sizedAnchor(anchor, 48f, 48f, 20)), occlusions));
        render("grain-text-cases", activity, leaf, 420, 340);
    }

    @Test public void vesselFillLevelsStayInsideTheirCircle() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Settings.Global.putFloat(activity.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        DayPalette palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT);
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(palette.background);
        activity.setContentView(root, new FrameLayout.LayoutParams(440, 120));
        RewardTextFormatter formatter = new RewardTextFormatter(Locale.GERMANY);
        for (int index = 0; index < 4; index++) {
            XpVesselView vessel = new XpVesselView(activity);
            vessel.setPalette(palette);
            vessel.bind(XpVesselUiModel.of(RewardBreakdown.fromStage(20, 0),
                    index == 0 ? 0 : index == 1 ? 1 : index == 2 ? 2 : 4,
                    4, false, formatter));
            root.addView(vessel, positioned(10 + index * 108, 10, 100, 100));
        }
        render("vessel-fill-levels", activity, root, 440, 120);
        Settings.Global.putFloat(activity.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        ShadowValueAnimator.reset();
    }

    private static TextView text(Activity activity, DayPalette palette, String value) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(18);
        text.setTextColor(palette.ink);
        return text;
    }

    private static FrameLayout.LayoutParams positioned(int left, int top, int width, int height) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = left;
        params.topMargin = top;
        return params;
    }

    private static void render(String name, Activity activity, View root,
                               int width, int height) throws Exception {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        ShadowLooper.shadowMainLooper().idle();
        WoodGrainRenderPipeline.awaitIdleForTest();
        ShadowLooper.shadowMainLooper().idle();
        Bitmap actual = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(actual));
        GoldenAssertions.compare(FocusRenderingGoldenRobolectricTest.class,
                "/golden/focus-rendering/" + name + ".png",
                new File("src/test/resources/golden/focus-rendering", name + ".png"),
                new File("build/reports/goldens/focus-rendering", name), actual,
                0, 0d, "UPDATE_FOCUS_RENDERING_GOLDENS");
        actual.recycle();
    }
}
