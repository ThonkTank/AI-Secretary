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
        for (String state : new String[]{"full", "step", "later", "complete", "evening", "empty",
                "empty-vessel", "partial-vessel", "harvest-ready", "harvested",
                "three-digit", "night"})
            render(state, 824, 1784, true);
    }

    @Test
    @Config(sdk = 35, qualifiers = "xxhdpi")
    public void renderWidgetReferenceStates() throws Exception {
        render("widget-small", 480, 480, false);
        render("widget-wide", 1032, 480, false);
        render("widget-large", 1032, 1032, false);
        render("widget-tall", 840, 1032, false);
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
            WoodGrainView.awaitGeometryForTest();
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
