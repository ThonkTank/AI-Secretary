package de.thonktank.autosecretary;

import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Combines expanded touch bounds for adjacent compact editor controls. */
final class TaskEditorTouchDelegateGroup extends TouchDelegate {
    private final List<TouchDelegate> delegates = new ArrayList<>();
    private final List<Rect> bounds = new ArrayList<>();
    private int activeDelegate = -1;

    TaskEditorTouchDelegateGroup(View parent) {
        super(new Rect(), parent);
    }

    void add(Rect hitBounds, View target) {
        Rect copy = new Rect(hitBounds);
        bounds.add(copy);
        delegates.add(new TouchDelegate(copy, target));
    }

    List<Rect> boundsForTest() {
        return Collections.unmodifiableList(bounds);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
            activeDelegate = closestDelegate(event.getX(), event.getY());
        if (activeDelegate < 0 || activeDelegate >= delegates.size()) return false;
        boolean handled = delegates.get(activeDelegate).onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
            activeDelegate = -1;
        return handled;
    }

    private int closestDelegate(float x, float y) {
        int closest = -1;
        float closestDistance = Float.MAX_VALUE;
        for (int index = 0; index < bounds.size(); index++) {
            Rect candidate = bounds.get(index);
            if (!candidate.contains(Math.round(x), Math.round(y))) continue;
            float dx = x - candidate.exactCenterX();
            float dy = y - candidate.exactCenterY();
            float distance = dx * dx + dy * dy;
            if (distance < closestDistance) {
                closest = index;
                closestDistance = distance;
            }
        }
        return closest;
    }
}
