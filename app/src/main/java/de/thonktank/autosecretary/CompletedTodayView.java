package de.thonktank.autosecretary;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Collapsed-by-default access to today's completed occurrences and their exact undo. */
@SuppressLint("ViewConstructor")
final class CompletedTodayView extends LinearLayout {
    private final UiStyle style;
    private final DashboardEventSink events;
    private final TextView header;
    private final LinearLayout rows;
    private boolean expanded;
    private DayPalette palette;

    CompletedTodayView(Context context, DashboardEventSink events) {
        super(context);
        this.events = events;
        style = new UiStyle(context);
        setId(R.id.dashboard_completed_today);
        setOrientation(VERTICAL);
        header = style.serif("", 16, 0, true, 350);
        header.setId(R.id.dashboard_completed_today_toggle);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinHeight(style.dp(48));
        header.setPadding(style.dp(8), 0, style.dp(8), 0);
        AccessibilityRoles.button(header);
        header.setOnClickListener(view -> {
            expanded = !expanded;
            applyExpanded();
        });
        addView(header, new LayoutParams(-1, style.dp(48)));
        rows = new LinearLayout(context);
        rows.setOrientation(VERTICAL);
        addView(rows, new LayoutParams(-1, -2));
    }

    void bind(List<TaskSnapshot> completed, DayPalette palette) {
        this.palette = palette;
        setVisibility(completed.isEmpty() ? GONE : VISIBLE);
        if (completed.isEmpty()) return;
        header.setText(getResources().getQuantityString(
                R.plurals.completed_today, completed.size(), completed.size()));
        header.setTextColor(palette.muted);
        rows.removeAllViews();
        for (TaskSnapshot task : completed) rows.addView(row(task, palette),
                new LayoutParams(-1, style.dp(48)));
        applyExpanded();
    }

    private View row(TaskSnapshot task, DayPalette palette) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(style.dp(12), 0, style.dp(8), 0);
        TextView title = style.sans(task.title, 16, palette.done, false);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(title, new LayoutParams(0, -2, 1));
        TextView xp = style.sans(task.awardedXp + " XP", 14, palette.muted, false);
        row.addView(xp, new LayoutParams(-2, -2));
        TextLinkView undo = new TextLinkView(getContext());
        undo.setText(R.string.action_undo);
        undo.bind(palette.hint, palette.dot);
        undo.setGravity(Gravity.CENTER);
        AccessibilityRoles.button(undo);
        undo.setContentDescription(getContext().getString(R.string.content_undo_task,
                task.title, task.awardedXp));
        undo.setVisibility(task.undoAvailable ? VISIBLE : GONE);
        undo.setOnClickListener(view -> events.emit(
                DashboardEvent.undoCompleted(task.occurrenceId)));
        LayoutParams undoParams = new LayoutParams(-2, style.dp(48));
        undoParams.leftMargin = style.dp(14);
        row.addView(undo, undoParams);
        return row;
    }

    private void applyExpanded() {
        rows.setVisibility(expanded ? VISIBLE : GONE);
        header.setSelected(expanded);
        header.setCompoundDrawablesWithIntrinsicBounds(0, 0,
                expanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more, 0);
        if (palette != null) header.setCompoundDrawableTintList(
                android.content.res.ColorStateList.valueOf(palette.dot));
        header.setContentDescription(getContext().getString(expanded
                ? R.string.completed_today_collapse : R.string.completed_today_expand));
        requestLayout();
    }
}
