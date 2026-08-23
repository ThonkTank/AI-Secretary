package de.thonktank.autosecretary;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

import java.util.List;
import java.util.function.BooleanSupplier;

/** Small accessibility contract for custom-drawn and text-backed controls. */
public final class AccessibilityRoles {
    private AccessibilityRoles() { }

    public static void button(View view) { toggleButton(view, null); }

    public static void button(View view, int minimumTouchTargetPx) {
        toggleButton(view, null, minimumTouchTargetPx);
    }

    public static void toggleButton(View view, BooleanSupplier checked) {
        toggleButton(view, checked, 0);
    }

    public static void toggleButton(View view, BooleanSupplier checked,
                                    int minimumTouchTargetPx) {
        view.setFocusable(true);
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(
                    View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
                if (checked != null) {
                    info.setCheckable(true);
                    info.setChecked(checked.getAsBoolean());
                }
                if (minimumTouchTargetPx > 0 && host.getParent() instanceof ViewGroup) {
                    Rect bounds = new Rect();
                    host.getHitRect(bounds);
                    expandToMinimum(bounds, minimumTouchTargetPx);
                    info.setBoundsInParent(bounds);
                }
            }
        });
    }

    static void minimumTarget(View view, int minimumTouchTargetPx) {
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(
                    View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                if (!(host.getParent() instanceof ViewGroup)) return;
                Rect bounds = new Rect();
                host.getHitRect(bounds);
                expandToMinimum(bounds, minimumTouchTargetPx);
                info.setBoundsInParent(bounds);
            }
        });
    }

    static void installExpandedTouchTargets(ViewGroup parent, List<? extends View> targets,
                                            int minimumTouchTargetPx) {
        parent.post(() -> {
            TaskEditorTouchDelegateGroup delegates = new TaskEditorTouchDelegateGroup(parent);
            for (View target : targets) {
                Rect bounds = new Rect();
                target.getHitRect(bounds);
                expandToMinimum(bounds, minimumTouchTargetPx);
                delegates.add(bounds, target);
            }
            parent.setTouchDelegate(delegates);
        });
    }

    static void installExpandedTouchTarget(View target, int minimumTouchTargetPx) {
        target.post(() -> {
            if (!(target.getParent() instanceof ViewGroup)) return;
            ViewGroup parent = (ViewGroup) target.getParent();
            TaskEditorTouchDelegateGroup delegates;
            if (parent.getTouchDelegate() instanceof TaskEditorTouchDelegateGroup)
                delegates = (TaskEditorTouchDelegateGroup) parent.getTouchDelegate();
            else {
                delegates = new TaskEditorTouchDelegateGroup(parent);
                parent.setTouchDelegate(delegates);
            }
            Rect bounds = new Rect();
            target.getHitRect(bounds);
            expandToMinimum(bounds, minimumTouchTargetPx);
            delegates.add(bounds, target);
        });
    }

    private static void expandToMinimum(Rect bounds, int minimumPx) {
        int missingWidth = Math.max(0, minimumPx - bounds.width());
        int missingHeight = Math.max(0, minimumPx - bounds.height());
        bounds.left -= missingWidth / 2;
        bounds.right += missingWidth - missingWidth / 2;
        bounds.top -= missingHeight / 2;
        bounds.bottom += missingHeight - missingHeight / 2;
    }
}
