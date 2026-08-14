package com.autosecretary.ui.update;

import android.view.View;

import androidx.lifecycle.LifecycleOwner;

import com.autosecretary.presentation.databinding.ActivityMainBinding;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Binds the update state machine to its XML panel without owning installer side effects. */
public final class UpdatePanelController {
    private final ActivityMainBinding binding;
    private final UpdateViewModel viewModel;
    private final boolean enabled;
    private final BooleanSupplier canInstallPackages;
    private final Consumer<UpdateUiEffect> effectHandler;

    public UpdatePanelController(
            ActivityMainBinding binding,
            UpdateViewModel viewModel,
            boolean enabled,
            BooleanSupplier canInstallPackages,
            Consumer<UpdateUiEffect> effectHandler) {
        this.binding = binding;
        this.viewModel = viewModel;
        this.enabled = enabled;
        this.canInstallPackages = canInstallPackages;
        this.effectHandler = effectHandler;
        binding.UpdateAction.setOnClickListener(view -> handleAction());
    }

    public void bind(LifecycleOwner owner) {
        viewModel.state().observe(owner, this::render);
        viewModel.effects().observe(owner, this::handleEffect);
        if (!enabled) {
            binding.UpdateStatus.setText("Lokaler Debug-Build · Updates sind deaktiviert");
            binding.UpdateAction.setVisibility(View.GONE);
            binding.UpdateProgress.setVisibility(View.GONE);
        } else if (viewModel.state().getValue() == null
                || !viewModel.state().getValue().checked()) {
            viewModel.check();
        }
    }

    public void onResume() {
        if (enabled) viewModel.continueInstall(canInstallPackages.getAsBoolean());
    }

    private void handleAction() {
        UpdateUiState state = viewModel.state().getValue();
        if (!enabled || state == null || state.busy()) return;
        if (!state.retryable()) {
            viewModel.check();
        } else if (state.verified() != null) {
            viewModel.requestInstall(canInstallPackages.getAsBoolean());
        } else if (state.available() != null) {
            viewModel.download();
        } else {
            viewModel.check();
        }
    }

    private void render(UpdateUiState state) {
        if (!enabled || state == null) return;
        binding.UpdateAction.setEnabled(!state.busy());
        binding.UpdateProgress.setVisibility(state.busy() ? View.VISIBLE : View.GONE);
        if (state.busy()) {
            binding.UpdateStatus.setText(state.available() == null
                    ? "Suche nach veröffentlichtem Update …"
                    : "Update wird geladen und geprüft …");
        } else if (state.error() != null) {
            binding.UpdateStatus.setText(state.error());
            binding.UpdateAction.setText(state.retryable()
                    ? "Erneut versuchen" : "Neue Freigabe prüfen");
        } else if (state.verified() != null) {
            binding.UpdateStatus.setText("Version " + state.verified().info().versionName()
                    + " ist signiert und bereit");
            binding.UpdateAction.setText("Installer erneut öffnen");
        } else if (state.available() != null) {
            binding.UpdateStatus.setText(
                    "Version " + state.available().versionName() + " ist verfügbar");
            binding.UpdateAction.setText("Update installieren");
        } else if (state.checked()) {
            binding.UpdateStatus.setText("Diese Version ist aktuell");
            binding.UpdateAction.setText("Erneut prüfen");
        } else {
            binding.UpdateStatus.setText("Signierte Updates aus diesem Repository");
            binding.UpdateAction.setText("Nach Updates suchen");
        }
    }

    private void handleEffect(UpdateUiEffect effect) {
        if (effect == null) return;
        viewModel.consumeEffect(effect.id());
        effectHandler.accept(effect);
    }
}
