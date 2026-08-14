package com.autosecretary.ui.ai;

import com.autosecretary.application.ai.BulkChangeProposal;
import com.autosecretary.application.model.ModelDownloadProgress;
import com.autosecretary.application.model.ModelDownloadTicket;

/** Mutually exclusive local-AI states. */
public sealed interface AiUiState permits AiUiState.Missing, AiUiState.Downloading,
        AiUiState.Ready, AiUiState.Inferring, AiUiState.ProposalReady, AiUiState.Error {
    enum Operation { NONE, INSTALL, INFERENCE }

    default boolean busy() { return this instanceof Downloading || this instanceof Inferring; }
    default Operation operation() {
        return this instanceof Downloading ? Operation.INSTALL
                : this instanceof Inferring ? Operation.INFERENCE : Operation.NONE;
    }
    default boolean modelReady() {
        return this instanceof Ready || this instanceof Inferring
                || this instanceof ProposalReady
                || this instanceof Error error && error.modelReady();
    }
    default BulkChangeProposal proposal() {
        return this instanceof ProposalReady ready ? ready.proposal() : null;
    }
    default String instruction() {
        return this instanceof Inferring value ? value.instruction()
                : this instanceof ProposalReady value ? value.instruction()
                : this instanceof Error value ? value.instruction() : "";
    }
    default String error() { return this instanceof Error value ? value.detail() : null; }

    record Missing(long sizeBytes) implements AiUiState { }
    record Downloading(
            ModelDownloadTicket ticket,
            ModelDownloadProgress progress,
            long sizeBytes,
            boolean openEditorAfter) implements AiUiState { }
    record Ready() implements AiUiState { }
    record Inferring(String instruction) implements AiUiState { }
    record ProposalReady(
            BulkChangeProposal proposal, String instruction) implements AiUiState { }
    record Error(
            String detail,
            boolean modelReady,
            long sizeBytes,
            String instruction) implements AiUiState { }
}
