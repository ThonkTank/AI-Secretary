package de.thonktank.autosecretary;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.FrameLayout;

import java.time.LocalTime;

@SuppressLint("ViewConstructor")
public final class HeaderView extends LinearLayout {
    private final UiStyle style;
    private final TextView greeting;
    private final TextView add;

    public HeaderView(Context context, Runnable onAdd) {
        super(context);
        style = new UiStyle(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(style.dp(76), 0, style.dp(22), 0);
        greeting = style.serif("", 19, 0, true, 300);
        greeting.setTranslationY(style.dp(7));
        addView(greeting, new LayoutParams(0, -2, 1));
        FrameLayout addTarget = new FrameLayout(context);
        addTarget.setContentDescription(context.getString(R.string.content_add_task));
        addTarget.setOnClickListener(view -> onAdd.run());
        addTarget.setTranslationY(style.dp(7));
        add = style.sans("＋", 23, 0, false);
        add.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams visual = new FrameLayout.LayoutParams(style.dp(40), style.dp(40), Gravity.CENTER);
        addTarget.addView(add, visual);
        addView(addTarget, new LayoutParams(style.dimen(R.dimen.touch_target), style.dimen(R.dimen.touch_target)));
    }

    public void bind(LocalTime time, DayPalette palette) {
        greeting.setText(DayPalette.greetingRes(time));
        greeting.setTextColor(palette.status);
        add.setTextColor(palette.lightText);
        add.setBackground(style.pill(palette.light, 24));
    }
}
