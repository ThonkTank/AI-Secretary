package de.thonktank.autosecretary.presentation.alltasks;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.UiStyle;

/** Fixed hierarchies for step rows and transient insertion targets. */
final class AllTasksStepViewHolder extends AllTasksRowViewHolder {
    private final AllTasksRowUi ui;
    private final AllTasksRow.Kind kind;
    private LinearLayout shell;
    private LinearLayout pill;
    private ImageButton handle;
    private TextView title;
    private FrameLayout target;
    private View targetLine;

    AllTasksStepViewHolder(ViewGroup parent, AllTasksRow.Kind kind, AllTasksRowUi ui) {
        super(parent);
        this.kind = kind;
        this.ui = ui;
        if (kind == AllTasksRow.Kind.STEP) buildStep();
        else if (kind == AllTasksRow.Kind.STEP_TARGET) buildTarget();
        else throw new IllegalArgumentException("Unsupported step row " + kind);
    }

    private void buildStep() {
        shell = ui.row();
        shell.setPadding(ui.style.dp(8), 0, ui.style.dp(8), ui.style.dp(4));
        pill = ui.row();
        pill.setPadding(ui.style.dp(8), ui.style.dp(3), ui.style.dp(8), ui.style.dp(3));
        handle = ui.icon(R.drawable.ic_drag_handle, R.string.all_drag_step);
        pill.addView(handle, new LinearLayout.LayoutParams(ui.style.dp(44), ui.style.dp(44)));
        title = ui.style.sans("", 17, Color.TRANSPARENT, false);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setMinHeight(ui.style.dp(44));
        pill.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(pill, new LinearLayout.LayoutParams(-1, -2));
        root.addView(shell, new FrameLayout.LayoutParams(-1, -2));
    }

    private void buildTarget() {
        target = new FrameLayout(ui.context);
        targetLine = new View(ui.context);
        FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(-1, ui.style.dp(2));
        lineParams.gravity = Gravity.CENTER_VERTICAL;
        lineParams.setMargins(ui.style.dp(16), 0, ui.style.dp(16), 0);
        target.addView(targetLine, lineParams);
        root.addView(target, new FrameLayout.LayoutParams(-1, -1));
    }

    @Override void bind(AllTasksRow row, AllTasksUiState state,
                        DayPalette palette, boolean dragActive) {
        prepare(row, dragActive, ui);
        if (kind == AllTasksRow.Kind.STEP) bindStep(row, palette);
        else bindTarget(palette, dragActive);
    }

    private void bindStep(AllTasksRow row, DayPalette palette) {
        shell.setBackground(new AllTasksCardDrawable(ui.style, palette,
                AllTasksCardDrawable.Segment.MIDDLE));
        pill.setBackground(ui.ripple(ui.style.pill(UiStyle.alpha(palette.leaf1, .72f), 18),
                18, palette));
        handle.setVisibility(row.task.archived ? View.GONE : View.VISIBLE);
        handle.setColorFilter(palette.dot);
        title.setTextColor(palette.ink);
        title.setText(ui.highlighter.highlight(row.step.text, row.task.needle, palette));
        pill.setOnClickListener(view -> ui.listener.onEditStep(row.taskId, row.step.id));
        pill.setContentDescription(ui.context.getString(R.string.a11y_step_row, row.step.text));
    }

    private void bindTarget(DayPalette palette, boolean dragActive) {
        target.setBackground(new AllTasksCardDrawable(ui.style, palette,
                AllTasksCardDrawable.Segment.MIDDLE));
        targetLine.setBackgroundColor(palette.light);
        targetLine.setVisibility(dragActive ? View.VISIBLE : View.GONE);
        target.setContentDescription(dragActive
                ? ui.context.getString(R.string.a11y_step_drop_target) : null);
    }
}
