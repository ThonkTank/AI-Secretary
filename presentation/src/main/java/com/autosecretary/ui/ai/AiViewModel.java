package com.autosecretary.ui.ai;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.autosecretary.application.ai.AiProposalGateway;
import com.autosecretary.application.ai.AiConsentPort;
import com.autosecretary.domain.WorkItem;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/** Retains model I/O and inference across configuration changes without retaining an Activity. */
public final class AiViewModel extends ViewModel {
    private final AiProposalGateway editor;
    private final AiConsentPort consent;
    private final Executor uiExecutor;
    private final MutableLiveData<AiUiState> state;
    private Future<?> running;
    private volatile boolean cleared;

    public AiViewModel(AiProposalGateway editor, AiConsentPort consent, Executor uiExecutor) {
        this.editor = editor;
        this.consent = consent;
        this.uiExecutor = uiExecutor;
        state = new MutableLiveData<>(AiUiState.initial(editor.hasModel()));
    }

    public LiveData<AiUiState> state() { return state; }

    public boolean termsAccepted() { return consent.accepted(); }

    public void acceptTermsAndInstall() {
        consent.accept();
        installModel(true);
    }

    public void installModel(boolean openEditorAfter) {
        if (current().busy() || current().modelReady()) return;
        start(AiUiState.Operation.INSTALL);
        running = editor.installModel(
                () -> dispatch(() -> state.setValue(new AiUiState(false,
                        AiUiState.Operation.NONE, true, null, current().proposalId(),
                        current().openEditorId() + (openEditorAfter ? 1 : 0),
                        current().instruction(), null))),
                this::postError);
    }

    public void propose(String instruction, List<WorkItem> currentItems) {
        if (current().busy() || !current().modelReady()) return;
        AiUiState value = current();
        state.setValue(new AiUiState(true, AiUiState.Operation.INFERENCE,
                value.modelReady(), null, value.proposalId(), value.openEditorId(),
                instruction, null));
        running = editor.propose(instruction, currentItems,
                proposal -> dispatch(() -> state.setValue(new AiUiState(false,
                        AiUiState.Operation.NONE, true, proposal, current().proposalId() + 1,
                        current().openEditorId(), current().instruction(), null))),
                this::postError);
    }

    public void consumeProposal() {
        AiUiState value = current();
        state.setValue(new AiUiState(value.busy(), value.operation(), value.modelReady(), null,
                value.proposalId(), value.openEditorId(), value.instruction(), value.error()));
    }

    public void consumeOpenEditor() {
        AiUiState value = current();
        state.setValue(new AiUiState(value.busy(), value.operation(), value.modelReady(),
                value.proposal(), value.proposalId(), 0, value.instruction(), value.error()));
    }

    public void consumeError() {
        AiUiState value = current();
        state.setValue(new AiUiState(value.busy(), value.operation(), value.modelReady(),
                value.proposal(), value.proposalId(), value.openEditorId(),
                value.instruction(), null));
    }

    public void cancel() {
        if (running != null) running.cancel(true);
        running = null;
        AiUiState value = current();
        state.setValue(new AiUiState(false, AiUiState.Operation.NONE,
                editor.hasModel(), null, value.proposalId(), value.openEditorId(),
                value.instruction(), null));
    }

    private void start(AiUiState.Operation operation) {
        AiUiState value = current();
        state.setValue(new AiUiState(true, operation, value.modelReady(), null,
                value.proposalId(), value.openEditorId(), value.instruction(), null));
    }

    private void postError(Throwable error) {
        String message = friendlyError(error);
        dispatch(() -> state.setValue(new AiUiState(false, AiUiState.Operation.NONE,
                editor.hasModel(), null, current().proposalId(), current().openEditorId(),
                current().instruction(), message)));
    }

    private static String friendlyError(Throwable error) {
        String raw = error.getMessage() == null ? "" : error.getMessage();
        String normalized = (error.getClass().getSimpleName() + " " + raw)
                .toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("memory") || normalized.contains("speicher")
                || error instanceof OutOfMemoryError) {
            return "Beim Rechnen ging der Speicher aus. Bitte die App neu starten und den Wunsch kürzer formulieren.";
        }
        if (normalized.contains("empty") || normalized.contains("leer")) {
            return "Das heruntergeladene Modell konnte nicht gelesen werden. Bitte erneut laden.";
        }
        if (normalized.contains("format") || normalized.contains("parse")) {
            return "Das Modell hat keinen verlässlichen Vorschlag erzeugt. Der Wunsch wurde nicht übernommen; bitte einfacher formulieren und noch einmal versuchen.";
        }
        return raw.isBlank()
                ? "Die lokale Berechnung konnte nicht abgeschlossen werden. Nichts wurde geändert; bitte noch einmal versuchen."
                : raw;
    }

    private void dispatch(Runnable action) {
        uiExecutor.execute(() -> {
            if (!cleared) action.run();
        });
    }

    private AiUiState current() {
        AiUiState value = state.getValue();
        return value == null ? AiUiState.initial(editor.hasModel()) : value;
    }

    @Override
    protected void onCleared() {
        cleared = true;
        if (running != null) running.cancel(true);
    }
}
