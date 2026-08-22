package de.thonktank.autosecretary.presentation.alltasks;

import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.R;

/** Fixed hierarchy for all characterized empty states. */
final class AllTasksEmptyViewHolder extends AllTasksRowViewHolder {
    private final AllTasksRowUi ui;
    private final LinearLayout empty;
    private final TextView title;
    private final TextView subtitle;

    AllTasksEmptyViewHolder(ViewGroup parent, AllTasksRowUi ui) {
        super(parent);
        this.ui = ui;
        empty = ui.column();
        empty.setPadding(ui.style.dp(22), ui.style.dp(28), ui.style.dp(22), ui.style.dp(28));
        empty.setRotation(-.5f);
        title = ui.style.serif("", 25, Color.TRANSPARENT, false, 250);
        subtitle = ui.style.sans("", 16, Color.TRANSPARENT, false);
        empty.addView(title);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        // Preserve the characterized legacy spacing: this value was pixels, not dp.
        subtitleParams.topMargin = 12;
        empty.addView(subtitle, subtitleParams);
        root.addView(empty, new FrameLayout.LayoutParams(-1, -2));
    }

    @Override void bind(AllTasksRow row, AllTasksUiState state,
                        DayPalette palette, boolean dragActive) {
        prepare(row, dragActive, ui);
        empty.setBackground(ui.style.dashed(palette));
        int titleResource;
        int subtitleResource;
        if (row.emptyReason == AllTasksRow.EmptyReason.SEARCH) {
            titleResource = R.string.all_empty_search_title;
            subtitleResource = R.string.all_empty_search_subtitle;
        } else if (row.emptyReason == AllTasksRow.EmptyReason.FILTERS) {
            titleResource = R.string.all_empty_filter_title;
            subtitleResource = state.mode == AllTasksUiState.Mode.SORT
                    ? R.string.all_empty_filter_sort_subtitle
                    : R.string.all_empty_filter_subtitle;
        } else {
            titleResource = R.string.all_empty_status_title;
            subtitleResource = R.string.all_empty_status_subtitle;
        }
        title.setTextColor(palette.ink);
        title.setText(ui.context.getString(titleResource));
        subtitle.setTextColor(palette.hint);
        subtitle.setText(ui.context.getString(subtitleResource));
    }
}
