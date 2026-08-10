package com.autosecretary.app;

import android.app.Application;
import android.content.Context;

import com.autosecretary.ai.OnDeviceBulkEditor;
import com.autosecretary.data.DeviceCalendarReader;
import com.autosecretary.data.TaskStore;
import com.autosecretary.widget.FocusWidgetProvider;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny composition root for the complete application. */
public final class AutoSecretaryApplication extends Application {
    private ExecutorService executor;
    private TaskStore store;
    private SecretaryRepository repository;
    private OnDeviceBulkEditor bulkEditor;

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        store = new TaskStore(this);
        repository = new SecretaryRepository(
                store,
                new DeviceCalendarReader(this),
                executor,
                () -> FocusWidgetProvider.refreshAll(this));
        bulkEditor = new OnDeviceBulkEditor(this, executor);
    }

    public SecretaryRepository repository() {
        return repository;
    }

    public OnDeviceBulkEditor bulkEditor() {
        return bulkEditor;
    }

    public ExecutorService executor() {
        return executor;
    }

    public static AutoSecretaryApplication from(Context context) {
        return (AutoSecretaryApplication) context.getApplicationContext();
    }
}
