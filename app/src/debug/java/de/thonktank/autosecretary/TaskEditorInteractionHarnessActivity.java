package de.thonktank.autosecretary;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.FrameLayout;

import java.time.LocalDate;
import java.time.LocalTime;

/** Debug-only on-device host for the task editor interaction contract. */
public final class TaskEditorInteractionHarnessActivity extends Activity
        implements TaskEditorView.Listener {
    private static final String EDITOR_STATE = "editor_state";
    private TaskEditorView editor;
    private EditorUiState state;
    private boolean dismissed;
    private String deletedTaskId;
    private int saveRequests;
    private int deleteRequests;
    private int dismissRequests;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PresentationTrace.emit("editor-host", "create", "saved=" + (savedInstanceState != null));
        setContentView(new FrameLayout(this));
        if (savedInstanceState != null) {
            Bundle restored = savedInstanceState.getBundle(EDITOR_STATE);
            if (restored != null) mount(EditorUiState.fromBundle(restored));
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (state != null) outState.putBundle(EDITOR_STATE, state.toBundle());
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        PresentationTrace.emit("editor-host", "destroy", "changing=" + isChangingConfigurations());
        super.onDestroy();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        PresentationTrace.emit("editor-host", "window-focus", "value=" + hasFocus);
    }

    public void mount(EditorUiState initial) {
        PresentationTrace.emit("editor-host", "mount", "page=" + initial.page);
        state = initial;
        dismissed = false;
        deletedTaskId = null;
        saveRequests = 0;
        deleteRequests = 0;
        dismissRequests = 0;
        editor = new TaskEditorView(this, this);
        setContentView(editor);
        editor.bind(initial, DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT),
                LocalDate.of(2026, 8, 23));
    }

    public TaskEditorView editor() { return editor; }
    public EditorUiState editorState() { return state; }
    public boolean dismissed() { return dismissed; }
    public String deletedTaskId() { return deletedTaskId; }
    public int saveRequests() { return saveRequests; }
    public int deleteRequests() { return deleteRequests; }
    public int dismissRequests() { return dismissRequests; }
    public AlertDialog prompt() { return editor == null ? null : editor.promptForTest(); }

    @Override public void onBackPressed() {
        if (editor != null && editor.handleBack()) return;
        super.onBackPressed();
    }

    @Override public void onDraftChanged(EditorUiState draft) { state = draft; }
    @Override public void onSave(EditorUiState draft) {
        saveRequests++;
        state = draft;
    }
    @Override public void onDelete(String taskId) {
        deleteRequests++;
        deletedTaskId = taskId;
    }
    @Override public void onDismiss() {
        dismissRequests++;
        dismissed = true;
    }
}
