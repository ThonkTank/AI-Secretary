package com.autosecretary.application;

import com.autosecretary.domain.WorkItem;

import java.util.List;

public record FocusSnapshot(
        List<WorkItem> workItems,
        List<CompletionRecord> completions,
        List<StepCompletion> stepCompletions) {
    public FocusSnapshot {
        workItems = List.copyOf(workItems);
        completions = List.copyOf(completions);
        stepCompletions = List.copyOf(stepCompletions);
    }
}
