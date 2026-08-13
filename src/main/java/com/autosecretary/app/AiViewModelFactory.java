package com.autosecretary.app;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.ui.ai.AiViewModel;

public final class AiViewModelFactory implements ViewModelProvider.Factory {
    private final AppGraph graph;

    public AiViewModelFactory(AppGraph graph) {
        this.graph = graph;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!modelClass.isAssignableFrom(AiViewModel.class)) {
            throw new IllegalArgumentException("Unbekanntes ViewModel: " + modelClass);
        }
        return (T) new AiViewModel(
                graph.bulkEditor(), graph.aiConsent(), graph.executors().main());
    }
}
