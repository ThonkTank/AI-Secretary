package com.autosecretary.app;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.ui.update.UpdateViewModel;

public final class UpdateViewModelFactory implements ViewModelProvider.Factory {
    private final AppGraph graph;

    public UpdateViewModelFactory(AppGraph graph) {
        this.graph = graph;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!modelClass.isAssignableFrom(UpdateViewModel.class)) {
            throw new IllegalArgumentException("Unbekanntes ViewModel: " + modelClass);
        }
        return (T) new UpdateViewModel(
                graph.updateGateway(), graph.executors().io(), graph.executors().main());
    }
}
