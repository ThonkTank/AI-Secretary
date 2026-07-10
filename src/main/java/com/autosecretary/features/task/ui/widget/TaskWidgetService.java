package com.autosecretary.features.task.ui.widget;

import android.content.Intent;
import android.widget.RemoteViewsService;

import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.task.application.LoadTaskWidgetItemsUseCase;

/**
 * Implements Android's {@link RemoteViewsService} contract for the task widget.
 * Called by the widget framework to instantiate the list adapter factory on-demand.
 * Obtains a widget read-model loader from {@link AutoSecretaryApplication}
 * so widget data stays behind the application boundary without importing the DI root.
 *
 * See {@link README.md} for widget architecture overview.
 */
public class TaskWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        LoadTaskWidgetItemsUseCase loadTaskWidgetItemsUseCase =
                AutoSecretaryApplication.from(this)
                .createLoadTaskWidgetItemsUseCase();
        return new TaskWidgetFactory(getApplicationContext(), loadTaskWidgetItemsUseCase);
    }
}
