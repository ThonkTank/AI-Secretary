package de.thonktank.autosecretary;

import de.thonktank.autosecretary.ui.today.FocusStepRowView;

import android.view.View;
import android.view.ViewGroup;

/** Read-only queries over observable view state shared by rendering tests. */
final class ViewTestQueries {
    private ViewTestQueries() { }

    static int visibleFollowingStepRows(View root) {
        return Math.max(0, visibleStepRows(root) - 1);
    }

    private static int visibleStepRows(View root) {
        if (root.getVisibility() != View.VISIBLE) return 0;
        int rows = root instanceof FocusStepRowView ? 1 : 0;
        if (!(root instanceof ViewGroup)) return rows;
        ViewGroup group = (ViewGroup) root;
        for (int index = 0; index < group.getChildCount(); index++)
            rows += visibleStepRows(group.getChildAt(index));
        return rows;
    }
}
