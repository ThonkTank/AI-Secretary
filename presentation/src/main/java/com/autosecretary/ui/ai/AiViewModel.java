package com.autosecretary.ui.ai;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.autosecretary.application.ai.AiConsentPort;
import com.autosecretary.application.ai.AiProposalGateway;
import com.autosecretary.application.model.ModelDownloadProgress;
import com.autosecretary.application.model.ModelDownloadTicket;
import com.autosecretary.application.model.ModelRepository;
import com.autosecretary.application.model.ModelStatus;
import com.autosecretary.domain.WorkItem;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** Coordinates explicit model transfer and inference without retaining an Activity. */
public final class AiViewModel extends ViewModel {
    private final AiProposalGateway editor;
    private final AiConsentPort consent;
    private final ModelRepository models;
    private final ExecutorService io;
    private final Executor uiExecutor;
    private final MutableLiveData<AiUiState> state;
    private final MutableLiveData<AiUiEffect> effects = new MutableLiveData<>();
    private Future<?> running;
    private volatile boolean cleared;
    private long effectSequence;

    public AiViewModel(
            AiProposalGateway editor,
            AiConsentPort consent,
            ModelRepository models,
            ExecutorService io,
            Executor uiExecutor) {
        this.editor = editor;
        this.consent = consent;
        this.models = models;
        this.io = io;
        this.uiExecutor = uiExecutor;
        ModelStatus model = models.status();
        state = new MutableLiveData<>(from(model));
        if (model instanceof ModelStatus.Downloading downloading) {
            observe(downloadloadingTicket(downloading), false);
        }
    }

    public LiveData<AiUiState> state() { return state; }
    public LiveData<AiUiEffect> effects() { return effects; }
    public boolean termsAccepted() { return consent.accepted(); }

    public void acceptTermsAndInstall() {
        consent.accept();
        installModel(true);
    }

    public void installModel(boolean openEditorAfter) {
        if (current().busy() || current().modelReady()) return;
        if (!consent.accepted()) {
            String message = "Vor dem Download müssen die Gemma-Bedingungen akzeptiert werden.";
            state.setValue(new AiUiState.Error(message, false, modelSize(), ""));
            emit(new AiUiEffect.ShowError(nextEffectId(), message));
            return;
        }
        running = io.submit(() -> {
            try {
                ModelDownloadTicket ticket = models.enqueue();
                dispatch(() -> state.setValue(new AiUiState.Downloading(ticket,
                        new ModelDownloadProgress.Pending(), modelSize(), openEditorAfter)));
                observeLoop(ticket, openEditorAfter);
            } catch (Throwable error) {
                postError(error, false, "");
            }
        });
    }

    public void propose(String instruction, List<WorkItem> currentItems) {
        if (current().busy() || !current().modelReady()) return;
        state.setValue(new AiUiState.Inferring(instruction));
        running = editor.propose(instruction, currentItems,
                proposal -> dispatch(() -> {
                    state.setValue(new AiUiState.ProposalReady(proposal, instruction));
                    emit(new AiUiEffect.OpenProposal(nextEffectId()));
                }),
                error -> postError(error, true, instruction));
    }

    public void consumeProposal() { state.setValue(new AiUiState.Ready()); }

    public void consumeEffect(long id) {
        AiUiEffect effect = effects.getValue();
        if (effect != null && effect.id() == id) effects.setValue(null);
    }

    public void consumeError() {
        AiUiState value = current();
        state.setValue(value.modelReady() ? new AiUiState.Ready()
                : new AiUiState.Missing(modelSize()));
    }

    public void cancel() {
        AiUiState value = current();
        if (value instanceof AiUiState.Downloading downloading) {
            models.cancel(downloading.ticket());
        }
        if (running != null) running.cancel(true);
        running = null;
        state.setValue(value.modelReady() ? new AiUiState.Ready()
                : new AiUiState.Missing(modelSize()));
    }

    private void observe(ModelDownloadTicket ticket, boolean openEditorAfter) {
        running = io.submit(() -> observeLoop(ticket, openEditorAfter));
    }

    private void observeLoop(ModelDownloadTicket ticket, boolean openEditorAfter) {
        while (!cleared && !Thread.currentThread().isInterrupted()) {
            ModelDownloadProgress progress = models.query(ticket);
            dispatch(() -> state.setValue(new AiUiState.Downloading(
                    ticket, progress, modelSize(), openEditorAfter)));
            if (progress instanceof ModelDownloadProgress.Complete) {
                try {
                    models.verifyAndActivate(ticket);
                    dispatch(() -> {
                        state.setValue(new AiUiState.Ready());
                        if (openEditorAfter) {
                            emit(new AiUiEffect.OpenInstruction(nextEffectId()));
                        }
                    });
                } catch (Throwable error) {
                    postError(error, false, "");
                }
                return;
            }
            if (progress instanceof ModelDownloadProgress.Failed failed) {
                dispatch(() -> {
                    state.setValue(new AiUiState.Error(
                            failed.detail(), false, modelSize(), ""));
                    emit(new AiUiEffect.ShowError(nextEffectId(), failed.detail()));
                });
                return;
            }
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void postError(Throwable error, boolean modelReady, String instruction) {
        String message = friendlyError(error);
        dispatch(() -> {
            state.setValue(new AiUiState.Error(message, modelReady, modelSize(), instruction));
            emit(new AiUiEffect.ShowError(nextEffectId(), message));
        });
    }

    private long modelSize() { return models.status().sizeBytes(); }

    private static ModelDownloadTicket downloadloadingTicket(ModelStatus.Downloading status) {
        return status.ticket();
    }

    private static AiUiState from(ModelStatus status) {
        if (status instanceof ModelStatus.Ready) return new AiUiState.Ready();
        if (status instanceof ModelStatus.Downloading value) {
            return new AiUiState.Downloading(value.ticket(), value.progress(),
                    value.sizeBytes(), false);
        }
        if (status instanceof ModelStatus.Failed failed) {
            return new AiUiState.Error(failed.detail(), false, failed.sizeBytes(), "");
        }
        return new AiUiState.Missing(status.sizeBytes());
    }

    private static String friendlyError(Throwable error) {
        String raw = error.getMessage() == null ? "" : error.getMessage();
        String normalized = (error.getClass().getSimpleName() + " " + raw)
                .toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("memory") || normalized.contains("speicher")
                || error instanceof OutOfMemoryError) {
            return "Beim Rechnen ging der Speicher aus. Bitte die App neu starten und den Wunsch kürzer formulieren.";
        }
        if (normalized.contains("format") || normalized.contains("parse")) {
            return "Das Modell hat keinen verlässlichen Vorschlag erzeugt. Bitte einfacher formulieren.";
        }
        return raw.isBlank() ? "Die lokale Berechnung konnte nicht abgeschlossen werden." : raw;
    }

    private void dispatch(Runnable action) {
        uiExecutor.execute(() -> { if (!cleared) action.run(); });
    }

    private void emit(AiUiEffect effect) { effects.setValue(effect); }
    private long nextEffectId() { return ++effectSequence; }

    private AiUiState current() {
        AiUiState value = state.getValue();
        return value == null ? new AiUiState.Missing(modelSize()) : value;
    }

    @Override protected void onCleared() {
        cleared = true;
        if (running != null) running.cancel(true);
    }
}
