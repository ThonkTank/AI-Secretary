package com.autosecretary.features.task.ui.widget;

import android.content.Intent;
import android.widget.RemoteViewsService;

import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.task.data.TaskDao;

/**
 * Implements Android's {@link RemoteViewsService} contract for the task widget.
 * Called by the widget framework to instantiate the list adapter factory on-demand.
 * Obtains {@link TaskDao} from {@link com.autosecretary.app.AppCompositionRoot}
 * to follow the convention that all DI routing flows through the composition root.
 *
 * See {@link README.md} for widget architecture overview.
 */
public class TaskWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        TaskDao taskDao = AutoSecretaryApplication.from(this).getAppCompositionRoot().getTaskDao();
        return new TaskWidgetFactory(getApplicationContext(), taskDao);
    }
}
