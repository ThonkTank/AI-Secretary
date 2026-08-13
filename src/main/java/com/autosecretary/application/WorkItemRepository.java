package com.autosecretary.application;

import com.autosecretary.domain.WorkItem;
import com.autosecretary.application.ai.BulkChange;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Synchronous application port. Callers choose the named executor. */
public interface WorkItemRepository {
    FocusSnapshot loadSnapshot();
    WorkItem find(String id);
    void save(WorkItem item);
    void delete(String id);
    void deleteAll(List<String> ids);
    WorkItem complete(String id, LocalDateTime at);
    WorkItem setStepCompleted(String workItemId, String stepId, boolean completed, LocalDateTime at);
    List<DayPlanDirective> directives(LocalDate day);
    void saveDirective(DayPlanDirective directive, String undoLabel);
    void replacePlan(List<StoredPlanSlot> slots, List<StoredPlanningConflict> conflicts);
    List<StoredPlanSlot> storedPlan(LocalDate day);
    String latestUndoLabel();
    boolean undoLatest(LocalDateTime at);

    MigrationReview migrationReview();

    void resolveMigrationCandidates(
            List<MigrationCandidateResolution> resolutions,
            long reportId,
            LocalDateTime at);
    void applyChangeSet(List<BulkChange> changes, String label, LocalDateTime at);
}
