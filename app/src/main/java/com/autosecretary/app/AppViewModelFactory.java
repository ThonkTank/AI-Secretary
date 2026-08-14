package com.autosecretary.app;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.savedstate.SavedStateRegistryOwner;

import com.autosecretary.ui.MainViewModel;
import com.autosecretary.ui.ai.AiViewModel;
import com.autosecretary.ui.editor.EditorViewModel;
import com.autosecretary.ui.settings.PlanningSettingsViewModel;
import com.autosecretary.ui.update.UpdateViewModel;

/** The one ViewModel construction entry point for the single-activity app. */
public final class AppViewModelFactory extends AbstractSavedStateViewModelFactory {
    private final AppGraph graph;

    public AppViewModelFactory(SavedStateRegistryOwner owner, Bundle defaults, AppGraph graph) {
        super(owner, defaults);
        this.graph = graph;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    protected <T extends ViewModel> T create(
            @NonNull String key,
            @NonNull Class<T> modelClass,
            @NonNull SavedStateHandle handle) {
        if (modelClass == MainViewModel.class) {
            return (T) new MainViewModel(handle, graph.planFocus(), graph.workItems(),
                    graph.moveWorkItem(), graph.clock(),
                    graph.executors().database(), graph.executors().main(),
                    graph::refreshWidgets);
        }
        if (modelClass == EditorViewModel.class) {
            return (T) new EditorViewModel(handle, graph.workItems(), graph.clock(),
                    graph.executors().database(), graph.executors().main());
        }
        if (modelClass == PlanningSettingsViewModel.class) {
            return (T) new PlanningSettingsViewModel(handle, graph.planningSettings(),
                    graph.executors().database(), graph.executors().main());
        }
        if (modelClass == AiViewModel.class) {
            return (T) new AiViewModel(
                    graph.bulkEditor(), graph.aiConsent(), graph.models(),
                    graph.executors().io(), graph.executors().main());
        }
        if (modelClass == UpdateViewModel.class) {
            return (T) new UpdateViewModel(
                    handle, graph.updates(), graph.executors().io(), graph.executors().main());
        }
        throw new IllegalArgumentException("Unbekanntes ViewModel " + modelClass.getName());
    }
}
