package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class WoodGrainRenderPipelineTest {
    @Test public void concurrentRequestsBuildAKeyOnceOffTheCallingThread() {
        WoodGrainRenderPipeline.clearForTest();
        WoodGrainRenderRequest request = request(960, 720, 14);

        CompletableFuture<WoodGrainRenderData> first =
                WoodGrainRenderPipeline.request(request);
        CompletableFuture<WoodGrainRenderData> second =
                WoodGrainRenderPipeline.request(request);

        WoodGrainRenderData data = first.join();
        assertSame(data, second.join());
        assertFalse(data.strokes.isEmpty());
        assertEquals(1, WoodGrainRenderPipeline.buildCountForTest());
        assertTrue(WoodGrainRenderPipeline.lastBuildThreadForTest()
                .startsWith("wood-grain-geometry"));
    }

    @Test public void weightedCacheNeverExceedsItsByteBudget() {
        WoodGrainRenderCache cache = new WoodGrainRenderCache(2_000_000);
        WoodGrainRenderData first = data(50_000);
        WoodGrainRenderData second = data(50_000);
        WoodGrainRenderData tooLarge = data(100_000);

        cache.put("first", first);
        cache.put("second", second);

        assertTrue(cache.bytes() <= cache.maximumBytes());
        assertNull(cache.get("first"));
        assertSame(second, cache.get("second"));
        cache.put("too-large", tooLarge);
        assertNull(cache.get("too-large"));
        assertTrue(cache.bytes() <= cache.maximumBytes());
    }

    @Test public void drawNeverTriggersGeometryAndDetachedViewPublishesOnReattach() {
        WoodGrainRenderPipeline.clearForTest();
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        WoodGrainView grain = new WoodGrainView(activity);
        root.addView(grain, new FrameLayout.LayoutParams(600, 480));
        activity.setContentView(root);
        root.measure(exact(600), exact(480));
        root.layout(0, 0, 600, 480);
        grain.bind(DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO),
                Collections.singletonList(new WoodGrainView.Anchor(
                        new RectF(180, 140, 300, 260), 8)));
        root.removeView(grain);
        WoodGrainView.awaitGeometryForTest();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(grain.hasRenderDataForTest());

        root.addView(grain, new FrameLayout.LayoutParams(600, 480));
        root.measure(exact(600), exact(480));
        root.layout(0, 0, 600, 480);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        WoodGrainView.awaitGeometryForTest();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(grain.hasRenderDataForTest());
        int builds = WoodGrainRenderPipeline.buildCountForTest();
        Bitmap bitmap = Bitmap.createBitmap(600, 480, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        for (int index = 0; index < 8; index++) grain.draw(canvas);
        assertEquals(builds, WoodGrainRenderPipeline.buildCountForTest());
        bitmap.recycle();
    }

    private static WoodGrainRenderRequest request(int width, int height, int level) {
        return WoodGrainRenderRequest.anchors(width, height, 2f, Arrays.asList(
                new WoodGrainRenderRequest.Anchor(new RectF(100, 100, 240, 240), level),
                new WoodGrainRenderRequest.Anchor(new RectF(300, 260, 440, 400), level)));
    }

    private static WoodGrainRenderData data(int segments) {
        return new WoodGrainRenderData(Collections.singletonList(
                new WoodGrainRenderData.Stroke(new Path(), .4f, 2f, segments)));
    }

    private static int exact(int size) {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
    }
}
