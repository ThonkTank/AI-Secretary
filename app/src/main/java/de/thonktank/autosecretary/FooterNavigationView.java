package de.thonktank.autosecretary;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
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
        setPadding(style.dp(60), style.dp(16), style.dp(26), style.dp(16));
        int[] names = {R.string.nav_today, R.string.nav_all, R.string.nav_options};
        int[] ids = {R.id.navigation_today, R.id.navigation_all_tasks, R.id.navigation_options};
        for (int i = 0; i < labels.length; i++) {
            NavLabel label = new NavLabel(context);
            label.setId(ids[i]);
            label.setText(names[i]);
            label.setGravity(Gravity.CENTER);
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

    private NavLabel delegatedTarget;

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            delegatedTarget = null;
            float minimum = style.dp(48);
            for (NavLabel label : labels) {
                Rect target = effectiveTouchBounds(label);
                if (target.contains(Math.round(event.getX()), Math.round(event.getY()))) {
                    delegatedTarget = label;
                    break;
                }
            }
        }
        if (delegatedTarget == null) return super.dispatchTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) delegatedTarget.setPressed(true);
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            NavLabel target = delegatedTarget;
            boolean inside = effectiveTouchBounds(target).contains(
                    Math.round(event.getX()), Math.round(event.getY()));
            target.setPressed(false);
            delegatedTarget = null;
            if (inside) target.performClick();
        } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            delegatedTarget.setPressed(false);
            delegatedTarget = null;
        }
        return true;
    }

    Rect effectiveTouchBounds(View label) {
        int minimum = style.dp(48);
        int extraX = Math.max(0, minimum - label.getWidth());
        int extraY = Math.max(0, minimum - label.getHeight());
        return new Rect(label.getLeft() - extraX / 2, label.getTop() - extraY / 2,
                label.getRight() + (extraX + 1) / 2,
                label.getBottom() + (extraY + 1) / 2);
    }

    private final class NavLabel extends TextView {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean active;

        NavLabel(Context context) {
            super(context);
            setTypeface(style.sans);
            setTextSize(17);
            setIncludeFontPadding(false);
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
