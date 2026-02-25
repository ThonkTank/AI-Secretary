package com.autosecretary.app;

import android.app.Application;
import android.content.Context;

public class AutoSecretaryApplication extends Application {
    private AppCompositionRoot appCompositionRoot;

    @Override
    public void onCreate() {
        super.onCreate();
        appCompositionRoot = new AppCompositionRoot(this);
    }

    public AppCompositionRoot getAppCompositionRoot() {
        return appCompositionRoot;
    }

    public static AutoSecretaryApplication from(Context context) {
        return (AutoSecretaryApplication) context.getApplicationContext();
    }
}
