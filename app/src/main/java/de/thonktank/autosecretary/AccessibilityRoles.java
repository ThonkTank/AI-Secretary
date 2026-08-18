package de.thonktank.autosecretary;

import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

import java.util.function.BooleanSupplier;

/** Small accessibility contract for custom-drawn and text-backed controls. */
final class AccessibilityRoles {
    private AccessibilityRoles() { }

    static void button(View view) { toggleButton(view, null); }

    static void toggleButton(View view, BooleanSupplier checked) {
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
            }
        });
    }
}
