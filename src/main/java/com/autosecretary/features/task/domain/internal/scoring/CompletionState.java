package com.autosecretary.features.task.domain.internal.scoring;

import java.time.LocalDate;

public final class CompletionState {
    private final int completions;
    private final LocalDate lastCompletion;
    private final int periodCompletions;
    private final boolean complete;
    private final int scheduledToday;

    public CompletionState(int completions, LocalDate lastCompletion, int periodCompletions, boolean complete, int scheduledToday) {
        this.completions = completions;
        this.lastCompletion = lastCompletion;
        this.periodCompletions = periodCompletions;
        this.complete = complete;
        this.scheduledToday = scheduledToday;
    }

    public int completions() {
        return completions;
    }

    public LocalDate lastCompletion() {
        return lastCompletion;
    }

    public int periodCompletions() {
        return periodCompletions;
    }

    public boolean isComplete() {
        return complete;
    }

    public int scheduledToday() {
        return scheduledToday;
    }

    public CompletionState withIncrementedScheduledToday() {
        return new CompletionState(completions, lastCompletion, periodCompletions, complete, scheduledToday + 1);
    }
}
