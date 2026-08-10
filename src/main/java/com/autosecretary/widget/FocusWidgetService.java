package com.autosecretary.widget;

import android.content.Intent;
import android.widget.RemoteViewsService;

import com.autosecretary.app.AutoSecretaryApplication;

public final class FocusWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new FocusWidgetFactory(
                getApplicationContext(),
                AutoSecretaryApplication.from(this).repository());
    }
}
