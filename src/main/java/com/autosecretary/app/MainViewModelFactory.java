package com.autosecretary.app;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.savedstate.SavedStateRegistryOwner;

import com.autosecretary.ui.MainViewModel;

public final class MainViewModelFactory extends AbstractSavedStateViewModelFactory {
    private final AppGraph graph;

    public MainViewModelFactory(SavedStateRegistryOwner owner, Bundle defaults, AppGraph graph) {
        super(owner, defaults);
        this.graph = graph;
    }

    @NonNull
    @Override
    protected <T extends ViewModel> T create(
            @NonNull String key,
            @NonNull Class<T> modelClass,
            @NonNull SavedStateHandle handle) {
        if (!modelClass.isAssignableFrom(MainViewModel.class)) {
            throw new IllegalArgumentException("Unbekanntes ViewModel " + modelClass.getName());
        }
        @SuppressWarnings("unchecked") T result = (T) new MainViewModel(handle,
                graph.planFocus(), graph.workItemCommands(), graph.moveWorkItem(),
                graph.resolveMigrationCandidate(), graph.clock(), graph.planningSettingsUseCase(),
                graph.executors().database(), graph.executors().main(),
                graph::refreshWidgets);
        return result;
    }
}
