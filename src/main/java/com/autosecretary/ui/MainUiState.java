package com.autosecretary.ui;

import com.autosecretary.application.DashboardData;
import com.autosecretary.ui.editor.ObligationEditorState;
import com.autosecretary.ui.settings.PlanningSettingsEditorState;

public record MainUiState(
        DashboardData dashboard,
        String surface,
        String filter,
        boolean loading,
        String error,
        long completionSignal,
        ObligationEditorState editor,
        PlanningSettingsEditorState planningEditor) {
    public static MainUiState initial(
            String surface,
            String filter,
            ObligationEditorState editor,
            PlanningSettingsEditorState planningEditor) {
        return new MainUiState(null, surface, filter, true, null, 0, editor, planningEditor);
    }
}
