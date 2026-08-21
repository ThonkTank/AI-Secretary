package de.thonktank.autosecretary;

import de.thonktank.autosecretary.ui.today.EdgeAutoScroller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class EdgeAutoScrollerTest {
    @Test public void distanceDependsOnElapsedTimeNotDragEventCount() {
        FakeHost sparseHost = new FakeHost();
        FakeTime sparseTime = new FakeTime();
        EdgeAutoScroller sparse = new EdgeAutoScroller(sparseHost, sparseTime, 50, 360f);
        sparse.update(199f, 200);
        sparseTime.now = 100;
        sparseHost.runFrame();

        FakeHost noisyHost = new FakeHost();
        FakeTime noisyTime = new FakeTime();
        EdgeAutoScroller noisy = new EdgeAutoScroller(noisyHost, noisyTime, 50, 360f);
        for (int index = 0; index < 40; index++) noisy.update(199f, 200);
        noisyTime.now = 100;
        noisyHost.runFrame();

        assertEquals(36, sparseHost.distance);
        assertEquals(sparseHost.distance, noisyHost.distance);
        assertEquals(1, noisyHost.pendingFrames);
    }

    @Test public void leavingEdgeOrEndingDragStopsScheduledFrames() {
        FakeHost host = new FakeHost();
        FakeTime time = new FakeTime();
        EdgeAutoScroller scroller = new EdgeAutoScroller(host, time, 40, 300f);

        scroller.update(1f, 200);
        assertTrue(host.pendingFrames > 0);
        scroller.update(100f, 200);
        assertEquals(0, host.pendingFrames);
        scroller.update(199f, 200);
        scroller.stop();
        assertEquals(0, host.pendingFrames);
        assertFalse(host.hasFrame());
    }

    private static final class FakeTime implements EdgeAutoScroller.TimeSource {
        long now;
        @Override public long nowMillis() { return now; }
    }

    private static final class FakeHost implements EdgeAutoScroller.ScrollHost {
        int distance;
        int pendingFrames;
        Runnable frame;

        @Override public void scrollBy(int dy) { distance += dy; }

        @Override public void postOnAnimation(Runnable value) {
            frame = value;
            pendingFrames = 1;
        }

        @Override public void removeCallbacks(Runnable value) {
            if (frame == value) {
                frame = null;
                pendingFrames = 0;
            }
        }

        void runFrame() {
            Runnable value = frame;
            frame = null;
            pendingFrames = 0;
            value.run();
        }

        boolean hasFrame() { return frame != null; }
    }
}
