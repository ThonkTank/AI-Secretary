package de.thonktank.autosecretary.presentation.alltasks;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.R;

/** Fixed hierarchies for task-card headers and their add-step footer. */
final class AllTasksTaskCardViewHolder extends AllTasksRowViewHolder {
    private final AllTasksRowUi ui;
    private final AllTasksRow.Kind kind;
    private LinearLayout card;
    private TextView title;
    private TextView meta;
    private TextView menu;
    private TextView steps;
    private TextView add;

    AllTasksTaskCardViewHolder(ViewGroup parent, AllTasksRow.Kind kind, AllTasksRowUi ui) {
        super(parent);
        this.kind = kind;
        this.ui = ui;
        if (kind == AllTasksRow.Kind.TASK_HEADER) buildHeader();
        else if (kind == AllTasksRow.Kind.STEP_ADD) buildAddStep();
        else throw new IllegalArgumentException("Unsupported task-card row " + kind);
    }

    private void buildHeader() {
        card = ui.column();
        LinearLayout header = ui.row();
        LinearLayout copy = ui.column();
        title = ui.style.serif("", 22, Color.TRANSPARENT, false, 350);
        copy.addView(title);
        meta = ui.style.sans("", 14, Color.TRANSPARENT, false);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = ui.style.dp(3);
        copy.addView(meta, metaParams);
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        menu = ui.style.sans("⋮", 21, Color.TRANSPARENT, false);
        menu.setGravity(Gravity.CENTER);
        header.addView(menu, new LinearLayout.LayoutParams(ui.style.dp(48), ui.style.dp(48)));
        card.addView(header);
        steps = ui.style.sans("", 15, Color.TRANSPARENT, false);
        steps.setGravity(Gravity.CENTER_VERTICAL);
        steps.setMinHeight(ui.style.dp(44));
        steps.setPadding(0, 0, ui.style.dp(8), 0);
        LinearLayout.LayoutParams stepParams = new LinearLayout.LayoutParams(-2, -2);
        stepParams.topMargin = ui.style.dp(-4);
        card.addView(steps, stepParams);
        root.addView(card, new FrameLayout.LayoutParams(-1, -2));
    }

    private void buildAddStep() {
        add = ui.style.sans("＋ " + ui.context.getString(R.string.all_add_step),
                14, Color.TRANSPARENT, false);
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setMinHeight(ui.style.dp(44));
        add.setPadding(ui.style.dp(20), 0, ui.style.dp(8), 0);
        root.addView(add, new FrameLayout.LayoutParams(-1, -2));
    }

    @Override void bind(AllTasksRow row, AllTasksUiState state,
                        DayPalette palette, boolean dragActive) {
        prepare(row, dragActive, ui);
        if (kind == AllTasksRow.Kind.TASK_HEADER) bindHeader(row, palette);
        else bindAddStep(row, palette);
    }

    private void bindHeader(AllTasksRow row, DayPalette palette) {
        AllTasksUiState.TaskItem item = row.task;
        card.setPadding(ui.style.dp(18), ui.style.dp(14), ui.style.dp(8),
                item.expanded ? 0 : ui.style.dp(12));
        card.setBackground(item.expanded
                ? new AllTasksCardDrawable(ui.style, palette, AllTasksCardDrawable.Segment.TOP)
                : ui.style.leaf(palette.leaf2, palette.leaf2Edge, 42, 8, 42, 8));
        if (item.expanded) card.setElevation(0);
        else ui.style.shadow(card, palette, 5, .55f);
        title.setTextColor(palette.ink);
        title.setText(ui.highlighter.highlight(item.task.title, item.needle, palette));
        meta.setTextColor(palette.hint);
        meta.setText(ui.taskMeta(item));
        menu.setTextColor(palette.dot);
        menu.setContentDescription(ui.context.getString(R.string.a11y_task_menu,
                item.task.title));
        menu.setBackground(ui.ripple(ui.style.pill(Color.TRANSPARENT, 24), 24, palette));
        menu.setOnClickListener(anchor -> showTaskMenu(anchor, item));
        steps.setTextColor(palette.ink2);
        steps.setText(ui.stepLine(item));
        if (item.steps.isEmpty()) {
            steps.setOnClickListener(null);
            steps.setContentDescription(null);
        } else {
            steps.setOnClickListener(ignored -> ui.listener.onToggleTask(item.cardKey));
            steps.setContentDescription(ui.context.getString(item.expanded
                    ? R.string.a11y_collapse_task : R.string.a11y_expand_task));
        }
        card.setContentDescription(ui.context.getString(R.string.a11y_task_row,
                item.task.title, ui.taskMeta(item)));
    }

    private void bindAddStep(AllTasksRow row, DayPalette palette) {
        add.setTextColor(palette.ink2);
        add.setBackground(new AllTasksCardDrawable(ui.style, palette,
                AllTasksCardDrawable.Segment.BOTTOM));
        add.setOnClickListener(ignored -> ui.listener.onAddStep(row.taskId));
        add.setContentDescription(ui.context.getString(R.string.a11y_add_step_target));
    }

    private void showTaskMenu(View anchor, AllTasksUiState.TaskItem item) {
        PopupMenu popup = new PopupMenu(ui.context, anchor);
        popup.getMenu().add(0, 1, 0, R.string.task_edit);
        popup.getMenu().add(0, 2, 1, R.string.task_delete);
        popup.setOnMenuItemClickListener(selected -> {
            if (selected.getItemId() == 1) ui.listener.onEditTask(item.task.id.value);
            else ui.listener.onDeleteTask(item.task.id.value, item.task.title);
            return true;
        });
        popup.show();
    }
}
