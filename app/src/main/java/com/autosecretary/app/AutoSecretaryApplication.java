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
import com.autosecretary.application.LocationPort;
import com.autosecretary.application.update.VerifiedUpdate;
import com.autosecretary.background.BackgroundScheduler;

public final class AutoSecretaryApplication extends Application implements Configuration.Provider {
    private AppGraph graph;
    private AppExecutors executors;

    @Override
    public void onCreate() {
        super.onCreate();
        executors = new AppExecutors(
                androidx.core.content.ContextCompat.getMainExecutor(this));
        BackgroundScheduler.install(this);
    }

    public synchronized AppGraph graph() {
        if (graph == null) graph = new AppGraph(this, executors);
        return graph;
    }
    public AppExecutors executors() { return executors; }
    public ViewModelProvider.Factory viewModelFactory(
            SavedStateRegistryOwner owner, Bundle defaults) {
        return new AppViewModelFactory(owner, defaults, graph());
    }
    public LocationPort location() { return graph().location(); }
    public int databaseVersion() { return com.autosecretary.data.FocusDatabase.VERSION; }
    public Intent updateSettingsIntent(Context host) {
        return graph().updateInstaller().settingsIntent(host);
    }
    public Intent updateInstallerIntent(Context host, VerifiedUpdate update) {
        return graph().updateInstaller().installerIntent(host, update);
    }
    public AiProposalGateway bulkEditor() { return graph().bulkEditor(); }
    @Override
    public void onTerminate() {
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
