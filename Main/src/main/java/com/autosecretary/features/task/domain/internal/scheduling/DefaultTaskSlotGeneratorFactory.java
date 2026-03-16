package com.autosecretary.features.task.domain.internal.scheduling;

import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.scheduling.CalendarBlockedIntervalProvider;
import com.autosecretary.features.task.domain.scheduling.SchedulingWindowProvider;
import com.autosecretary.features.task.domain.scheduling.TaskBudgetEligibilityService;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerator;
import com.autosecretary.features.task.domain.scheduling.TaskTransitionStatLoader;

import java.util.function.Consumer;

/**
 * Internal construction bridge so external callers can obtain a generator without
 * depending directly on the implementation type.
 */
public final class DefaultTaskSlotGeneratorFactory {
    private DefaultTaskSlotGeneratorFactory() {
    }

    public static TaskSlotGenerator create(TaskLifecycleManager lifecycleManager,
                                           Consumer<String> logger,
                                           SchedulingWindowProvider schedulingWindowProvider,
                                           CalendarBlockedIntervalProvider calendarBlockedIntervalProvider,
                                           TaskTransitionStatLoader transitionStatLoader,
                                           TaskBudgetEligibilityService taskBudgetEligibilityService) {
        return new DefaultTaskSlotGenerator(
                lifecycleManager,
                logger,
                schedulingWindowProvider,
                calendarBlockedIntervalProvider,
                transitionStatLoader,
                taskBudgetEligibilityService
        );
    }
}
