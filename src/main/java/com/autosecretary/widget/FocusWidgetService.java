package com.autosecretary.widget;

import android.content.Intent;
import android.widget.RemoteViewsService;

import com.autosecretary.app.AutoSecretaryApplication;

public final class FocusWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        com.autosecretary.app.AppGraph graph = null;
        try { graph = AutoSecretaryApplication.from(this).graph(); }
        catch (IllegalStateException ignored) { }
        return new FocusWidgetFactory(
                getApplicationContext(),
                graph);
    }
}
