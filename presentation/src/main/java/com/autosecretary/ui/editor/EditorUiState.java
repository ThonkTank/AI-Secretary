package com.autosecretary.ui.editor;

/** The editor is either closed, editable, saving, or displaying a failed save. */
public sealed interface EditorUiState
        permits EditorUiState.Closed, EditorUiState.Editing,
        EditorUiState.Saving, EditorUiState.Failed {
    record Closed() implements EditorUiState { }
    record Editing(ObligationEditorState editor) implements EditorUiState { }
    record Saving(ObligationEditorState editor) implements EditorUiState { }
    record Failed(ObligationEditorState editor, String message) implements EditorUiState { }
}
