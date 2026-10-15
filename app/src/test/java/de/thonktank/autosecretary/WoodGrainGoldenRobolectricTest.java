package de.thonktank.autosecretary.ui.leaf;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.GoldenAssertions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;

/** Native Android path rendering of the three visually accepted prototype arrangements. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w360dp-h735dp-xhdpi")
public final class WoodGrainGoldenRobolectricTest {
    @Test public void hygieneKeepsUnequalRingCountsAndOpenOrganicJoins() throws Exception {
        render("hygiene", 360, 735, WoodGrainContourTest.hygiene());
    }

    @Test public void twoUnequalNeighborsKeepTheirSmoothJoin() throws Exception {
        render("neighbors", 360, 340, Arrays.asList(
                new WoodGrainGeometry.Shape(123, 141, 32, 32, 8),
                new WoodGrainGeometry.Shape(221, 192, 32, 32, 4)));
    }

    @Test public void wideDewStressKeepsAllOuterJoinsOpen() throws Exception {
        render("wide-dew-stress", 360, 410, WoodGrainContourTest.stress());
    }

    private static void render(String name, int widthDp, int heightDp,
                               List<WoodGrainGeometry.Shape> shapes) throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        float density = activity.getResources().getDisplayMetrics().density;
        int width = Math.round(widthDp * density), height = Math.round(heightDp * density);
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT);
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(palette.leaf1);
        WoodGrainView grain = new WoodGrainView(activity);
        root.addView(grain, new FrameLayout.LayoutParams(-1, -1));
        List<WoodGrainView.Anchor> anchors = new ArrayList<>();
        for (WoodGrainGeometry.Shape shape : shapes) anchors.add(new WoodGrainView.Anchor(
                new RectF((shape.cx - shape.width / 2) * density,
                        (shape.cy - shape.height / 2) * density,
                        (shape.cx + shape.width / 2) * density,
                        (shape.cy + shape.height / 2) * density), shape.level));
        grain.bind(palette, anchors);
        root.addView(new View(activity) {
            @Override protected void onDraw(Canvas canvas) {
                canvas.save();
                canvas.scale(density, density);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                for (WoodGrainGeometry.Shape shape : shapes) {
                    RectF bounds = new RectF(shape.cx - shape.width / 2,
                            shape.cy - shape.height / 2, shape.cx + shape.width / 2,
                            shape.cy + shape.height / 2);
                    float radius = Math.min(shape.width, shape.height) / 2;
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(palette.leaf1);
                    canvas.drawRoundRect(bounds, radius, radius, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1.3f);
                    paint.setColor(palette.muted);
                    canvas.drawRoundRect(bounds, radius, radius, paint);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(palette.accent);
                    paint.setTextSize(11);
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(Integer.toString(shape.level), shape.cx, shape.cy + 4, paint);
                }
                canvas.restore();
            }
        }, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        ShadowLooper.shadowMainLooper().idle();
        WoodGrainRenderPipeline.awaitIdleForTest();
        ShadowLooper.shadowMainLooper().idle();
        Bitmap actual = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(actual));
        int grainPixels = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int pixel = actual.getPixel(x, y);
            int red = (pixel >> 16) & 255, green = (pixel >> 8) & 255, blue = pixel & 255;
            if (red - green > 10 && green - blue > 20) grainPixels++;
        }
        assertTrue("the published view must actually contain the grain contours", grainPixels > 1000);
        GoldenAssertions.compare(WoodGrainGoldenRobolectricTest.class,
                "/golden/wood-grain/" + name + ".png",
                new File("src/test/resources/golden/wood-grain", name + ".png"),
                new File("build/reports/goldens/wood-grain", name), actual,
                0, 0d, "UPDATE_WOOD_GRAIN_GOLDENS");
        actual.recycle();
    }
}
