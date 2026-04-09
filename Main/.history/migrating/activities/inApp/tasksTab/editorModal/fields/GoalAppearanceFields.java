package activities.inApp.tasksTab.editorModal.fields;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import static activities.generic.ViewHelper.*;

import com.autosecretary.R;

import java.util.function.BooleanSupplier;

import entities.TrackedItem;

/**
 * Domain-Gruppe: Goal-Icon (Emoji-Eingabe) und Farb-Grid (10 Farben).
 * Keine Visibility-Effekte auf andere Gruppen.
 */
public class GoalAppearanceFields implements FieldGroup {

    private static final String[] GOAL_COLORS = {
        "#FFE53935", "#FFD81B60", "#FF8E24AA", "#FF5E35B1", "#FF1E88E5",
        "#FF00ACC1", "#FF00897B", "#FF43A047", "#FFFB8C00", "#FF6D4C41"
    };

    private final Context context;
    private final BooleanSupplier suppressCheck;

    private EditText goalIconField;
    private View goalIconRow;
    private View goalColorRow;
    private LinearLayout colorGrid;

    private String selectedColor = null;

    public GoalAppearanceFields(Context context, View root, BooleanSupplier suppressCheck) {
        this.context = context;
        this.suppressCheck = suppressCheck;
        bind(root);
    }

    private void bind(View root) {
        goalIconRow = root.findViewById(R.id.row_goal_icon);
        goalIconField = root.findViewById(R.id.field_goal_icon);
        goalColorRow = root.findViewById(R.id.row_goal_color);
        colorGrid = root.findViewById(R.id.color_grid);
        buildColorGrid();
    }

    private void buildColorGrid() {
        int size = dp(context, 32);
        int margin = dp(context, 4);
        for (String hex : GOAL_COLORS) {
            View swatch = new View(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, margin, margin, margin);
            swatch.setLayoutParams(lp);
            try {
                swatch.setBackground(roundedBg(context, Color.parseColor(hex), 4));
            } catch (IllegalArgumentException e) {
                continue;
            }
            swatch.setOnClickListener(v -> {
                if (suppressCheck.getAsBoolean()) return;
                selectedColor = hex;
                highlightSelectedColor(hex);
            });
            swatch.setTag(hex);
            colorGrid.addView(swatch);
        }
    }

    // ========================================================================
    // FieldGroup
    // ========================================================================

    @Override
    public void populate(TrackedItem item) {
        if (item != null) {
            goalIconField.setText(item.goalIcon != null ? item.goalIcon : "");
            selectedColor = item.goalColor;
        } else {
            goalIconField.setText("");
            selectedColor = null;
        }
        highlightSelectedColor(selectedColor);
    }

    @Override
    public void apply(TrackedItem.Builder builder) {
        String icon = goalIconField.getText().toString().trim();
        if (!icon.isEmpty()) builder.goalIcon(icon);
        if (selectedColor != null) builder.goalColor(selectedColor);
    }

    @Override
    public void updateVisibility(VisibilityFlags flags) {
        int v = flags.showGoalAppearance() ? View.VISIBLE : View.GONE;
        goalIconRow.setVisibility(v);
        goalColorRow.setVisibility(v);
    }

    // ========================================================================
    // HELPER
    // ========================================================================

    private void highlightSelectedColor(String selected) {
        for (int i = 0; i < colorGrid.getChildCount(); i++) {
            View child = colorGrid.getChildAt(i);
            String hex = (String) child.getTag();
            float scale = hex.equals(selected) ? 1.3f : 1.0f;
            child.setScaleX(scale);
            child.setScaleY(scale);
        }
    }
}
