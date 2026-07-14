package com.autosecretary.features.task.domain.scheduling;

import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.internal.scheduling.DefaultTaskSlotGeneratorFactory;

import java.util.function.Consumer;

/**
 * Public factory for creating task slot generators without exposing internal implementations.
 */
public final class TaskSlotGenerators {
    private TaskSlotGenerators() {
    }

    public static Builder builder(TaskLifecycleManager lifecycleManager) {
        return new Builder(lifecycleManager);
    }

    public static final class Builder {
        private final TaskLifecycleManager lifecycleManager;
        private Consumer<String> logger;
        private SchedulingWindowProvider schedulingWindowProvider = SchedulingWindowProvider.DEFAULT;
        private CalendarBlockedIntervalProvider calendarBlockedIntervalProvider = CalendarBlockedIntervalProvider.NONE;
        private CategoryWindowProvider categoryWindowProvider = CategoryWindowProvider.NONE;
        private TaskTransitionStatLoader transitionStatLoader;
        private TaskBudgetEligibilityService taskBudgetEligibilityService;

        private Builder(TaskLifecycleManager lifecycleManager) {
            this.lifecycleManager = lifecycleManager;
        }

        public Builder logger(Consumer<String> logger) {
            this.logger = logger;
            return this;
        }

        public Builder schedulingWindowProvider(SchedulingWindowProvider schedulingWindowProvider) {
            this.schedulingWindowProvider = schedulingWindowProvider;
            return this;
        }

        public Builder calendarBlockedIntervalProvider(CalendarBlockedIntervalProvider calendarBlockedIntervalProvider) {
            this.calendarBlockedIntervalProvider = calendarBlockedIntervalProvider;
            return this;
        }

        public Builder categoryWindowProvider(CategoryWindowProvider categoryWindowProvider) {
            this.categoryWindowProvider = categoryWindowProvider;
            return this;
        }

        public Builder transitionStatLoader(TaskTransitionStatLoader transitionStatLoader) {
            this.transitionStatLoader = transitionStatLoader;
            return this;
        }

        public Builder taskBudgetEligibilityService(TaskBudgetEligibilityService taskBudgetEligibilityService) {
            this.taskBudgetEligibilityService = taskBudgetEligibilityService;
            return this;
        }

        public TaskSlotGenerator build() {
            return DefaultTaskSlotGeneratorFactory.create(
                    lifecycleManager,
                    logger,
                    schedulingWindowProvider,
                    calendarBlockedIntervalProvider,
                    categoryWindowProvider,
                    transitionStatLoader,
                    taskBudgetEligibilityService
            );
        }
    }
}
