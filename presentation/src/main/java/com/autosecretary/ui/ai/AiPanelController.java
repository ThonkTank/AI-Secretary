package com.autosecretary.ui.ai;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LifecycleOwner;

import com.autosecretary.presentation.R;
import com.autosecretary.presentation.databinding.ActivityMainBinding;

import java.util.function.Consumer;

/** Owns local-AI panel rendering and dialog transitions. */
public final class AiPanelController {
    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private final AiViewModel viewModel;
    private final Consumer<String> errorHandler;

    public AiPanelController(
            AppCompatActivity activity,
            ActivityMainBinding binding,
            AiViewModel viewModel,
            Consumer<String> errorHandler) {
        this.activity = activity;
        this.binding = binding;
        this.viewModel = viewModel;
        this.errorHandler = errorHandler;
        binding.AiBulkEdit.setOnClickListener(view -> ensureReady(this::showInstruction));
        binding.AiProgressCancel.setOnClickListener(view -> viewModel.cancel());
    }

    public void bind(LifecycleOwner owner) {
        viewModel.state().observe(owner, this::render);
    }

    private void render(AiUiState state) {
        if (state == null) return;
        binding.AiProgress.setVisibility(state.busy() ? View.VISIBLE : View.GONE);
        binding.AiAnnualRings.setRunning(state.busy());
        if (state.busy()) {
            binding.AiProgressText.setText(switch (state.operation()) {
                case INSTALL -> "Das Modell wird heruntergeladen und lokal geprüft.";
                case INFERENCE -> "liest die Einträge und den Kalender …";
                case NONE -> "bereitet die lokale KI vor …";
            });
        }
        binding.AiProgressCancel.setVisibility(state.busy()
                && state.operation() == AiUiState.Operation.INSTALL ? View.VISIBLE : View.GONE);
        binding.ModelStatus.setText(state.busy()
                ? state.operation() == AiUiState.Operation.INFERENCE
                        ? R.string.ai_working : R.string.model_importing
                : state.modelReady() ? R.string.model_ready : R.string.model_download);
        if (state.error() != null) {
            errorHandler.accept(state.error());
            viewModel.consumeError();
            return;
        }
        if (state.openEditorId() > 0) {
            viewModel.consumeOpenEditor();
            showInstruction();
        }
        if (state.proposal() != null
                && activity.getSupportFragmentManager().findFragmentByTag(
                        AiProposalDialogFragment.TAG) == null
                && !activity.getSupportFragmentManager().isStateSaved()) {
            new AiProposalDialogFragment().show(
                    activity.getSupportFragmentManager(), AiProposalDialogFragment.TAG);
        }
    }

    private void showInstruction() {
        if (activity.getSupportFragmentManager().findFragmentByTag(
                AiInstructionDialogFragment.TAG) == null
                && !activity.getSupportFragmentManager().isStateSaved()) {
            new AiInstructionDialogFragment().show(
                    activity.getSupportFragmentManager(), AiInstructionDialogFragment.TAG);
        }
    }

    private void ensureReady(Runnable continuation) {
        AiUiState state = viewModel.state().getValue();
        if (state == null || state.busy()) return;
        if (!viewModel.termsAccepted()) {
            if (activity.getSupportFragmentManager().findFragmentByTag(
                    AiTermsDialogFragment.TAG) == null) {
                new AiTermsDialogFragment().show(
                        activity.getSupportFragmentManager(), AiTermsDialogFragment.TAG);
            }
        } else if (!state.modelReady()) {
            viewModel.installModel(true);
        } else {
            continuation.run();
        }
    }
}
