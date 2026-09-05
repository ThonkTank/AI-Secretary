package de.thonktank.autosecretary.ui.today;

import de.thonktank.autosecretary.*;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One shared minus/value/plus editor used by the active Today step. */
final class InlineValueEditorView extends LinearLayout {
    private final UiStyle style;
    private final TextView label;
    private final TextView minus;
    private final TextView value;
    private final TextView plus;

    InlineValueEditorView(Context context) {
        super(context);
        style = new UiStyle(context);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(style.dp(48));
        label = style.sans("", 14, 0, false);
        addView(label, new LayoutParams(0, -2, 1));
        minus = action("−");
        minus.setId(R.id.inline_value_decrement);
        addView(minus, new LayoutParams(style.dp(44), style.dp(44)));
        value = style.sans("", 19, 0, true);
        value.setId(R.id.inline_value_current);
        value.setGravity(Gravity.CENTER);
        LayoutParams valueParams = new LayoutParams(-2, style.dp(44));
        valueParams.setMargins(style.dp(4), 0, style.dp(4), 0);
        addView(value, valueParams);
        plus = action("+");
        plus.setId(R.id.inline_value_increment);
        addView(plus, new LayoutParams(style.dp(44), style.dp(44)));
    }

    void bind(String name, String formattedValue, boolean canDecrease, boolean canIncrease,
              DayPalette palette, Runnable decrease, Runnable increase) {
        label.setText(name);
        label.setTextColor(palette.muted);
        value.setText(formattedValue);
        value.setTextColor(palette.ink);
        bindAction(minus, canDecrease, palette, decrease,
                getContext().getString(R.string.content_value_decrease, name));
        bindAction(plus, canIncrease, palette, increase,
                getContext().getString(R.string.content_value_increase, name));
    }

    private TextView action(String text) {
        TextView view = style.sans(text, 21, 0, false);
        view.setGravity(Gravity.CENTER);
        AccessibilityRoles.button(view);
        return view;
    }

    private void bindAction(TextView view, boolean enabled, DayPalette palette,
                            Runnable action, String description) {
        view.setTextColor(palette.accent);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.TRANSPARENT);
        circle.setStroke(style.dp(1), palette.dot);
        view.setBackground(circle);
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : .45f);
        view.setContentDescription(description);
        view.setOnClickListener(enabled && action != null ? ignored -> action.run() : null);
    }
}
