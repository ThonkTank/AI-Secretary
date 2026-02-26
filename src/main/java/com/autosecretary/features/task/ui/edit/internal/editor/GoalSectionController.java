package com.autosecretary.features.task.ui.edit.internal.editor;

import android.graphics.Color;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;

import androidx.annotation.DimenRes;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.R;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;

public class GoalSectionController {

    private static final int GOAL_COLOR_COLUMN_COUNT = 5;
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

        this.goalIconView.setText(editState.goalIcon != null
            ? editState.goalIcon
            : TaskCore.DEFAULT_GOAL_ICON);
        this.selectedGoalColorHex = editState.goalColorHex != null
            ? editState.goalColorHex
            : TaskCore.DEFAULT_GOAL_COLOR_HEX;

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

            try {
                swatch.setBackgroundColor(Color.parseColor(hex));
            } catch (IllegalArgumentException ex) {
                continue;
            }

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
        for (int i = 0; i < goalColorGrid.getChildCount(); i++) {
            View swatch = goalColorGrid.getChildAt(i);
            Object tag = swatch.getTag();
            boolean selected = tag instanceof String && ((String) tag).equals(selectedGoalColorHex);
            swatch.setScaleX(selected ? 1.25f : 1.0f);
            swatch.setScaleY(selected ? 1.25f : 1.0f);
            swatch.setAlpha(selected ? 1.0f : 0.75f);
        }
    }

    private int dimenPx(@DimenRes int dimenResId) {
        return fragment.requireContext().getResources().getDimensionPixelSize(dimenResId);
    }
}
