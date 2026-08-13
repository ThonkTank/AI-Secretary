package com.autosecretary.ui.ai;

import com.autosecretary.application.ai.BulkChangeProposal;

public record AiUiState(
        boolean busy,
        Operation operation,
        boolean modelReady,
        BulkChangeProposal proposal,
        long proposalId,
        long openEditorId,
        String instruction,
        String error) {
    public enum Operation { NONE, INSTALL, INFERENCE }

    public static AiUiState initial(boolean modelReady) {
        return new AiUiState(false, Operation.NONE, modelReady, null, 0, 0, "", null);
    }
}
