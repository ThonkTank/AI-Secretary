package de.thonktank.autosecretary;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Consumer;

@SuppressLint("ViewConstructor")
public final class FooterNavigationView extends LinearLayout {
    private final UiStyle style;
    private final TextView[] labels = new TextView[3];
    private final NavigationDestination[] destinations = NavigationDestination.values();

    public FooterNavigationView(Context context, Consumer<NavigationDestination> navigate) {
        super(context);
        style = new UiStyle(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(style.dp(60), 0, style.dp(22), 0);
        int[] names = {R.string.nav_today, R.string.nav_all, R.string.nav_options};
        for (int i = 0; i < labels.length; i++) {
            TextView label = style.sans(context.getString(names[i]), 17, 0, false);
            label.setGravity(Gravity.CENTER);
            label.setMinWidth(style.dp(48));
            label.setMinHeight(style.dp(48));
            NavigationDestination destination = destinations[i];
            label.setOnClickListener(view -> navigate.accept(destination));
            LayoutParams params = new LayoutParams(-2, -1);
            params.setMargins(0, 0, style.dp(26), 0);
            addView(label, params);
            labels[i] = label;
        }
    }

    public void bind(NavigationDestination selected, DayPalette palette) {
        for (int i = 0; i < labels.length; i++) {
            boolean active = destinations[i] == selected;
            TextView label = labels[i];
            label.setTextColor(active ? palette.ink2 : palette.status);
            label.setPadding(0, 0, 0, active ? style.dp(3) : 0);
            label.setBackground(active ? underline(palette.light) : null);
            label.setSelected(active);
        }
    }

    private GradientDrawable underline(int color) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, Color.TRANSPARENT, color});
        drawable.setGradientCenter(.5f, .94f);
        return drawable;
    }
}
