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
    private boolean deferredOpen;
    private boolean disposed;
    private EditorUiState pendingState;
    private DayPalette pendingPalette;
    private LocalDate pendingToday;

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
        if (disposed) return;
        if (!state.open) {
            deferredOpen = false;
            clearPendingOpen();
            unmount();
            dashboard.setVisibility(View.VISIBLE);
            return;
        }
        if (deferredOpen) {
            pendingState = state;
            pendingPalette = palette;
            pendingToday = today;
            return;
        }
        renderOpen(state, palette, today);
    }

    public void deferNextOpen() {
        if (!disposed) deferredOpen = true;
    }

    public void completeDeferredOpen() {
        if (disposed) return;
        deferredOpen = false;
        if (pendingState == null) return;
        EditorUiState state = pendingState;
        DayPalette palette = pendingPalette;
        LocalDate today = pendingToday;
        clearPendingOpen();
        renderOpen(state, palette, today);
    }

    public void dispose() {
        disposed = true;
        deferredOpen = false;
        clearPendingOpen();
    }

    private void renderOpen(EditorUiState state, DayPalette palette, LocalDate today) {
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

    private void clearPendingOpen() {
        pendingState = null;
        pendingPalette = null;
        pendingToday = null;
    }
}
