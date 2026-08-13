package com.autosecretary.app;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.work.Configuration;
import androidx.lifecycle.ViewModelProvider;
import androidx.savedstate.SavedStateRegistryOwner;

import com.autosecretary.application.ai.AiProposalGateway;
import com.autosecretary.application.LegacyImportPort;
import com.autosecretary.application.LocationPort;
import com.autosecretary.application.update.VerifiedUpdate;
import com.autosecretary.background.BackgroundScheduler;
import com.autosecretary.data.FocusDatabaseFactory;
import com.autosecretary.data.LegacyArchiveImportGateway;
import com.autosecretary.platform.CalendarChangeObserver;
import com.autosecretary.widget.FocusWidgetProvider;
import com.autosecretary.domain.PlanningSettings;

public final class AutoSecretaryApplication extends Application implements Configuration.Provider {
    private AppGraph graph;
    private AppExecutors executors;
    private LegacyImportPort legacyImports;
    private CalendarChangeObserver calendarObserver;

    @Override
    public void onCreate() {
        super.onCreate();
        executors = new AppExecutors(
                androidx.core.content.ContextCompat.getMainExecutor(this));
        java.util.concurrent.Future<?> storagePreparation = executors.io().submit(
                () -> FocusDatabaseFactory.prepare(this));
        executors.setDatabaseGate(storagePreparation);
        legacyImports = new LegacyArchiveImportGateway(this, executors::setDatabaseGate);
        calendarObserver = new CalendarChangeObserver(this, executors.database(), () -> {
            try {
                graph().planFocus().execute(Integer.MAX_VALUE, true);
                FocusWidgetProvider.refreshAll(this);
            } catch (RuntimeException ignored) {
                // Import decision or transient calendar/database state; periodic work retries later.
            }
        });
        calendarObserver.refreshRegistration();
        scheduleBackground();
    }

    public synchronized AppGraph graph() {
        if (legacyImports.requiresUserDecision()) {
            throw new IllegalStateException("Build-4-Importentscheidung steht noch aus");
        }
        if (graph == null) graph = new AppGraph(this, executors);
        return graph;
    }
    public AppExecutors executors() { return executors; }
    public LegacyImportPort legacyImports() { return legacyImports; }
    public ViewModelProvider.Factory legacyImportViewModelFactory() {
        return new LegacyImportViewModelFactory(this);
    }
    public ViewModelProvider.Factory mainViewModelFactory(
            SavedStateRegistryOwner owner, Bundle defaults) {
        return new MainViewModelFactory(owner, defaults, graph());
    }
    public ViewModelProvider.Factory updateViewModelFactory() {
        return new UpdateViewModelFactory(graph());
    }
    public ViewModelProvider.Factory aiViewModelFactory() {
        return new AiViewModelFactory(graph());
    }
    public LocationPort location() { return graph().location(); }
    public java.io.File migrationBackupArchive() { return graph().migrationBackupArchive(); }
    public int databaseVersion() { return FocusDatabaseFactory.version(); }
    public Intent updateIntent(Context host, VerifiedUpdate update) {
        return graph().updateInstaller().intent(host, update);
    }
    public void refreshCalendarObservation() { calendarObserver.refreshRegistration(); }
    public AiProposalGateway bulkEditor() { return graph().bulkEditor(); }
    public void scheduleBackground() {
        java.time.LocalTime dayStart;
        try { dayStart = graph().planningSettingsUseCase().load().day().start(); }
        catch (RuntimeException pendingImport) { dayStart = PlanningSettings.defaults().day().start(); }
        BackgroundScheduler.install(this, dayStart);
    }

    @Override
    public void onTerminate() {
        if (calendarObserver != null) calendarObserver.close();
        if (graph != null) graph.close();
        if (executors != null) executors.close();
        super.onTerminate();
    }

    public static AutoSecretaryApplication from(Context context) {
        return (AutoSecretaryApplication) context.getApplicationContext();
    }

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder().build();
    }
}
