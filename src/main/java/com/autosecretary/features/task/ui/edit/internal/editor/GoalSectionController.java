package com.autosecretary.features.task.ui.edit.internal.editor;

import android.graphics.Color;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;

import androidx.annotation.DimenRes;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.R;
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
    // Colors in #AARRGGBB format (alpha first) as required by Color.parseColor().
    // This differs from standard Android color resources (#RRGGBB or #AARRGGBB in XML).
    // FF = fully opaque alpha prefix; the remaining 6 hex digits are the RGB values.
    private static final String[] GOAL_COLORS = {
        "#FFE53935", "#FFD81B60", "#FF8E24AA", "#FF5E35B1", "#FF1E88E5",
        "#FF00ACC1", "#FF00897B", "#FF43A047", "#FFFB8C00", "#FF6D4C41"
    };

    private final DialogFragment fragment;
    private final EditText goalIconView;
    private final GridLayout goalColorGrid;
    private String selectedGoalColorHex;

    public GoalSectionController(DialogFragment fragment, View rootView, TaskEditState editState) {
        this.fragment = fragment;
        this.goalIconView = rootView.findViewById(R.id.EditGoalIcon);
        this.goalColorGrid = rootView.findViewById(R.id.GoalColorGrid);

        // goalIcon and goalColorHex are never null; TaskEditState initializes them to defaults,
        // and TaskEditStateMapper.fromTask() uses Objects.requireNonNullElse() to ensure non-null values.
        this.goalIconView.setText(editState.goalIcon);
        this.selectedGoalColorHex = editState.goalColorHex;

        buildGoalColorGrid();
    }

    public EditText getGoalIconView() {
        return goalIconView;
    }

    public String getSelectedGoalColorHex() {
        return selectedGoalColorHex;
    }

    private void buildGoalColorGrid() {
        goalColorGrid.removeAllViews();
        goalColorGrid.setColumnCount(GOAL_COLOR_COLUMN_COUNT);

        for (int i = 0; i < GOAL_COLORS.length; i++) {
            String hex = GOAL_COLORS[i];
            View swatch = new View(fragment.requireContext());
            // spec(index, weight=1f): the 1f flex weight distributes equal space to all cells
            // in their row/column, so swatches fill the grid evenly without fixed pixel widths.
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(i / GOAL_COLOR_COLUMN_COUNT, 1f),
                GridLayout.spec(i % GOAL_COLOR_COLUMN_COUNT, 1f)
            );
            int size = dimenPx(R.dimen.task_editor_goal_color_size);
            int margin = dimenPx(R.dimen.task_editor_goal_color_margin);
            params.width = size;
            params.height = size;
            params.setMargins(margin, margin, margin, margin);
            swatch.setLayoutParams(params);

            swatch.setBackgroundColor(Color.parseColor(hex));
            swatch.setTag(hex);
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
        }
    }

    private int dimenPx(@DimenRes int dimenResId) {
        return fragment.requireContext().getResources().getDimensionPixelSize(dimenResId);
    }
}
