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
    private TaskEditorView editor;
    private EditorUiState state;
    private boolean dismissed;
    private String deletedTaskId;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new FrameLayout(this));
    }

    public void mount(EditorUiState initial) {
        state = initial;
        dismissed = false;
        deletedTaskId = null;
        editor = new TaskEditorView(this, this);
        setContentView(editor);
        editor.bind(initial, DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT),
                LocalDate.of(2026, 8, 23));
    }

    public TaskEditorView editor() { return editor; }
    public EditorUiState editorState() { return state; }
    public boolean dismissed() { return dismissed; }
    public String deletedTaskId() { return deletedTaskId; }
    public AlertDialog prompt() { return editor == null ? null : editor.promptForTest(); }

    @Override public void onBackPressed() {
        if (editor != null && editor.handleBack()) return;
        super.onBackPressed();
    }

    @Override public void onDraftChanged(EditorUiState draft) { state = draft; }
    @Override public void onSave(EditorUiState draft) { state = draft; }
    @Override public void onDelete(String taskId) { deletedTaskId = taskId; }
    @Override public void onDismiss() { dismissed = true; }
}
