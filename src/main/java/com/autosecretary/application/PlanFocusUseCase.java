package com.autosecretary.application;

import com.autosecretary.domain.BusyInterval;
import com.autosecretary.domain.CompletionEvidence;
import com.autosecretary.domain.FocusPlanner;
import com.autosecretary.domain.PlanAssignment;
import com.autosecretary.domain.PlanConflict;
import com.autosecretary.domain.PlanOrderDirective;
import com.autosecretary.domain.PlanningResult;
import com.autosecretary.domain.PlanningSettings;
import com.autosecretary.domain.WorkItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Builds and atomically stores the multi-day plan, then derives the calm focus surface. */
public final class PlanFocusUseCase {
    private final WorkItemRepository repository;
    private final CalendarPort calendar;
    private final PlanningSettingsRepository settingsRepository;
    private final AppClock clock;
    private final FocusPlanner planner;

    public PlanFocusUseCase(
            WorkItemRepository repository,
            CalendarPort calendar,
            PlanningSettingsRepository settingsRepository,
            AppClock clock,
            FocusPlanner planner) {
        this.repository = repository;
        this.calendar = calendar;
        this.settingsRepository = settingsRepository;
        this.clock = clock;
        this.planner = planner;
    }

    public DashboardData execute(int focusLimit, boolean persistPlan) {
        LocalDateTime now = clock.now();
        PlanningSettings settings = settingsRepository.load();
        FocusSnapshot snapshot = repository.loadSnapshot();
        List<BusyInterval> busy = calendar.read(now.toLocalDate(),
                now.toLocalDate().plusDays(settings.horizonDays()));
        List<CompletionEvidence> evidence = snapshot.completions().stream()
                .map(value -> new CompletionEvidence(value.workItemId(), value.completedAt()))
                .collect(java.util.stream.Collectors.toList());
        List<PlanOrderDirective> directives = repository.directives(now.toLocalDate()).stream()
                .map(value -> new PlanOrderDirective(value.day(), value.workItemId(),
                        PlanOrderDirective.Relation.valueOf(value.relation().name()),
                        value.anchorWorkItemId(), value.updatedAt()))
                .collect(java.util.stream.Collectors.toList());
        PlanningResult result = planner.plan(snapshot.workItems(), evidence, busy,
                directives, settings, now);
        if (persistPlan) persist(result, now);

        List<PlanAssignment> today = result.assignments().stream()
                .filter(value -> value.start().toLocalDate().equals(now.toLocalDate()))
                .filter(value -> !value.end().isBefore(now))
                .limit(Math.max(0, focusLimit))
                .collect(java.util.stream.Collectors.toList());
        List<WorkItem> ordered = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PlanAssignment assignment : result.assignments()) {
            if (seen.add(assignment.workItem().id())) ordered.add(assignment.workItem());
        }
        for (WorkItem item : snapshot.workItems()) if (seen.add(item.id())) ordered.add(item);
        return new DashboardData(today, ordered, busy, result.conflicts(),
                snapshot.stepCompletions(), repository.latestUndoLabel(),
                repository.migrationReview());
    }

    private void persist(PlanningResult result, LocalDateTime computedAt) {
        List<StoredPlanSlot> slots = result.assignments().stream()
                .map(value -> new StoredPlanSlot(UUID.randomUUID().toString(),
                        value.workItem().id(), value.occurrenceKey(), value.start(), value.end(), computedAt))
                .collect(java.util.stream.Collectors.toList());
        List<StoredPlanningConflict> conflicts = result.conflicts().stream()
                .map(value -> new StoredPlanningConflict(UUID.randomUUID().toString(),
                        value.workItem().id(), value.occurrenceKey(), value.reason().name(),
                        value.detail(), computedAt))
                .collect(java.util.stream.Collectors.toList());
        repository.replacePlan(slots, conflicts);
    }
}
