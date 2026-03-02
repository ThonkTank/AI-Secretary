package com.autosecretary.features.task.ui.edit.internal.editor;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;

import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.shared.ui.ColorUtil;
import com.autosecretary.features.task.ui.edit.state.TaskEditDefaults;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;

/**
 * Manages the goal-appearance section of the task-edit form: a free-text emoji/icon
 * field and a fixed colour-palette grid where the user picks a highlight colour.
 *
 * <p>The selected colour is stored internally as a hex string and retrieved via
 * {@link #getSelectedGoalColorHex()} when the form is saved.
 */
public class GoalSectionController {

    private static final int GOAL_COLOR_COLUMN_COUNT = 5;
    private static final float SELECTED_SCALE = 1.25f;
    private static final float DESELECTED_SCALE = 1.0f;
    private static final float SELECTED_ALPHA = 1.0f;
    private static final float DESELECTED_ALPHA = 0.75f;
    private final Fragment fragment;
    private final EditText goalIconView;
    private final GridLayout goalColorGrid;
    private final String[] goalColors;
    private String selectedGoalColorHex;

    public GoalSectionController(Fragment fragment, View rootView, TaskEditState editState) {
        this.fragment = fragment;
        this.goalIconView = rootView.findViewById(R.id.EditGoalIcon);
        this.goalColorGrid = rootView.findViewById(R.id.GoalColorGrid);
        this.goalColors = fragment.requireContext().getResources()
                .getStringArray(R.array.task_goal_palette);

        // goalIcon and goalColorHex are never null; TaskEditState initializes them to defaults,
        // and TaskEditStateMapper.fromTask() uses Objects.requireNonNullElse() to ensure non-null values.
        this.goalIconView.setText(editState.goalIcon);
        this.selectedGoalColorHex = editState.goalColorHex;

        buildGoalColorGrid();
    }

    /** Returns the trimmed goal icon text, falling back to the default if empty. */
    public String getGoalIconText() {
        String text = goalIconView.getText().toString().trim();
        return text.isEmpty() ? TaskEditDefaults.GOAL_ICON : text;
    }

    public String getSelectedGoalColorHex() {
        return selectedGoalColorHex;
    }

    private void buildGoalColorGrid() {
        goalColorGrid.removeAllViews();
        goalColorGrid.setColumnCount(GOAL_COLOR_COLUMN_COUNT);

        Context ctx = fragment.requireContext();
        Resources res = ctx.getResources();
        int size = res.getDimensionPixelSize(R.dimen.task_editor_goal_color_size);
        int margin = res.getDimensionPixelSize(R.dimen.task_editor_goal_color_margin);
        float cornerRadius = margin * 2f;

        for (int i = 0; i < goalColors.length; i++) {
            String hex = goalColors[i];
            int colorNumber = i + 1;
            View swatch = new View(ctx);
            // spec(index, weight=1f): the 1f flex weight distributes equal space to all cells
            // in their row/column, so swatches fill the grid evenly without fixed pixel widths.
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(i / GOAL_COLOR_COLUMN_COUNT, 1f),
                GridLayout.spec(i % GOAL_COLOR_COLUMN_COUNT, 1f)
            );
            params.width = size;
            params.height = size;
            params.setMargins(margin, margin, margin, margin);
            swatch.setLayoutParams(params);

            GradientDrawable swatchBg = new GradientDrawable();
            swatchBg.setColor(ColorUtil.parseColorSafe(hex, Color.TRANSPARENT));
            swatchBg.setCornerRadius(cornerRadius);
            swatch.setBackground(swatchBg);
            swatch.setTag(hex);
            swatch.setContentDescription(fragment.getString(
                R.string.task_editor_goal_color_content_description, colorNumber));
            swatch.setOnClickListener(v -> {
                selectedGoalColorHex = hex;
                updateGoalColorSelection();
            });
            goalColorGrid.addView(swatch);
        }

        updateGoalColorSelection();
    }

    private void updateGoalColorSelection() {
        // Each swatch's tag was set to its hex string in buildGoalColorGrid(),
        // so matching tag == selectedGoalColorHex identifies the active swatch.
        for (int i = 0; i < goalColorGrid.getChildCount(); i++) {
            View swatch = goalColorGrid.getChildAt(i);
            boolean selected = selectedGoalColorHex.equals(swatch.getTag());
            float scale = selected ? SELECTED_SCALE : DESELECTED_SCALE;
            swatch.setScaleX(scale);
            swatch.setScaleY(scale);
            swatch.setAlpha(selected ? SELECTED_ALPHA : DESELECTED_ALPHA);
            int colorNumber = i + 1;
            swatch.setContentDescription(fragment.getString(
                selected ? R.string.task_editor_goal_color_selected_content_description
                         : R.string.task_editor_goal_color_content_description,
                colorNumber));
        }
    }
}
