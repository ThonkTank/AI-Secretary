package com.autosecretary.features.task.ui.widget;

import android.content.Intent;
import android.widget.RemoteViewsService;

import com.autosecretary.features.task.application.LoadTaskWidgetItemsUseCase;

/**
 * Implements Android's {@link RemoteViewsService} contract for the task widget.
 * Called by the widget framework to instantiate the list adapter factory on-demand.
 * Obtains a widget read-model loader from {@link TaskWidgetDependencies}
 * so widget data stays behind the application boundary without importing app wiring.
 *
 * See {@link README.md} for widget architecture overview.
 */
public class TaskWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        LoadTaskWidgetItemsUseCase loadTaskWidgetItemsUseCase =
                ((TaskWidgetDependencies) getApplicationContext())
                .createLoadTaskWidgetItemsUseCase();
        return new TaskWidgetFactory(getApplicationContext(), loadTaskWidgetItemsUseCase);
    }
}
