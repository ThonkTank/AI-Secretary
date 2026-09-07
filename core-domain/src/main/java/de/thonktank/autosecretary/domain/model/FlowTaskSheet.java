package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** First-class Today projection; internal execution occurrences never become cards. */
public final class FlowTaskSheet {
    public static final class Entry {
        public enum Kind { RUN_STEP, CANDIDATE }

        public final Kind kind;
        public final String targetId;
        public final String title;
        public final long queueOrder;
        public final TaskStepTemplate candidateTemplate;
        public final OccurrenceStep runStep;
        public final FlowRunSummary run;

        private Entry(Kind kind, String targetId, String title, long queueOrder,
                      TaskStepTemplate candidateTemplate, OccurrenceStep runStep,
                      FlowRunSummary run) {
            this.kind = kind;
            this.targetId = targetId;
            this.title = title;
            this.queueOrder = queueOrder;
            this.candidateTemplate = candidateTemplate;
            this.runStep = runStep;
            this.run = run;
        }

        public static Entry candidate(FlowCandidate candidate, TaskStepTemplate template) {
            return new Entry(Kind.CANDIDATE, candidate.id, template.text, candidate.queueOrder,
                    template, null, null);
        }

        public static Entry runStep(FlowRunSummary run, OccurrenceStep step) {
            return new Entry(Kind.RUN_STEP, run.id, run.seedTitle + ": " + step.text,
                    run.queueOrder, null, step, run);
        }
    }

    public final FlowTaskSheetPlacement placement;
    public final Task task;
    public final List<Entry> entries;

    public FlowTaskSheet(FlowTaskSheetPlacement placement, Task task, List<Entry> entries) {
        if (placement == null || task == null || entries == null || entries.isEmpty())
            throw new IllegalArgumentException("A flow task sheet must be non-empty");
        this.placement = placement;
        this.task = task;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }
}
