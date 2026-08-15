package de.thonktank.autosecretary;

import android.app.Application;
import android.content.Context;

import de.thonktank.autosecretary.data.legacy.LegacyStateCleaner;
import de.thonktank.autosecretary.infrastructure.AndroidAppLogger;
import de.thonktank.autosecretary.infrastructure.AppLogger;

public final class AutoSecretaryApplication extends Application {
    private AppContainer container;
    private LegacyStateCleaner legacyStateCleaner;

    @Override public void onCreate() {
        super.onCreate();
        AppLogger logger = new AndroidAppLogger();
        legacyStateCleaner = new LegacyStateCleaner(this, logger);
        legacyStateCleaner.cleanOnce();
        container = AppContainer.create(this, logger);
    }

    public AppContainer container() {
        return container;
    }

    public LegacyStateCleaner legacyStateCleaner() {
        return legacyStateCleaner;
    }

    public static AutoSecretaryApplication from(Context context) {
        return (AutoSecretaryApplication) context.getApplicationContext();
    }
}
