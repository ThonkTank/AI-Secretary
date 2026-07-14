package com.autosecretary.features.assistant.ui;

import com.autosecretary.features.assistant.application.AssistantProposals.PendingProposal;

import java.util.List;

/**
 * The complete, immutable render state of the assistant screen, published as a single
 * {@code LiveData<AssistantUiState>}. Every event (send, progress tick, answer, confirm, new chat)
 * replaces the whole state; the view diffs it. Sub-states always change together, so one stream is
 * simpler than the many typed streams used elsewhere, and immutability lets a RecyclerView adapter
 * compare exchanges by object identity (unchanged items keep their reference across a publish).
 *
 * @param exchanges      the chat history, oldest first
 * @param sending        whether a send is in flight (blocks a second send)
 * @param attachmentName the staged attachment's display name, or {@code null} if none is staged
 * @param hasUndo        whether a task change is available to undo
 */
public record AssistantUiState(List<ExchangeItem> exchanges, boolean sending,
                               String attachmentName, boolean hasUndo) {

    /** Outcome of one exchange as shown in the chat history. */
    public enum Status { PENDING, ANSWERED, ERROR }

    /** Apply-state of a single parked proposal card. */
    public enum ProposalStatus { PENDING, APPLIED }

    /**
     * One entry in the chat history: a user message and the assistant's answer (or an error). Each
     * exchange has a stable {@code id} so the adapter can track it across state publishes.
     *
     * @param progressText live progress label shown while {@link Status#PENDING}; {@code null} otherwise
     * @param errorMessage set only when {@link Status#ERROR}
     */
    public record ExchangeItem(long id, String userText, String attachmentName, String thinkingText,
                               String answerText, String progressText, List<ProposalItem> proposals,
                               String errorMessage, Status status) {
    }

    /** A parked proposal plus its apply-state. Immutable: confirming replaces it, never mutates it. */
    public record ProposalItem(PendingProposal proposal, ProposalStatus status) {
    }
}
