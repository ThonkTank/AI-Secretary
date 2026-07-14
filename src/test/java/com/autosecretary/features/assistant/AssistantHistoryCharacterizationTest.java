package com.autosecretary.features.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.autosecretary.features.assistant.application.AssistantChatUseCase;
import com.autosecretary.features.assistant.application.AssistantChatUseCase.AssistantTurn;
import com.autosecretary.features.assistant.application.AssistantProposals.PendingProposal;
import com.autosecretary.features.assistant.application.AssistantProposals.RecipesProposal;
import com.autosecretary.features.assistant.application.ConfirmAssistantProposalUseCase;
import com.autosecretary.features.assistant.ui.AssistantUiState;
import com.autosecretary.features.assistant.ui.AssistantUiState.ExchangeItem;
import com.autosecretary.features.assistant.ui.AssistantUiState.ProposalStatus;
import com.autosecretary.features.assistant.ui.AssistantUiState.Status;
import com.autosecretary.features.assistant.ui.AssistantViewModel;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.function.Consumer;

/**
 * Characterizes the assistant screen's session chat history and confirm flow: each send appends an
 * exchange with its parked proposals, confirming a proposal marks it APPLIED, and {@code newChat}
 * clears the history. Reads the single immutable {@link AssistantUiState} the ViewModel publishes.
 * Uses stub use-cases so no network/DB is exercised.
 */
public final class AssistantHistoryCharacterizationTest extends AutoSecretaryRobolectricTest {

    private StubChatUseCase chat;
    private StubConfirmUseCase confirm;
    private AssistantViewModel viewModel;

    @Before
    public void setUp() {
        chat = new StubChatUseCase();
        confirm = new StubConfirmUseCase();
        viewModel = new AssistantViewModel(chat, confirm, null);
    }

    private AssistantUiState state() {
        return viewModel.getState().getValue();
    }

    /** Invariant: two sends leave two exchanges; confirming the first proposal marks it APPLIED. */
    @Test
    public void sendConfirmSendRetainsHistoryAndMarksAppliedProposal() {
        viewModel.send("lege ein Rezept an", null, null, null);
        assertEquals(1, state().exchanges().size());
        assertEquals(Status.ANSWERED, state().exchanges().get(0).status());

        ExchangeItem first = state().exchanges().get(0);
        assertEquals(ProposalStatus.PENDING, first.proposals().get(0).status());

        viewModel.confirm(first.id(), 0, summary -> {}, e -> {});
        assertEquals(ProposalStatus.APPLIED, state().exchanges().get(0).proposals().get(0).status());

        viewModel.send("und noch eins", null, null, null);
        assertEquals(2, state().exchanges().size());
        assertEquals(ProposalStatus.APPLIED, state().exchanges().get(0).proposals().get(0).status());
        assertEquals(ProposalStatus.PENDING, state().exchanges().get(1).proposals().get(0).status());
    }

    /** Invariant: newChat clears both the on-screen history and the underlying conversation. */
    @Test
    public void newChatClearsHistoryAndConversation() {
        viewModel.send("hallo", null, null, null);
        assertFalse(state().exchanges().isEmpty());

        viewModel.newChat();
        assertTrue(state().exchanges().isEmpty());
        assertTrue("conversation cleared", chat.cleared);
    }

    /** Invariant: an in-flight send is durable UI state and rejects a second send. */
    @Test
    public void asyncSendCreatesPendingExchangeAndRejectsSecondSend() {
        ManualChatUseCase manualChat = new ManualChatUseCase();
        viewModel = new AssistantViewModel(manualChat, confirm, null);
        viewModel.setDraftText("Pflege diese Aufgaben ein");
        viewModel.setPendingAttachment("aufgaben.md", "text/markdown", "- [ ] A".getBytes());

        assertTrue(viewModel.sendDraft());

        assertTrue("send marked in-flight", viewModel.isSending());
        assertTrue("send marked in-flight in state", state().sending());
        assertEquals("draft cleared after pending exchange is stored", "", viewModel.getDraftText());
        assertFalse("attachment moved into pending exchange", viewModel.hasPendingAttachment());
        assertEquals(1, state().exchanges().size());
        assertEquals(Status.PENDING, state().exchanges().get(0).status());
        assertEquals("Pflege diese Aufgaben ein", state().exchanges().get(0).userText());
        assertEquals("aufgaben.md", state().exchanges().get(0).attachmentName());

        assertFalse("second send rejected while first is running",
                viewModel.send("zweite Nachricht", null, null, null));

        manualChat.succeed(new AssistantTurn("Vorschlag steht bereit.", "", List.of()));

        assertFalse(viewModel.isSending());
        assertEquals(Status.ANSWERED, state().exchanges().get(0).status());
        assertEquals("Vorschlag steht bereit.", state().exchanges().get(0).answerText());
    }

    /** Invariant: an async failure replaces the pending exchange and frees the next send. */
    @Test
    public void asyncSendErrorReleasesInputAndKeepsOriginalMessageVisible() {
        ManualChatUseCase manualChat = new ManualChatUseCase();
        viewModel = new AssistantViewModel(manualChat, confirm, null);
        viewModel.setDraftText("Importiere die Aufgaben");
        viewModel.setPendingAttachment("aufgaben.md", "text/markdown", "- [ ] A".getBytes());

        assertTrue(viewModel.sendDraft());
        manualChat.fail("Timeout beim Claude-Request");

        assertFalse(viewModel.isSending());
        ExchangeItem exchange = state().exchanges().get(0);
        assertEquals(Status.ERROR, exchange.status());
        assertEquals("Importiere die Aufgaben", exchange.userText());
        assertEquals("aufgaben.md", exchange.attachmentName());
        assertEquals("Timeout beim Claude-Request", exchange.errorMessage());

        assertTrue("next send accepted after failure",
                viewModel.send("nochmal", null, null, null));
    }

    /**
     * Invariant: a progress tick replaces only the pending (last) item; earlier completed items keep
     * their object identity across the publish. The RecyclerView adapter's DiffUtil relies on this to
     * rebind exactly one row per streamed tick instead of the whole history.
     */
    @Test
    public void progressTickReplacesOnlyPendingItem() {
        ManualChatUseCase manualChat = new ManualChatUseCase();
        viewModel = new AssistantViewModel(manualChat, confirm, null);

        viewModel.send("erste", null, null, null);
        manualChat.succeed(new AssistantTurn("Antwort eins.", "", List.of()));
        ExchangeItem completed = state().exchanges().get(0);

        viewModel.send("zweite", null, null, null); // stays pending; manualChat holds the callbacks
        List<ExchangeItem> before = state().exchanges();
        assertEquals(2, before.size());
        ExchangeItem pendingBefore = before.get(1);

        manualChat.progress("Prüfe vorhandene Tasks…");
        List<ExchangeItem> after = state().exchanges();

        assertEquals(2, after.size());
        assertSame("completed item keeps its identity across the tick", completed, after.get(0));
        assertNotSame("pending item is replaced", pendingBefore, after.get(1));
        assertEquals("progress text landed on the pending item",
                "Prüfe vorhandene Tasks…", after.get(1).progressText());
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
        private Consumer<String> progress;

        ManualChatUseCase() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public void send(String userText, Attachment attachment, boolean thinkingEnabled,
                         Consumer<AssistantTurn> onResult, Consumer<String> onError,
                         Consumer<String> onProgress) {
            this.result = onResult;
            this.error = onError;
            this.progress = onProgress;
        }

        void succeed(AssistantTurn turn) {
            result.accept(turn);
        }

        void fail(String message) {
            error.accept(message);
        }

        void progress(String text) {
            progress.accept(text);
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
