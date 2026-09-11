package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** First-class Today projection; internal execution occurrences never become cards. */
public final class FlowTaskSheet {
    public static final class Entry {
        public enum Kind { RUN_STEP, CANDIDATE, COLLECTION }

        public final Kind kind;
        public final String targetId;
        public final String title;
        public final long queueOrder;
        public final TaskStepTemplate candidateTemplate;
        public final OccurrenceStep runStep;
        public final FlowRunSummary run;
        public final FlowDelayPolicy waitAfter;
        public final Long finalTau;

        private Entry(Kind kind, String targetId, String title, long queueOrder,
                      TaskStepTemplate candidateTemplate, OccurrenceStep runStep,
                      FlowRunSummary run) {
            this(kind, targetId, title, queueOrder, candidateTemplate, runStep, run,
                    run == null ? null : run.delayAfter, null);
        }

        private Entry(Kind kind, String targetId, String title, long queueOrder,
                      TaskStepTemplate candidateTemplate, OccurrenceStep runStep,
                      FlowRunSummary run, FlowDelayPolicy waitAfter, Long finalTau) {
            this.kind = kind;
            this.targetId = targetId;
            this.title = title;
            this.queueOrder = queueOrder;
            this.candidateTemplate = candidateTemplate;
            this.runStep = runStep;
            this.run = run;
            this.waitAfter = waitAfter;
            this.finalTau = finalTau;
        }

        public static Entry candidate(FlowCandidate candidate, TaskStepTemplate template) {
            return new Entry(Kind.CANDIDATE, candidate.id, template.text, candidate.queueOrder,
                    template, null, null);
        }

        public static Entry runStep(FlowRunSummary run, OccurrenceStep step) {
            return new Entry(Kind.RUN_STEP, run.id, run.seedTitle + ": " + step.text,
                    run.queueOrder, null, step, run);
        }

        public static Entry candidate(FlowCandidate candidate, TaskStepTemplate template, FlowDelayPolicy wait) {
            return new Entry(Kind.CANDIDATE, candidate.id, template.text, candidate.queueOrder,
                    template, null, null, wait, null);
        }

        public static Entry action(FlowRunSummary summary, FlowGraphRun.Step step,
                                   OccurrenceStep occurrence, Long finalTau) {
            return new Entry(Kind.RUN_STEP, step.id, summary.seedTitle + ": " + step.source.title,
                    summary.queueOrder, null, occurrence, summary, step.source.waitAfter, finalTau);
        }

        public static Entry collection(FlowRunSummary summary) {
            return new Entry(Kind.COLLECTION, summary.id, summary.seedTitle + ": Tau einsammeln",
                    summary.queueOrder, null, null, summary, null, summary.uncollectedTau);
        }
    }

    public final FlowTaskSheetPlacement placement;
    public final Task task;
    public final List<Entry> entries;
    public final List<FlowRunSummary> running;

    public FlowTaskSheet(FlowTaskSheetPlacement placement, Task task, List<Entry> entries) {
        this(placement, task, entries, Collections.emptyList());
    }

    public FlowTaskSheet(FlowTaskSheetPlacement placement, Task task, List<Entry> entries,
                         List<FlowRunSummary> running) {
        if (placement == null || task == null || entries == null || entries.isEmpty())
            throw new IllegalArgumentException("A flow task sheet must be non-empty");
        this.placement = placement;
        this.task = task;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.running = Collections.unmodifiableList(new ArrayList<>(running));
    }
}
