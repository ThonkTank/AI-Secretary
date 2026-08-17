package de.thonktank.autosecretary;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Consumer;

@SuppressLint("ViewConstructor")
public final class FooterNavigationView extends LinearLayout {
    private final UiStyle style;
    private final NavLabel[] labels = new NavLabel[3];
    private final NavigationDestination[] destinations = NavigationDestination.values();

    public FooterNavigationView(Context context, Consumer<NavigationDestination> navigate) {
        super(context);
        style = new UiStyle(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.TOP);
        setPadding(style.dp(60), style.dp(16), style.dp(26), style.dp(6));
        int[] names = {R.string.nav_today, R.string.nav_all, R.string.nav_options};
        for (int i = 0; i < labels.length; i++) {
            NavLabel label = new NavLabel(context);
            label.setText(names[i]);
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
            NavLabel label = labels[i];
            label.setTextColor(active ? palette.ink2 : palette.status);
            label.setActive(active, palette.light);
            label.setSelected(active);
        }
    }

    private final class NavLabel extends TextView {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean active;

        NavLabel(Context context) {
            super(context);
            setTypeface(style.sans);
            setTextSize(17);
            setIncludeFontPadding(false);
            setMinWidth(style.dp(48));
            setMinHeight(style.dp(48));
            line.setStrokeWidth(style.dp(1.5f));
        }

        void setActive(boolean active, int color) {
            this.active = active;
            line.setColor(color);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!active) return;
            float textWidth = getPaint().measureText(getText().toString());
            float left = (getWidth() - textWidth) * .5f;
            float y = getHeight() * .5f - (getPaint().ascent() + getPaint().descent()) * .5f
                    + style.dp(5);
            canvas.drawLine(left, y, left + textWidth, y, line);
        }
    }
}
