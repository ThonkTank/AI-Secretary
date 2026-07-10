package com.autosecretary.features.task.application.internal.alarms;

import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.shared.WidgetRefreshNotifier;

public interface TaskAlarmDependencies {
    RegenerateScheduleUseCase getRegenerateScheduleUseCase();

    WidgetRefreshNotifier getWidgetRefreshNotifier();
}
