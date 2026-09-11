package de.thonktank.autosecretary.presentation.today;

/** One waiting phase, not an executable checkbox or an unstarted candidate. */
public final class FlowWaitUiModel {
    public final String runId;
    public final String waitId;
    public final String title;
    public final Long readyAtEpochMillis;
    public final boolean elapsed;

    public FlowWaitUiModel(String runId, String waitId, String title, Long readyAtEpochMillis) {
        this(runId, waitId, title, readyAtEpochMillis, false);
    }

    public FlowWaitUiModel(String runId, String waitId, String title, Long readyAtEpochMillis, boolean elapsed) {
        this.runId = java.util.Objects.requireNonNull(runId);
        this.waitId = java.util.Objects.requireNonNull(waitId);
        this.title = java.util.Objects.requireNonNull(title);
        this.readyAtEpochMillis = readyAtEpochMillis;
        this.elapsed = elapsed;
    }
}
