package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Dashboard;
import de.thonktank.autosecretary.domain.model.DashboardTask;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.util.ArrayList;
import java.util.List;

public final class DeferTask {
    private final TaskRepository repository;
    private final LoadDashboard loadDashboard;
    private final TaskOrdering ordering;
    private final Clock clock;

    public DeferTask(TaskRepository repository, LoadDashboard loadDashboard,
                     TaskOrdering ordering, Clock clock) {
        this.repository = repository;
        this.loadDashboard = loadDashboard;
        this.ordering = ordering;
        this.clock = clock;
    }

    public void execute(String occurrenceOrTaskId) {
        Dashboard dashboard = loadDashboard.execute(clock.today());
        List<DashboardTask> open = new ArrayList<>();
        for (DashboardTask item : dashboard.tasks) if (!item.done) open.add(item);
        int index = indexOf(open, occurrenceOrTaskId);
        if (index < 0 || index >= open.size() - 1) return;
        TaskId first = open.get(index).task.id;
        TaskId second = open.get(index + 1).task.id;
        repository.inTransaction(() -> {
            List<Task> reordered = ordering.swap(repository.allTasks(), first, second);
            for (Task task : reordered) repository.updateTask(task);
        });
    }

    private static int indexOf(List<DashboardTask> tasks, String id) {
        for (int i = 0; i < tasks.size(); i++) {
            DashboardTask item = tasks.get(i);
            if (item.task.id.value.equals(id)
                    || item.occurrence != null && item.occurrence.id.equals(id)) return i;
        }
        return -1;
    }
}
