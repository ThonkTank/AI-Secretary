package de.thonktank.autosecretary.presentation.alltasks;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.UiStyle;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.time.format.DateTimeFormatter;

/** Shared row styling and localized copy; it does not own RecyclerView state. */
final class AllTasksRowUi {
    final Context context;
    final UiStyle style;
    final AllTasksView.Listener listener;
    final AllTasksSearchHighlighter highlighter;

    AllTasksRowUi(Context context, UiStyle style, AllTasksView.Listener listener) {
        this.context = context;
        this.style = style;
        this.listener = listener;
        this.highlighter = new AllTasksSearchHighlighter(style);
    }

    LinearLayout row() {
        LinearLayout value = new LinearLayout(context);
        value.setOrientation(LinearLayout.HORIZONTAL);
        value.setGravity(Gravity.CENTER_VERTICAL);
        return value;
    }

    LinearLayout column() {
        LinearLayout value = new LinearLayout(context);
        value.setOrientation(LinearLayout.VERTICAL);
        return value;
    }

    ImageButton icon(int drawable, int description) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(drawable);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setContentDescription(context.getString(description));
        button.setPadding(style.dp(10), style.dp(10), style.dp(10), style.dp(10));
        return button;
    }

    RippleDrawable ripple(Drawable content, float radius, DayPalette palette) {
        return new RippleDrawable(ColorStateList.valueOf(UiStyle.alpha(palette.ink, .10f)),
                content, style.pill(Color.WHITE, radius));
    }

    String taskMeta(AllTasksUiState.TaskItem item) {
        String timing = item.task.nextDueOn == null ? context.getString(R.string.all_no_due)
                : context.getString(R.string.all_next_due,
                item.task.nextDueOn.format(DateTimeFormatter.ofPattern("dd.MM.")));
        String value = slotLabel(item.slot) + " · " + recurrenceLabel(item.task.recurrence)
                + " · " + timing;
        return item.archived ? value + " · " + context.getString(R.string.all_archived) : value;
    }

    String stepLine(AllTasksUiState.TaskItem item) {
        int count = item.steps.size();
        if (count == 0) return context.getString(R.string.all_no_steps);
        if (item.searchExpanded)
            return context.getString(R.string.all_steps_matching,
                    item.matchingSteps.size(), count) + " ⌃";
        String label = count == 1 ? context.getString(R.string.all_step_count)
                : context.getString(R.string.all_steps_count, count);
        return label + (item.expanded ? " ⌃" : " ⌄");
    }

    String recurrenceLabel(Recurrence value) {
        if (value == Recurrence.ONCE) return context.getString(R.string.rhythm_once);
        if (value == Recurrence.DAILY) return context.getString(R.string.rhythm_daily);
        if (value == Recurrence.INTERVAL) return context.getString(R.string.rhythm_every_n);
        return context.getString(R.string.rhythm_weekdays);
    }

    String slotLabel(TaskSlot slot) {
        if (slot == TaskSlot.MORNING) return context.getString(R.string.slot_morning);
        if (slot == TaskSlot.MIDDAY) return context.getString(R.string.slot_midday);
        if (slot == TaskSlot.EVENING) return context.getString(R.string.slot_evening);
        return context.getString(R.string.slot_later);
    }
}
