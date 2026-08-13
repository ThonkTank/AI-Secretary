package com.autosecretary.ui;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.autosecretary.R;

import java.util.List;

/** Small leaf-shaped action sheet over the handoff's fifty-percent forest dimmer. */
public final class LeafActionMenu {
    public record Action(String label, boolean danger, Runnable command) { }

    private LeafActionMenu() { }

    public static void show(View source, List<Action> actions) {
        Dialog dialog = new Dialog(source.getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout leaf = new LinearLayout(source.getContext());
        leaf.setOrientation(LinearLayout.VERTICAL);
        leaf.setPadding(dp(source, 22), dp(source, 12), dp(source, 22), dp(source, 12));
        leaf.setBackgroundResource(R.drawable.bg_leaf_focus);
        for (int index = 0; index < actions.size(); index++) {
            Action action = actions.get(index);
            TextView row = new TextView(source.getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinHeight(dp(source, 52));
            row.setText(action.label());
            row.setTextColor(source.getContext().getColor(
                    action.danger() ? R.color.danger : R.color.ink));
            row.setTextSize(17);
            row.setOnClickListener(view -> {
                dialog.dismiss();
                action.command().run();
            });
            leaf.addView(row, new LinearLayout.LayoutParams(
                    dp(source, 270), LinearLayout.LayoutParams.WRAP_CONTENT));
            if (index >= actions.size() - 1) continue;
            View divider = new View(source.getContext());
            divider.setBackgroundColor(withAlpha(
                    source.getContext().getColor(R.color.ink_muted), 42));
            leaf.addView(divider, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(source, 1)));
        }
        dialog.setContentView(leaf);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.5f;
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }
        dialog.show();
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
