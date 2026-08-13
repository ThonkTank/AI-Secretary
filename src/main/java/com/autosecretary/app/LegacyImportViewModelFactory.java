package com.autosecretary.app;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.ui.migration.LegacyImportViewModel;

public final class LegacyImportViewModelFactory implements ViewModelProvider.Factory {
    private final AutoSecretaryApplication application;

    public LegacyImportViewModelFactory(AutoSecretaryApplication application) {
        this.application = application;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!modelClass.isAssignableFrom(LegacyImportViewModel.class)) {
            throw new IllegalArgumentException("Unbekanntes ViewModel: " + modelClass.getName());
        }
        return (T) new LegacyImportViewModel(
                application.legacyImports(), application.executors().io(),
                application.executors().main(), () -> {
                    try {
                        application.executors().callDatabase(() -> {
                            application.graph().database().getOpenHelper().getWritableDatabase();
                            return null;
                        });
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Datenbanköffnung wurde unterbrochen", error);
                    } catch (java.util.concurrent.ExecutionException error) {
                        throw new IllegalStateException("Datenbanköffnung ist fehlgeschlagen", error);
                    }
                });
    }
}
