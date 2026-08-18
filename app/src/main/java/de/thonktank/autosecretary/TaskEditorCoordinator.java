package de.thonktank.autosecretary;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import java.time.LocalDate;

/** Owns mounting and binding the full-screen task editor. */
public final class TaskEditorCoordinator {
    private final Context context;
    private final FrameLayout root;
    private final View dashboard;
    private final TaskEditorView.Listener listener;
    private TaskEditorView editor;
    private int topInset;
    private int bottomInset;

    public TaskEditorCoordinator(Context context, FrameLayout root, View dashboard,
                                 TaskEditorView.Listener listener) {
        this.context = context;
        this.root = root;
        this.dashboard = dashboard;
        this.listener = listener;
    }

    public void setInsets(int top, int bottom) {
        topInset = top;
        bottomInset = bottom;
        if (editor != null) editor.setPadding(0, topInset, 0, bottomInset);
    }

    public void render(EditorUiState state, DayPalette palette, LocalDate today) {
        if (!state.open) {
            unmount();
            dashboard.setVisibility(View.VISIBLE);
            return;
        }
        dashboard.setVisibility(View.INVISIBLE);
        if (state.loading) return;
        if (editor == null) {
            editor = new TaskEditorView(context, listener);
            editor.setPadding(0, topInset, 0, bottomInset);
            root.addView(editor, new FrameLayout.LayoutParams(-1, -1));
        }
        editor.bind(state, palette, today);
    }

    public boolean handleBack() {
        return editor != null && editor.handleBack();
    }

    private void unmount() {
        if (editor == null) return;
        root.removeView(editor);
        editor = null;
    }
}
