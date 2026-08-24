package de.thonktank.autosecretary.ui.today;

import android.widget.ScrollView;

import de.thonktank.autosecretary.PresentationTrace;

/** Frame-driven edge scrolling whose speed does not depend on drag-event frequency. */
public final class EdgeAutoScroller implements Runnable {
    public interface ScrollHost {
        void scrollBy(int dy);
        void postOnAnimation(Runnable frame);
        void removeCallbacks(Runnable frame);
    }

    public interface TimeSource { long nowMillis(); }

    public static final class AndroidScrollHost implements ScrollHost {
        private final ScrollView view;

        public AndroidScrollHost(ScrollView view) { this.view = view; }

        @Override public void scrollBy(int dy) { view.scrollBy(0, dy); }
        @Override public void postOnAnimation(Runnable frame) { view.postOnAnimation(frame); }
        @Override public void removeCallbacks(Runnable frame) { view.removeCallbacks(frame); }
    }

    private final ScrollHost host;
    private final TimeSource time;
    private final int edgeSize;
    private final float pixelsPerSecond;
    private int direction;
    private long lastFrame;
    private float remainder;
    private boolean scheduled;

    public EdgeAutoScroller(ScrollHost host, TimeSource time, int edgeSize,
                            float pixelsPerSecond) {
        if (host == null || time == null || edgeSize <= 0 || pixelsPerSecond <= 0)
            throw new IllegalArgumentException("Complete edge-scroll dependencies are required");
        this.host = host;
        this.time = time;
        this.edgeSize = edgeSize;
        this.pixelsPerSecond = pixelsPerSecond;
    }

    public void update(float pointerY, int viewportHeight) {
        int next = pointerY < edgeSize ? -1
                : pointerY > viewportHeight - edgeSize ? 1 : 0;
        if (next == direction) return;
        direction = next;
        traceValue("direction", direction);
        lastFrame = time.nowMillis();
        remainder = 0f;
        if (direction == 0) stopFrame();
        else scheduleFrame();
    }

    public void stop() {
        direction = 0;
        remainder = 0f;
        trace("stop", "");
        stopFrame();
    }

    @Override public void run() {
        scheduled = false;
        if (direction == 0) return;
        long now = time.nowMillis();
        long elapsed = Math.max(0L, Math.min(100L, now - lastFrame));
        lastFrame = now;
        float distance = remainder + direction * pixelsPerSecond * elapsed / 1_000f;
        int pixels = (int) distance;
        remainder = distance - pixels;
        if (pixels != 0) {
            host.scrollBy(pixels);
            traceValue("frame", pixels);
        }
        scheduleFrame();
    }

    private static void trace(String kind, String detail) {
        if (PresentationTrace.enabled())
            PresentationTrace.emit("today-edge-scroll", kind, detail);
    }

    private static void traceValue(String kind, int value) {
        if (PresentationTrace.enabled())
            PresentationTrace.emit("today-edge-scroll", kind, "value=" + value);
    }

    private void scheduleFrame() {
        if (scheduled) return;
        scheduled = true;
        host.postOnAnimation(this);
    }

    private void stopFrame() {
        if (!scheduled) return;
        host.removeCallbacks(this);
        scheduled = false;
    }
}
