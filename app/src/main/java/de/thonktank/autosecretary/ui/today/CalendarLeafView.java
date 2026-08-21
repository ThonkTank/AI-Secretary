package de.thonktank.autosecretary.ui.today;

import de.thonktank.autosecretary.presentation.today.CalendarEventSnapshot;

import de.thonktank.autosecretary.*;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class CalendarLeafView extends LinearLayout {
    private final UiStyle style;
    private final TextView time;
    private final TextView title;
    private final TextView label;

    public CalendarLeafView(Context context) {
        super(context);
        style = new UiStyle(context);
        setOrientation(VERTICAL);
        int horizontal = style.dimen(R.dimen.leaf_padding);
        setPadding(horizontal, style.dp(20), horizontal, style.dp(20));
        setRotation(-1f);
        LinearLayout row = new LinearLayout(context);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        time = style.sans("", 19, 0, true);
        row.addView(time);
        title = style.serif("", 22, 0, false, 400);
        LayoutParams titleParams = new LayoutParams(0, -2, 1);
        titleParams.setMargins(style.dp(9), 0, 0, 0);
        row.addView(title, titleParams);
        addView(row, new LayoutParams(-1, -2));
        label = style.serif(context.getString(R.string.calendar_fixed), 15, 0, true, 300);
        addView(label);
    }

    public void bind(CalendarEventSnapshot event, DayPalette palette) {
        setBackground(style.leaf(palette.calendar, palette.calendarEdge,
                8, 56, 8, 56));
        style.shadow(this, palette, 7, .7f);
        time.setText(event.time);
        title.setText(event.title);
        time.setTextColor(palette.calendarInk);
        title.setTextColor(palette.calendarInk);
        label.setTextColor(palette.calendarLabel);
    }
}
