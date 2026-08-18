package de.thonktank.autosecretary;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "xxhdpi")
public final class WoodGrainBenchmarkTest {
    @Test public void recordsMissHitMemoryAndFrameDistributions() {
        Assume.assumeTrue("Run with -Dwoodgrain.benchmark=true",
                Boolean.getBoolean("woodgrain.benchmark"));
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Fixture fixture = new Fixture(activity, 1080, 900);
        fixture.awaitGeometry();
        for (int index = 0; index < 8; index++) fixture.draw();

        WoodGrainView.clearGeometryCacheForTest();
        List<Long> misses = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            WoodGrainView.clearGeometryCacheForTest();
            long started = System.nanoTime();
            fixture.grain.forceGeometryRequestForTest();
            fixture.awaitGeometry();
            misses.add(System.nanoTime() - started);
        }

        fixture.draw();
        List<Long> cacheHits = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            long started = System.nanoTime();
            fixture.grain.forceGeometryRequestForTest();
            fixture.awaitGeometry();
            cacheHits.add(System.nanoTime() - started);
        }
        List<Long> frames = new ArrayList<>();
        for (int index = 0; index < 120; index++) frames.add(fixture.timedDraw());

        WoodGrainView.clearGeometryCacheForTest();
        forceGc();
        long memoryBefore = usedHeap();
        for (int index = 0; index < 16; index++) {
            fixture.resize(840 + index * 12, 680 + index * 8);
            fixture.awaitGeometry();
            fixture.draw();
        }
        forceGc();
        long memoryAfter = usedHeap();

        Collections.sort(misses);
        Collections.sort(cacheHits);
        Collections.sort(frames);
        double missMedian = millis(percentile(misses, .50));
        double cacheHitMedian = millis(percentile(cacheHits, .50));
        double frameMedian = millis(percentile(frames, .50));
        double frameP95 = millis(percentile(frames, .95));
        long memoryDelta = Math.max(0L, memoryAfter - memoryBefore);
        System.out.printf(Locale.ROOT,
                "WOOD_GRAIN_BENCHMARK {\"missMedianMs\":%.3f,"
                        + "\"cacheHitMedianMs\":%.3f,\"frameMedianMs\":%.3f,"
                        + "\"frameP95Ms\":%.3f,"
                        + "\"cacheEntries\":%d,\"cacheBytes\":%d,"
                        + "\"heapDeltaBytes\":%d,\"buildCount\":%d}%n",
                missMedian, cacheHitMedian, frameMedian, frameP95,
                WoodGrainView.geometryCacheEntriesForTest(),
                WoodGrainView.geometryCacheBytesForTest(), memoryDelta,
                WoodGrainView.geometryBuildCountForTest());
        assertTrue(missMedian >= 0d);
    }

    private static long percentile(List<Long> values, double quantile) {
        int index = Math.min(values.size() - 1,
                Math.max(0, (int) Math.ceil(values.size() * quantile) - 1));
        return values.get(index);
    }

    private static double millis(long nanos) { return nanos / 1_000_000d; }
    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
    private static void forceGc() {
        System.gc();
        System.runFinalization();
    }

    private static final class Fixture {
        final FrameLayout root;
        final WoodGrainView grain;
        final Bitmap bitmap;
        final Canvas canvas;

        Fixture(Activity activity, int width, int height) {
            root = new FrameLayout(activity);
            grain = new WoodGrainView(activity);
            root.addView(grain, new FrameLayout.LayoutParams(-1, -1));
            View first = anchor(activity, root, 130, 120, 144, 144);
            View second = anchor(activity, root, 355, 250, 144, 144);
            View third = anchor(activity, root, 620, 420, 144, 144);
            View text = anchor(activity, root, 210, 610, 480, 70);
            activity.setContentView(root);
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bitmap);
            resize(width, height);
            grain.bind(DayPalette.at(java.time.LocalTime.NOON, DayPalette.Mode.AUTO),
                    Arrays.asList(new WoodGrainView.Anchor(
                                    WoodGrainCoordinates.bounds(grain, first), 12),
                            new WoodGrainView.Anchor(
                                    WoodGrainCoordinates.bounds(grain, second), 10),
                            new WoodGrainView.Anchor(
                                    WoodGrainCoordinates.bounds(grain, third), 8)),
                    Collections.singletonList(WoodGrainCoordinates.bounds(grain, text)));
        }

        void resize(int width, int height) {
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, width, height);
            grain.layout(0, 0, width, height);
        }

        void draw() { grain.draw(canvas); }
        void awaitGeometry() {
            WoodGrainView.awaitGeometryForTest();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        long timedDraw() {
            long started = System.nanoTime();
            draw();
            return System.nanoTime() - started;
        }

        private static View anchor(Activity activity, FrameLayout root, int left, int top,
                                   int width, int height) {
            View view = new View(activity);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
            params.leftMargin = left;
            params.topMargin = top;
            root.addView(view, params);
            return view;
        }
    }
}
