package de.thonktank.autosecretary;

import android.app.Application;
import android.content.Context;

import de.thonktank.autosecretary.infrastructure.AndroidAppLogger;
import de.thonktank.autosecretary.infrastructure.AppLogger;

public final class AutoSecretaryApplication extends Application {
    private AppContainer container;

    @Override public void onCreate() {
        super.onCreate();
        AppLogger logger = new AndroidAppLogger();
        container = AppContainer.create(this, logger);
    }

    public AppContainer container() {
        return container;
    }

    public static AutoSecretaryApplication from(Context context) {
        return (AutoSecretaryApplication) context.getApplicationContext();
    }
}
