package com.autosecretary.features.task.ui.assistant;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.autosecretary.features.task.application.assistant.AssistantChatUseCase;
import com.autosecretary.features.task.application.assistant.AssistantChatUseCase.Attachment;
import com.autosecretary.features.task.application.assistant.AssistantChatUseCase.AssistantTurn;
import com.autosecretary.features.task.application.assistant.AssistantProposals.PendingProposal;
import com.autosecretary.features.task.application.assistant.ConfirmAssistantProposalUseCase;
import com.autosecretary.features.task.application.UndoTaskChangesUseCase;
import com.autosecretary.features.task.ui.assistant.AssistantUiState.ExchangeItem;
import com.autosecretary.features.task.ui.assistant.AssistantUiState.ProposalItem;
import com.autosecretary.features.task.ui.assistant.AssistantUiState.ProposalStatus;
import com.autosecretary.features.task.ui.assistant.AssistantUiState.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ViewModel for the assistant tab. Delegates to the chat/confirm/undo use-cases and keeps a
 * session-scoped chat history of user→assistant exchanges. The history and the underlying
 * conversation live as long as the ViewModel (activity-scoped), so they survive tab switches and
 * die with the session.
 *
 * <p>The whole screen renders from one immutable {@link AssistantUiState} published on a single
 * {@link LiveData}. Every event replaces the state; the view diffs it. Draft text and the attachment
 * bytes are held outside the state (draft to avoid a TextWatcher feedback loop, bytes because they
 * are not render data); only the attachment's display name enters the state.
 */
public class TaskAssistantViewModel extends ViewModel {

    /** File attachment currently staged in the assistant input. */
    private record PendingAttachment(String displayName, String mimeType, byte[] bytes) {
    }

    private final AssistantChatUseCase chatUseCase;
    private final ConfirmAssistantProposalUseCase confirmUseCase;
    private final UndoTaskChangesUseCase undoUseCase;

    private final List<ExchangeItem> history = new ArrayList<>();
    private boolean thinkingEnabled = true;
    private String draftText = "";
    private PendingAttachment pendingAttachment;
    private boolean sending;
    private long pendingId = -1;
    private long nextId;

    private final MutableLiveData<AssistantUiState> state = new MutableLiveData<>();

    public TaskAssistantViewModel(AssistantChatUseCase chatUseCase,
                                  ConfirmAssistantProposalUseCase confirmUseCase,
                                  UndoTaskChangesUseCase undoUseCase) {
        this.chatUseCase = chatUseCase;
        this.confirmUseCase = confirmUseCase;
        this.undoUseCase = undoUseCase;
        publish();
    }

    /** The single immutable render state of the whole screen. */
    public LiveData<AssistantUiState> getState() {
        return state;
    }

    public boolean isThinkingEnabled() {
        return thinkingEnabled;
    }

    public void setThinkingEnabled(boolean enabled) {
        this.thinkingEnabled = enabled;
    }

    public String getDraftText() {
        return draftText;
    }

    public void setDraftText(String text) {
        this.draftText = text != null ? text : "";
    }

    public boolean hasPendingAttachment() {
        return pendingAttachment != null;
    }

    public void setPendingAttachment(String displayName, String mimeType, byte[] bytes) {
        pendingAttachment = new PendingAttachment(displayName, mimeType, bytes);
        publish();
    }

    public void clearPendingAttachment() {
        pendingAttachment = null;
        publish();
    }

    public boolean isSending() {
        return sending;
    }

    public boolean sendDraft() {
        PendingAttachment attachment = pendingAttachment;
        boolean accepted = send(draftText,
                attachment != null ? attachment.displayName() : null,
                attachment != null ? attachment.mimeType() : null,
                attachment != null ? attachment.bytes() : null);
        if (accepted) {
            draftText = "";
            pendingAttachment = null;
            publish();
        }
        return accepted;
    }

    /**
     * Sends a user message (optionally with a file attachment). A pending exchange is appended
     * immediately; on completion it is replaced with the answer or an error. Rejected (returns
     * {@code false}) while a previous send is still in flight.
     */
    public boolean send(String userText, String attachmentName, String attachmentMime, byte[] attachmentBytes) {
        if (sending) {
            return false;
        }
        String safeUserText = userText != null ? userText : "";
        Attachment attachment = attachmentBytes != null
                ? new Attachment(attachmentName, attachmentMime, attachmentBytes)
                : null;

        long id = nextId++;
        history.add(new ExchangeItem(id, safeUserText, attachmentName,
                null, null, null, List.of(), null, Status.PENDING));
        sending = true;
        pendingId = id;
        publish();

        try {
            chatUseCase.send(safeUserText, attachment, thinkingEnabled,
                    turn -> completePending(id, answered(id, safeUserText, attachmentName, turn)),
                    message -> completePending(id, new ExchangeItem(id, safeUserText, attachmentName,
                            null, null, null, List.of(), message, Status.ERROR)),
                    progressText -> updateProgress(id, progressText));
        } catch (RuntimeException e) {
            completePending(id, new ExchangeItem(id, safeUserText, attachmentName,
                    null, null, null, List.of(), e.getMessage(), Status.ERROR));
        }
        return true;
    }

    private static ExchangeItem answered(long id, String userText, String attachmentName, AssistantTurn turn) {
        List<ProposalItem> proposals = new ArrayList<>();
        for (PendingProposal proposal : turn.proposals()) {
            proposals.add(new ProposalItem(proposal, ProposalStatus.PENDING));
        }
        return new ExchangeItem(id, userText, attachmentName, turn.thinkingText(), turn.answerText(),
                null, List.copyOf(proposals), null, Status.ANSWERED);
    }

    private void updateProgress(long id, String progressText) {
        if (!sending || pendingId != id) {
            return;
        }
        int index = indexOf(id);
        if (index < 0) {
            return;
        }
        ExchangeItem pending = history.get(index);
        history.set(index, new ExchangeItem(pending.id(), pending.userText(), pending.attachmentName(),
                null, null, progressText, List.of(), null, Status.PENDING));
        publish();
    }

    private void completePending(long id, ExchangeItem completed) {
        if (pendingId != id) {
            return;
        }
        int index = indexOf(id);
        if (index >= 0) {
            history.set(index, completed);
        } else {
            history.add(completed);
        }
        sending = false;
        pendingId = -1;
        publish();
    }

    /**
     * Confirms (applies) the parked proposal at {@code proposalIndex} of the exchange with
     * {@code exchangeId}; on success the proposal is replaced with an {@link ProposalStatus#APPLIED}
     * copy and the state is republished.
     */
    public void confirm(long exchangeId, int proposalIndex, Consumer<String> onApplied, Consumer<String> onError) {
        int index = indexOf(exchangeId);
        if (index < 0 || proposalIndex < 0 || proposalIndex >= history.get(index).proposals().size()) {
            return;
        }
        PendingProposal proposal = history.get(index).proposals().get(proposalIndex).proposal();
        confirmUseCase.confirm(proposal, summary -> {
            markApplied(exchangeId, proposalIndex);
            onApplied.accept(summary);
        }, onError);
    }

    private void markApplied(long exchangeId, int proposalIndex) {
        int index = indexOf(exchangeId);
        if (index < 0) {
            return;
        }
        ExchangeItem exchange = history.get(index);
        List<ProposalItem> proposals = new ArrayList<>(exchange.proposals());
        if (proposalIndex >= proposals.size()) {
            return;
        }
        proposals.set(proposalIndex,
                new ProposalItem(proposals.get(proposalIndex).proposal(), ProposalStatus.APPLIED));
        history.set(index, new ExchangeItem(exchange.id(), exchange.userText(), exchange.attachmentName(),
                exchange.thinkingText(), exchange.answerText(), exchange.progressText(),
                List.copyOf(proposals), exchange.errorMessage(), exchange.status()));
        publish();
    }

    /** Clears the on-screen history and the underlying conversation. */
    public boolean newChat() {
        if (sending) {
            return false;
        }
        chatUseCase.clearConversation();
        history.clear();
        draftText = "";
        pendingAttachment = null;
        publish();
        return true;
    }

    public void undoLast(Consumer<Boolean> onDone) {
        if (undoUseCase == null) {
            onDone.accept(false);
            return;
        }
        undoUseCase.undoLast(undone -> {
            publish(); // hasUndo may have changed
            onDone.accept(undone);
        });
    }

    public boolean hasUndo() {
        return undoUseCase != null && undoUseCase.hasUndo();
    }

    private int indexOf(long id) {
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).id() == id) {
                return i;
            }
        }
        return -1;
    }

    private void publish() {
        state.setValue(new AssistantUiState(List.copyOf(history), sending,
                pendingAttachment != null ? pendingAttachment.displayName() : null, hasUndo()));
    }
}
