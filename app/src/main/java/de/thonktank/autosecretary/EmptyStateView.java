package de.thonktank.autosecretary;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

@SuppressLint("ViewConstructor")
public final class EmptyStateView extends LinearLayout {
    private final UiStyle style;
    private final TextView title;
    private final TextView subtitle;
    private final TextView action;

    public EmptyStateView(Context context, Runnable onAdd) {
        super(context);
        style = new UiStyle(context);
        setOrientation(VERTICAL);
        setPadding(style.dp(28), style.dp(29), style.dp(28), style.dp(26));
        setRotation(-.5f);
        title = style.serif("", 30, 0, false, 200);
        title.setLineSpacing(0, 1.1f);
        addView(title);
        subtitle = style.sans("", 16, 0, false);
        subtitle.setLineSpacing(0, 1.55f);
        LayoutParams subtitleParams = new LayoutParams(-1, -2);
        subtitleParams.setMargins(0, style.dp(18), 0, 0);
        addView(subtitle, subtitleParams);
        action = new TextView(context);
        action.setText(R.string.action_add_task);
        action.setContentDescription(context.getString(R.string.content_add_task));
        action.setOnClickListener(view -> onAdd.run());
        LayoutParams actionParams = new LayoutParams(-2, style.dp(52));
        actionParams.setMargins(0, style.dp(18), 0, 0);
        addView(action, actionParams);
    }

    public void bind(DayPalette palette, boolean placeholder) {
        setBackground(style.dashed(palette));
        title.setText(placeholder ? R.string.all_placeholder_title : R.string.empty_title);
        subtitle.setText(placeholder ? R.string.all_placeholder_subtitle : R.string.empty_subtitle);
        title.setTextColor(palette.ink);
        subtitle.setTextColor(palette.hint);
        action.setVisibility(placeholder ? View.GONE : View.VISIBLE);
        if (!placeholder) {
            action.setTextColor(palette.accentText);
            action.setGravity(android.view.Gravity.CENTER);
            action.setPadding(style.dp(28), 0, style.dp(28), 0);
            action.setBackground(style.pill(palette.accent, 26));
            action.setTypeface(style.sansBold);
            action.setTextSize(17);
            style.shadow(action, palette, 5, .7f);
        }
    }
}
