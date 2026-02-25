package com.autosecretary.app;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.autosecretary.features.task.ui.widget.TaskWidgetProvider;

public class AutoSecretaryApplication extends Application {
    private AppCompositionRoot appCompositionRoot;

    @Override
    public void onCreate() {
        super.onCreate();
        appCompositionRoot = new AppCompositionRoot(this);
        registerWidgetRefreshOnUnlock();
    }

    private void registerWidgetRefreshOnUnlock() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                TaskWidgetProvider.notifyWidgetUpdate(context);
            }
        }, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    public AppCompositionRoot getAppCompositionRoot() {
        return appCompositionRoot;
    }

    public static AutoSecretaryApplication from(Context context) {
        return (AutoSecretaryApplication) context.getApplicationContext();
    }
}
