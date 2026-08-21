package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Complete task-definition inventory used by the management screen. */
public final class TaskCatalog {
    public final List<Item> items;

    public TaskCatalog(List<Item> items) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    public static final class Item {
        public final Task task;
        public final List<TaskStepTemplate> steps;
        public final List<TaskScheduleEntry> schedule;

        public Item(Task task, List<TaskStepTemplate> steps,
                    List<TaskScheduleEntry> schedule) {
            this.task = task;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
            this.schedule = Collections.unmodifiableList(new ArrayList<>(schedule));
        }
    }
}
