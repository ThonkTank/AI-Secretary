package com.autosecretary.data;

import android.content.Context;

import com.autosecretary.application.LegacyImportPort;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public final class LegacyArchiveImportGateway implements LegacyImportPort {
    private final Context context;
    private final Consumer<Future<?>> databaseGate;

    public LegacyArchiveImportGateway(Context context, Consumer<Future<?>> databaseGate) {
        this.context = context.getApplicationContext();
        this.databaseGate = databaseGate;
    }

    @Override
    public boolean requiresUserDecision() {
        return LegacyArchiveImporter.requiresUserDecision(context);
    }

    @Override
    public void importArchive(ArchiveSource source) {
        CompletableFuture<Void> preparation = new CompletableFuture<>();
        databaseGate.accept(preparation);
        try (InputStream input = source.open()) {
            if (input == null) throw new IllegalStateException("Exportarchiv konnte nicht geöffnet werden");
            LegacyArchiveImporter.stage(context, input);
            FocusDatabaseFactory.prepare(context);
            preparation.complete(null);
        } catch (Exception error) {
            preparation.completeExceptionally(error);
            throw new IllegalStateException("Build-4-Archiv konnte nicht übernommen werden", error);
        }
    }

    @Override
    public void chooseEmptyDatabase() {
        LegacyArchiveImporter.chooseEmptyDatabase(context);
        databaseGate.accept(CompletableFuture.completedFuture(null));
    }
}
