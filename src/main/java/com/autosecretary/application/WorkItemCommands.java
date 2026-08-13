package com.autosecretary.application;

import com.autosecretary.domain.WorkItem;
import com.autosecretary.application.ai.BulkChange;

import java.util.List;

/** Transaction-oriented commands for every mutation of the focus core. */
public final class WorkItemCommands {
    private final WorkItemRepository repository;
    private final AppClock clock;

    public WorkItemCommands(WorkItemRepository repository, AppClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void save(WorkItem item) { repository.save(item); }

    public void delete(String id) { repository.delete(id); }

    public void deleteAll(List<String> ids) { repository.deleteAll(List.copyOf(ids)); }

    public void complete(String id) { repository.complete(id, clock.now()); }

    public void setStepCompleted(String itemId, String stepId, boolean completed) {
        repository.setStepCompleted(itemId, stepId, completed, clock.now());
    }

    public boolean undo() { return repository.undoLatest(clock.now()); }

    public void applyChangeSet(List<BulkChange> changes, String label) {
        repository.applyChangeSet(changes, label, clock.now());
    }
}
