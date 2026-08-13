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
        installBundledModel(true);
    }

    public void installBundledModel(boolean openEditorAfter) {
        if (current().busy() || current().modelReady()) return;
        start(AiUiState.Operation.INSTALL);
        running = editor.installBundledModel(
                () -> dispatch(() -> state.setValue(new AiUiState(false,
                        AiUiState.Operation.NONE, true, null, current().proposalId(),
                        current().openEditorId() + (openEditorAfter ? 1 : 0), null))),
                this::postError);
    }

    public void importModel(AiProposalGateway.ModelSource source) {
        if (current().busy()) return;
        start(AiUiState.Operation.IMPORT);
        running = editor.importModel(source,
                () -> dispatch(() -> state.setValue(new AiUiState(false,
                        AiUiState.Operation.NONE, true, null, current().proposalId(),
                        current().openEditorId(), null))),
                this::postError);
    }

    public void propose(String instruction, List<WorkItem> currentItems) {
        if (current().busy() || !current().modelReady()) return;
        start(AiUiState.Operation.INFERENCE);
        running = editor.propose(instruction, currentItems,
                proposal -> dispatch(() -> state.setValue(new AiUiState(false,
                        AiUiState.Operation.NONE, true, proposal, current().proposalId() + 1,
                        current().openEditorId(), null))),
                this::postError);
    }

    public void consumeProposal() {
        AiUiState value = current();
        state.setValue(new AiUiState(value.busy(), value.operation(), value.modelReady(), null,
                value.proposalId(), value.openEditorId(), value.error()));
    }

    public void consumeOpenEditor() {
        AiUiState value = current();
        state.setValue(new AiUiState(value.busy(), value.operation(), value.modelReady(),
                value.proposal(), value.proposalId(), 0, value.error()));
    }

    public void consumeError() {
        AiUiState value = current();
        state.setValue(new AiUiState(value.busy(), value.operation(), value.modelReady(),
                value.proposal(), value.proposalId(), value.openEditorId(), null));
    }

    private void start(AiUiState.Operation operation) {
        AiUiState value = current();
        state.setValue(new AiUiState(true, operation, value.modelReady(), null,
                value.proposalId(), value.openEditorId(), null));
    }

    private void postError(Throwable error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName()
                : error.getMessage();
        dispatch(() -> state.setValue(new AiUiState(false, AiUiState.Operation.NONE,
                editor.hasModel(), null, current().proposalId(), current().openEditorId(), message)));
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
