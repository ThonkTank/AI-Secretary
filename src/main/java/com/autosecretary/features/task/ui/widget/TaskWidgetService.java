package com.autosecretary.features.task.ui.widget;

import android.content.Intent;
import android.widget.RemoteViewsService;

import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.task.data.TaskDAO;

public class TaskWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        TaskDAO taskDao = AutoSecretaryApplication.from(this).getAppCompositionRoot().getTaskDAO();
        return new TaskWidgetFactory(getApplicationContext(), taskDao);
    }
}
