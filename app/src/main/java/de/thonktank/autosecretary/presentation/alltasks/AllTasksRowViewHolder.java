package de.thonktank.autosecretary.presentation.alltasks;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import de.thonktank.autosecretary.DayPalette;

/** Base for fixed row hierarchies. Subclasses only mutate existing views from bind. */
abstract class AllTasksRowViewHolder extends RecyclerView.ViewHolder {
    final FrameLayout root;

    AllTasksRowViewHolder(@NonNull ViewGroup parent) {
        super(createRoot(parent));
        root = (FrameLayout) itemView;
    }

    private static FrameLayout createRoot(ViewGroup parent) {
        FrameLayout value = new FrameLayout(parent.getContext());
        value.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
        return value;
    }

    final void prepare(AllTasksRow row, boolean dragActive, AllTasksRowUi ui) {
        root.setLayoutParams(new RecyclerView.LayoutParams(-1,
                row.kind == AllTasksRow.Kind.STEP_TARGET
                        ? dragActive ? ui.style.dp(44) : 0 : -2));
        root.setAccessibilityDelegate(null);
        root.setContentDescription(null);
        root.setFocusable(false);
        root.setClickable(false);
    }

    abstract void bind(AllTasksRow row, AllTasksUiState state,
                       DayPalette palette, boolean dragActive);

    final View hierarchyAnchor() { return root.getChildAt(0); }
}
