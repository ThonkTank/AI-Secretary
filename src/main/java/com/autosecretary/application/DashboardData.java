package com.autosecretary.application;

import com.autosecretary.domain.BusyInterval;
import com.autosecretary.domain.PlanAssignment;
import com.autosecretary.domain.PlanConflict;
import com.autosecretary.domain.WorkItem;

import java.util.List;

public record DashboardData(
        List<PlanAssignment> focus,
        List<WorkItem> workItems,
        List<BusyInterval> calendar,
        List<PlanConflict> conflicts,
        List<CompletionRecord> completions,
        List<StepCompletion> stepCompletions,
        String undoLabel) {
    public DashboardData {
        focus = List.copyOf(focus);
        workItems = List.copyOf(workItems);
        calendar = List.copyOf(calendar);
        conflicts = List.copyOf(conflicts);
        completions = List.copyOf(completions);
        stepCompletions = List.copyOf(stepCompletions);
    }
}
