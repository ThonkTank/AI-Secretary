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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * ViewModel for the assistant tab. Delegates to the chat/confirm/undo use-cases and keeps a
 * session-scoped chat history of user→assistant exchanges. The history and the underlying
 * conversation live as long as the ViewModel (activity-scoped), so they survive tab switches and
 * die with the session. Holds no persistence, view, or {@code org.json} state itself.
 */
public class TaskAssistantViewModel extends ViewModel {

    /** Outcome of one exchange as shown in the chat history. */
    public enum Status { PENDING, ANSWERED, ERROR }

    /** Status of a single parked proposal card. */
    public enum ProposalStatus { PENDING, APPLIED }

    /** A parked proposal plus its mutable apply-state. */
    public static final class ProposalHandle {
        private final PendingProposal proposal;
        private ProposalStatus status = ProposalStatus.PENDING;

        ProposalHandle(PendingProposal proposal) {
            this.proposal = proposal;
        }

        public PendingProposal proposal() {
            return proposal;
        }

        public ProposalStatus status() {
            return status;
        }
    }

    /** File attachment currently staged in the assistant input. */
    public record PendingAttachment(String displayName, String mimeType, byte[] bytes) {
    }

    /** One entry in the chat history: a user message and the assistant's answer (or an error). */
    public static final class AssistantExchange {
        private final String userText;
        private final String attachmentName;
        private final String thinkingText;
        private final String answerText;
        private final List<ProposalHandle> proposals;
        private final String errorMessage;
        private final Status status;

        AssistantExchange(String userText, String attachmentName, String thinkingText, String answerText,
                          List<ProposalHandle> proposals, String errorMessage, Status status) {
            this.userText = userText;
            this.attachmentName = attachmentName;
            this.thinkingText = thinkingText;
            this.answerText = answerText;
            this.proposals = proposals;
            this.errorMessage = errorMessage;
            this.status = status;
        }

        public String userText() {
            return userText;
        }

        /** The attached file's display name, or {@code null} if none was attached. */
        public String attachmentName() {
            return attachmentName;
        }

        /** The assistant's thinking summary; {@code null}/blank when thinking was off or empty. */
        public String thinkingText() {
            return thinkingText;
        }

        public String answerText() {
            return answerText;
        }

        /** The parked proposals for this exchange (never null; empty when the assistant only answered). */
        public List<ProposalHandle> proposals() {
            return proposals;
        }

        /** The error message; {@code null} unless {@link #status()} is {@link Status#ERROR}. */
        public String errorMessage() {
            return errorMessage;
        }

        public Status status() {
            return status;
        }
    }

    private final AssistantChatUseCase chatUseCase;
    private final ConfirmAssistantProposalUseCase confirmUseCase;
    private final UndoTaskChangesUseCase undoUseCase;
    private final List<AssistantExchange> history = new ArrayList<>();
    private boolean thinkingEnabled = true;
    private String draftText = "";
    private PendingAttachment pendingAttachment;
    private boolean sending;
    private AssistantExchange pendingExchange;
    private AssistantExchange latestExchange;
    private String pendingProgressText;
    private final MutableLiveData<Integer> stateVersion = new MutableLiveData<>(0);
    private int stateCounter;

    public TaskAssistantViewModel(AssistantChatUseCase chatUseCase,
                                  ConfirmAssistantProposalUseCase confirmUseCase,
                                  UndoTaskChangesUseCase undoUseCase) {
        this.chatUseCase = chatUseCase;
        this.confirmUseCase = confirmUseCase;
        this.undoUseCase = undoUseCase;
    }

    /** The session chat history, oldest first. */
    public List<AssistantExchange> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public boolean isThinkingEnabled() {
        return thinkingEnabled;
    }

    public void setThinkingEnabled(boolean enabled) {
        this.thinkingEnabled = enabled;
    }

    public LiveData<Integer> getStateVersion() {
        return stateVersion;
    }

    public String getDraftText() {
        return draftText;
    }

    public void setDraftText(String text) {
        this.draftText = text != null ? text : "";
    }

    public PendingAttachment getPendingAttachment() {
        return pendingAttachment;
    }

    public void setPendingAttachment(String displayName, String mimeType, byte[] bytes) {
        pendingAttachment = new PendingAttachment(displayName, mimeType, bytes);
        notifyStateChanged();
    }

    public void clearPendingAttachment() {
        pendingAttachment = null;
        notifyStateChanged();
    }

    public boolean isSending() {
        return sending;
    }

    /**
     * The live progress label for the in-flight exchange (e.g. "Prüfe vorhandene Tasks…"), or
     * {@code null} when there is nothing more specific than the default pending label to show.
     */
    public String getPendingProgressText() {
        return pendingProgressText;
    }

    public AssistantExchange latestExchange() {
        return latestExchange;
    }

    public boolean sendDraft(Consumer<AssistantExchange> onResult, Consumer<AssistantExchange> onError) {
        PendingAttachment attachment = pendingAttachment;
        boolean accepted = send(draftText,
                attachment != null ? attachment.displayName() : null,
                attachment != null ? attachment.mimeType() : null,
                attachment != null ? attachment.bytes() : null,
                onResult, onError);
        if (accepted) {
            draftText = "";
            pendingAttachment = null;
            notifyStateChanged();
        }
        return accepted;
    }

    /**
     * Sends a user message (optionally with a file attachment). On success or failure a new exchange
     * is appended to the history and delivered to the caller so the UI can render it.
     */
    public boolean send(String userText, String attachmentName, String attachmentMime, byte[] attachmentBytes,
                        Consumer<AssistantExchange> onResult, Consumer<AssistantExchange> onError) {
        if (sending) {
            return false;
        }
        String safeUserText = userText != null ? userText : "";
        Attachment attachment = attachmentBytes != null
                ? new Attachment(attachmentName, attachmentMime, attachmentBytes)
                : null;

        AssistantExchange pending = new AssistantExchange(safeUserText, attachmentName,
                null, null, new ArrayList<>(), null, Status.PENDING);
        sending = true;
        pendingExchange = pending;
        latestExchange = pending;
        history.add(pending);
        notifyStateChanged();

        try {
            chatUseCase.send(safeUserText, attachment, thinkingEnabled,
                    turn -> {
                        AssistantExchange exchange = answered(safeUserText, attachmentName, turn);
                        completePending(pending, exchange);
                        onResult.accept(exchange);
                    },
                    message -> {
                        AssistantExchange exchange = new AssistantExchange(safeUserText, attachmentName,
                                null, null, new ArrayList<>(), message, Status.ERROR);
                        completePending(pending, exchange);
                        onError.accept(exchange);
                    },
                    progressText -> {
                        if (sending && pendingExchange == pending) {
                            pendingProgressText = progressText;
                            notifyStateChanged();
                        }
                    });
        } catch (RuntimeException e) {
            AssistantExchange exchange = new AssistantExchange(safeUserText, attachmentName,
                    null, null, new ArrayList<>(), e.getMessage(), Status.ERROR);
            completePending(pending, exchange);
            onError.accept(exchange);
        }
        return true;
    }

    private static AssistantExchange answered(String userText, String attachmentName, AssistantTurn turn) {
        List<ProposalHandle> handles = new ArrayList<>();
        for (PendingProposal proposal : turn.proposals()) {
            handles.add(new ProposalHandle(proposal));
        }
        return new AssistantExchange(userText, attachmentName, turn.thinkingText(), turn.answerText(),
                handles, null, Status.ANSWERED);
    }

    private void completePending(AssistantExchange pending, AssistantExchange completed) {
        if (pendingExchange != pending) {
            return;
        }
        int index = history.indexOf(pending);
        if (index >= 0) {
            history.set(index, completed);
        } else {
            history.add(completed);
        }
        sending = false;
        pendingExchange = null;
        pendingProgressText = null;
        latestExchange = completed;
        notifyStateChanged();
    }

    /** Confirms (applies) a parked proposal; marks it {@link ProposalStatus#APPLIED} on success. */
    public void confirm(ProposalHandle handle, Consumer<String> onApplied, Consumer<String> onError) {
        confirmUseCase.confirm(handle.proposal(), summary -> {
            handle.status = ProposalStatus.APPLIED;
            onApplied.accept(summary);
        }, onError);
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
        latestExchange = null;
        notifyStateChanged();
        return true;
    }

    public void undoLast(Consumer<Boolean> onDone) {
        if (undoUseCase == null) {
            onDone.accept(false);
            return;
        }
        undoUseCase.undoLast(onDone);
    }

    public boolean hasUndo() {
        if (undoUseCase == null) {
            return false;
        }
        return undoUseCase.hasUndo();
    }

    private void notifyStateChanged() {
        stateVersion.setValue(++stateCounter);
    }
}
