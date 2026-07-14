package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.autosecretary.features.task.application.assistant.AssistantChatUseCase;
import com.autosecretary.features.task.application.assistant.AssistantChatUseCase.AssistantTurn;
import com.autosecretary.features.task.application.assistant.AssistantProposals.PendingProposal;
import com.autosecretary.features.task.application.assistant.AssistantProposals.RecipesProposal;
import com.autosecretary.features.task.application.assistant.ConfirmAssistantProposalUseCase;
import com.autosecretary.features.task.ui.assistant.TaskAssistantViewModel;
import com.autosecretary.features.task.ui.assistant.TaskAssistantViewModel.AssistantExchange;
import com.autosecretary.features.task.ui.assistant.TaskAssistantViewModel.ProposalHandle;
import com.autosecretary.features.task.ui.assistant.TaskAssistantViewModel.ProposalStatus;
import com.autosecretary.features.task.ui.assistant.TaskAssistantViewModel.Status;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Characterizes the assistant screen's session chat history and confirm flow: each send appends an
 * exchange with its parked proposals, confirming a proposal marks it APPLIED, and {@code newChat}
 * clears the history. Uses stub use-cases so no network/DB is exercised.
 */
public final class TaskAssistantHistoryCharacterizationTest extends AutoSecretaryRobolectricTest {

    private StubChatUseCase chat;
    private StubConfirmUseCase confirm;
    private TaskAssistantViewModel viewModel;

    @Before
    public void setUp() {
        chat = new StubChatUseCase();
        confirm = new StubConfirmUseCase();
        viewModel = new TaskAssistantViewModel(chat, confirm, null);
    }

    /** Invariant: two sends leave two exchanges; confirming the first proposal marks it APPLIED. */
    @Test
    public void sendConfirmSendRetainsHistoryAndMarksAppliedProposal() {
        List<AssistantExchange> answered = new ArrayList<>();
        viewModel.send("lege ein Rezept an", null, null, null, answered::add, e -> {});
        assertEquals(1, viewModel.getHistory().size());
        assertEquals(Status.ANSWERED, viewModel.getHistory().get(0).status());

        ProposalHandle handle = viewModel.getHistory().get(0).proposals().get(0);
        assertEquals(ProposalStatus.PENDING, handle.status());

        viewModel.confirm(handle, summary -> {}, e -> {});
        assertEquals(ProposalStatus.APPLIED, handle.status());

        viewModel.send("und noch eins", null, null, null, answered::add, e -> {});
        assertEquals(2, viewModel.getHistory().size());
        assertEquals(ProposalStatus.APPLIED, viewModel.getHistory().get(0).proposals().get(0).status());
        assertEquals(ProposalStatus.PENDING, viewModel.getHistory().get(1).proposals().get(0).status());
    }

    /** Invariant: newChat clears both the on-screen history and the underlying conversation. */
    @Test
    public void newChatClearsHistoryAndConversation() {
        viewModel.send("hallo", null, null, null, e -> {}, e -> {});
        assertTrue(!viewModel.getHistory().isEmpty());

        viewModel.newChat();
        assertTrue(viewModel.getHistory().isEmpty());
        assertTrue("conversation cleared", chat.cleared);
    }

    /** Invariant: an in-flight send is durable UI state and rejects a second send. */
    @Test
    public void asyncSendCreatesPendingExchangeAndRejectsSecondSend() {
        ManualChatUseCase manualChat = new ManualChatUseCase();
        viewModel = new TaskAssistantViewModel(manualChat, confirm, null);
        viewModel.setDraftText("Pflege diese Aufgaben ein");
        viewModel.setPendingAttachment("aufgaben.md", "text/markdown", "- [ ] A".getBytes());

        List<AssistantExchange> answered = new ArrayList<>();
        assertTrue(viewModel.sendDraft(answered::add, e -> {}));

        assertTrue("send marked in-flight", viewModel.isSending());
        assertEquals("draft cleared after pending exchange is stored", "", viewModel.getDraftText());
        assertNull("attachment moved into pending exchange", viewModel.getPendingAttachment());
        assertEquals(1, viewModel.getHistory().size());
        assertEquals(Status.PENDING, viewModel.getHistory().get(0).status());
        assertEquals("Pflege diese Aufgaben ein", viewModel.getHistory().get(0).userText());
        assertEquals("aufgaben.md", viewModel.getHistory().get(0).attachmentName());

        assertFalse("second send rejected while first is running",
                viewModel.send("zweite Nachricht", null, null, null, answered::add, e -> {}));

        manualChat.succeed(new AssistantTurn("Vorschlag steht bereit.", "", List.of()));

        assertFalse(viewModel.isSending());
        assertEquals(1, answered.size());
        assertEquals(Status.ANSWERED, viewModel.getHistory().get(0).status());
        assertEquals("Vorschlag steht bereit.", viewModel.getHistory().get(0).answerText());
    }

    /** Invariant: an async failure replaces the pending exchange and frees the next send. */
    @Test
    public void asyncSendErrorReleasesInputAndKeepsOriginalMessageVisible() {
        ManualChatUseCase manualChat = new ManualChatUseCase();
        viewModel = new TaskAssistantViewModel(manualChat, confirm, null);
        viewModel.setDraftText("Importiere die Aufgaben");
        viewModel.setPendingAttachment("aufgaben.md", "text/markdown", "- [ ] A".getBytes());

        List<AssistantExchange> errors = new ArrayList<>();
        assertTrue(viewModel.sendDraft(e -> {}, errors::add));
        manualChat.fail("Timeout beim Claude-Request");

        assertFalse(viewModel.isSending());
        assertEquals(1, errors.size());
        AssistantExchange exchange = viewModel.getHistory().get(0);
        assertEquals(Status.ERROR, exchange.status());
        assertEquals("Importiere die Aufgaben", exchange.userText());
        assertEquals("aufgaben.md", exchange.attachmentName());
        assertEquals("Timeout beim Claude-Request", exchange.errorMessage());

        assertTrue("next send accepted after failure",
                viewModel.send("nochmal", null, null, null, e -> {}, e -> {}));
    }

    /** Delivers a canned answer with one parked recipe proposal, standing in for the chat engine. */
    private static final class StubChatUseCase extends AssistantChatUseCase {
        boolean cleared;

        StubChatUseCase() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public void send(String userText, Attachment attachment, boolean thinkingEnabled,
                         Consumer<AssistantTurn> onResult, Consumer<String> onError,
                         Consumer<String> onProgress) {
            PendingProposal proposal = new RecipesProposal(List.of());
            onResult.accept(new AssistantTurn("Erledigt.", "", List.of(proposal)));
        }

        @Override
        public void clearConversation() {
            cleared = true;
        }
    }

    /** Captures callbacks so tests can keep a send in-flight until they explicitly complete it. */
    private static final class ManualChatUseCase extends AssistantChatUseCase {
        private Consumer<AssistantTurn> result;
        private Consumer<String> error;

        ManualChatUseCase() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public void send(String userText, Attachment attachment, boolean thinkingEnabled,
                         Consumer<AssistantTurn> onResult, Consumer<String> onError,
                         Consumer<String> onProgress) {
            this.result = onResult;
            this.error = onError;
        }

        void succeed(AssistantTurn turn) {
            result.accept(turn);
        }

        void fail(String message) {
            error.accept(message);
        }
    }

    /** Confirms synchronously with a canned summary. */
    private static final class StubConfirmUseCase extends ConfirmAssistantProposalUseCase {
        StubConfirmUseCase() {
            super(null, null, null, null, null);
        }

        @Override
        public void confirm(PendingProposal proposal, Consumer<String> onDone, Consumer<String> onError) {
            assertSame(RecipesProposal.class, proposal.getClass());
            onDone.accept("1 Rezept(e) gespeichert.");
        }
    }
}
