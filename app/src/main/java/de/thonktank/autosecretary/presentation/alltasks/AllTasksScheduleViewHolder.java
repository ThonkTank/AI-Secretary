package de.thonktank.autosecretary.presentation.alltasks;

import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.R;

/** Fixed hierarchies for slot headings, schedule rows and schedule targets. */
final class AllTasksScheduleViewHolder extends AllTasksRowViewHolder {
    private final AllTasksRowUi ui;
    private final AllTasksRow.Kind kind;
    private TextView marker;
    private LinearLayout schedule;
    private ImageButton handle;
    private TextView title;
    private TextView recurrence;
    private TextView target;

    AllTasksScheduleViewHolder(ViewGroup parent, AllTasksRow.Kind kind, AllTasksRowUi ui) {
        super(parent);
        this.kind = kind;
        this.ui = ui;
        if (kind == AllTasksRow.Kind.SLOT_HEADER) buildHeader();
        else if (kind == AllTasksRow.Kind.SCHEDULE) buildSchedule();
        else if (kind == AllTasksRow.Kind.SCHEDULE_TARGET) buildTarget();
        else throw new IllegalArgumentException("Unsupported schedule row " + kind);
    }

    private void buildHeader() {
        marker = ui.style.serif("", 17, Color.TRANSPARENT, true, 300);
        marker.setPadding(ui.style.dp(4), ui.style.dp(12), 0, ui.style.dp(6));
        ViewCompat.setAccessibilityHeading(marker, true);
        root.addView(marker, new FrameLayout.LayoutParams(-1, -2));
    }

    private void buildSchedule() {
        schedule = ui.row();
        schedule.setPadding(ui.style.dp(8), ui.style.dp(4), ui.style.dp(10), ui.style.dp(4));
        handle = ui.icon(R.drawable.ic_drag_handle, R.string.all_drag_task);
        schedule.addView(handle,
                new LinearLayout.LayoutParams(ui.style.dp(48), ui.style.dp(48)));
        LinearLayout copy = ui.column();
        title = ui.style.serif("", 20, Color.TRANSPARENT, false, 350);
        recurrence = ui.style.sans("", 14, Color.TRANSPARENT, false);
        copy.addView(title);
        copy.addView(recurrence);
        schedule.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(schedule, new FrameLayout.LayoutParams(-1, -2));
    }

    private void buildTarget() {
        target = ui.style.sans(ui.context.getString(R.string.all_schedule_insert_target),
                14, Color.TRANSPARENT, false);
        target.setGravity(Gravity.CENTER_VERTICAL);
        target.setMinHeight(ui.style.dp(48));
        target.setPadding(ui.style.dp(56), 0, ui.style.dp(8), 0);
        root.addView(target, new FrameLayout.LayoutParams(-1, -2));
    }

    @Override void bind(AllTasksRow row, AllTasksUiState state,
                        DayPalette palette, boolean dragActive) {
        prepare(row, dragActive, ui);
        if (kind == AllTasksRow.Kind.SLOT_HEADER) {
            marker.setTextColor(palette.muted);
            marker.setText(ui.slotLabel(row.slot));
        } else if (kind == AllTasksRow.Kind.SCHEDULE) {
            bindSchedule(row, palette);
        } else {
            target.setTextColor(palette.muted);
            target.setContentDescription(ui.context.getString(R.string.a11y_schedule_drop_target,
                    ui.slotLabel(row.slot)));
        }
    }

    private void bindSchedule(AllTasksRow row, DayPalette palette) {
        AllTasksUiState.ScheduleItem item = row.schedule;
        schedule.setBackground(ui.style.leaf(palette.leaf2, palette.leaf2Edge, 36, 8, 36, 8));
        handle.setColorFilter(palette.dot);
        title.setTextColor(palette.ink);
        title.setText(item.title);
        recurrence.setTextColor(palette.hint);
        recurrence.setText(ui.recurrenceLabel(item.recurrence));
        schedule.setContentDescription(ui.context.getString(R.string.a11y_schedule_row,
                item.title, ui.slotLabel(item.slot), ui.recurrenceLabel(item.recurrence)));
    }
}
