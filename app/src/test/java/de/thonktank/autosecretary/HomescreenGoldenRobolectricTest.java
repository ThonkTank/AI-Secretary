package de.thonktank.autosecretary;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class HomescreenGoldenRobolectricTest {
    @Test
    @Config(sdk = 35, qualifiers = "w412dp-h892dp-xhdpi")
    public void renderPhoneReferenceStates() throws Exception {
        AssertionError mismatch = null;
        for (String state : new String[]{"full", "step", "later", "complete", "evening", "empty",
                "empty-vessel", "partial-vessel", "harvest-ready", "harvested",
                "three-digit", "night"}) {
            try {
                render(state, 824, 1784, true);
            } catch (AssertionError error) {
                if (mismatch == null) mismatch = error;
                else mismatch.addSuppressed(error);
            }
        }
        if (mismatch != null) throw mismatch;
    }

    @Test
    @Config(sdk = 35, qualifiers = "xxhdpi")
    public void renderWidgetReferenceStates() throws Exception {
        AssertionError mismatch = null;
        for (WidgetGolden golden : new WidgetGolden[]{
                new WidgetGolden("widget-small", 480, 480),
                new WidgetGolden("widget-wide", 1032, 480),
                new WidgetGolden("widget-large", 1032, 1032),
                new WidgetGolden("widget-tall", 840, 1032)}) {
            try {
                render(golden.name, golden.width, golden.height, false);
            } catch (AssertionError error) {
                if (mismatch == null) mismatch = error;
                else mismatch.addSuppressed(error);
            }
        }
        if (mismatch != null) throw mismatch;
    }

    private static final class WidgetGolden {
        final String name;
        final int width;
        final int height;

        WidgetGolden(String name, int width, int height) {
            this.name = name;
            this.width = width;
            this.height = height;
        }
    }

    private static void render(String preview, int width, int height,
                               boolean emulateSystemInsets) throws Exception {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .setClassName("de.thonktank.autosecretary",
                        HomescreenPreviewActivity.class.getName())
                .putExtra("preview", preview);
        try (ActivityController<HomescreenPreviewActivity> controller =
                     Robolectric.buildActivity(HomescreenPreviewActivity.class, intent)) {
            HomescreenPreviewActivity activity = controller.setup().get();
            View content = activity.findViewById(android.R.id.content);
            if (emulateSystemInsets) {
                LinearLayout screen = activity.findViewById(R.id.dashboard_screen);
                float density = activity.getResources().getDisplayMetrics().density;
                screen.setPadding(0, Math.round(28 * density), 0, 0);
            }
            content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            content.layout(0, 0, width, height);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            WoodGrainRenderPipeline.awaitIdleForTest();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            content.draw(new Canvas(bitmap));
            assertGolden(preview, bitmap);
            bitmap.recycle();
        }
    }

    private static void assertGolden(String name, Bitmap actual) throws Exception {
        GoldenAssertions.compare(HomescreenGoldenRobolectricTest.class,
                "/golden/homescreen/" + name + ".png",
                new File("src/test/resources/golden/homescreen", name + ".png"),
                new File("build/reports/goldens/homescreen", name), actual, 0, 0d,
                "UPDATE_HOMESCREEN_GOLDENS");
    }

}
