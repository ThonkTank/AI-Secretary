package com.autosecretary.ui.settings;

public sealed interface PlanningSettingsUiState
        permits PlanningSettingsUiState.Closed, PlanningSettingsUiState.Editing,
        PlanningSettingsUiState.Saving, PlanningSettingsUiState.Failed {
    record Closed() implements PlanningSettingsUiState { }
    record Editing(PlanningSettingsEditorState editor) implements PlanningSettingsUiState { }
    record Saving(PlanningSettingsEditorState editor) implements PlanningSettingsUiState { }
    record Failed(PlanningSettingsEditorState editor, String message)
            implements PlanningSettingsUiState { }
}
