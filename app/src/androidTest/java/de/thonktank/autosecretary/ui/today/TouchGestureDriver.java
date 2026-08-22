package de.thonktank.autosecretary.ui.today;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Build;
import android.os.SystemClock;
import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.Locale;

/** Owns the system-level finger metadata and timing used by Today gesture tests. */
final class TouchGestureDriver {
    private static final int MOVE_STEPS = 12;
    private static final long MOVE_DELAY_MILLIS = 24L;
    private static final int EDGE_HOLD_STEPS = 8;

    private final Instrumentation instrumentation;
    private final int touchDeviceId;
    private final int displayId;
    private long downTime;
    private long lastEventTime;
    private int startX;
    private int startY;
    private int lastX;
    private int lastY;
    private boolean active;

    TouchGestureDriver(Instrumentation instrumentation, View target) {
        if (instrumentation == null || target == null)
            throw new IllegalArgumentException("Instrumentation and a target view are required");
        this.instrumentation = instrumentation;
        touchDeviceId = touchscreenDeviceId();
        if (touchDeviceId < 0) throw new AssertionError("A touchscreen input device is required");
        Display display = target.getDisplay();
        displayId = display == null ? Display.DEFAULT_DISPLAY : display.getDisplayId();
    }

    void down(int[] location) {
        if (active) throw new IllegalStateException("A pointer gesture is already active");
        downTime = SystemClock.uptimeMillis();
        startX = location[0];
        startY = location[1];
        active = true;
        send(MotionEvent.ACTION_DOWN, startX, startY);
    }

    void holdForLongPress() {
        requireActive();
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 100L);
    }

    void moveTo(int[] target) {
        requireActive();
        int fromX = lastX;
        int fromY = lastY;
        for (int step = 1; step <= MOVE_STEPS; step++) {
            float progress = step / (float) MOVE_STEPS;
            int x = Math.round(fromX + (target[0] - fromX) * progress);
            int y = Math.round(fromY + (target[1] - fromY) * progress);
            send(MotionEvent.ACTION_MOVE, x, y);
            SystemClock.sleep(MOVE_DELAY_MILLIS);
        }
    }

    void holdAtEdge(int[] edge) {
        requireActive();
        for (int step = 0; step < EDGE_HOLD_STEPS; step++) {
            send(MotionEvent.ACTION_MOVE, edge[0], edge[1] - step % 2);
            SystemClock.sleep(MOVE_DELAY_MILLIS);
        }
    }

    void up() {
        if (!active) return;
        send(MotionEvent.ACTION_UP, lastX, lastY);
        active = false;
    }

    void cancel() {
        if (!active) return;
        send(MotionEvent.ACTION_CANCEL, lastX, lastY);
        active = false;
    }

    String describe() {
        return String.format(Locale.ROOT,
                "touchDeviceId=%d displayId=%d active=%s downTime=%d lastEventTime=%d "
                        + "start=(%d,%d) last=(%d,%d)",
                touchDeviceId, displayId, active, downTime, lastEventTime,
                startX, startY, lastX, lastY);
    }

    private void send(int action, int x, int y) {
        lastEventTime = SystemClock.uptimeMillis();
        lastX = x;
        lastY = y;
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords();
        coordinates.x = x;
        coordinates.y = y;
        coordinates.pressure = 1f;
        coordinates.size = 1f;
        MotionEvent.PointerProperties[] pointerProperties = {properties};
        MotionEvent.PointerCoords[] pointerCoordinates = {coordinates};
        MotionEvent event = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ? MotionEvent.obtain(downTime, lastEventTime, action, 1, pointerProperties,
                        pointerCoordinates, 0, 0, 1f, 1f, touchDeviceId, 0,
                        InputDevice.SOURCE_TOUCHSCREEN, 0, displayId,
                        MotionEvent.CLASSIFICATION_NONE)
                : MotionEvent.obtain(downTime, lastEventTime, action, 1, pointerProperties,
                        pointerCoordinates, 0, 0, 1f, 1f, touchDeviceId, 0,
                        InputDevice.SOURCE_TOUCHSCREEN, 0);
        try {
            UiAutomation automation = instrumentation.getUiAutomation();
            if (automation == null)
                throw new AssertionError("UI automation is required for pointer injection");
            if (!automation.injectInputEvent(event, true))
                throw new AssertionError("Pointer event injection failed: " + describe());
        } finally {
            event.recycle();
        }
    }

    private void requireActive() {
        if (!active) throw new IllegalStateException("No pointer gesture is active");
    }

    private static int touchscreenDeviceId() {
        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device != null && device.supportsSource(InputDevice.SOURCE_TOUCHSCREEN))
                return deviceId;
        }
        return -1;
    }
}
