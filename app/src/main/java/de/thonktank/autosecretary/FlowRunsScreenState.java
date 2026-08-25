package de.thonktank.autosecretary;

import androidx.annotation.Nullable;

import de.thonktank.autosecretary.domain.model.FlowRunSummary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Complete immutable render state for active flow runs. */
public final class FlowRunsScreenState {
    public final List<FlowRunSummary> runs;
    public final boolean loading;
    public final boolean changing;
    public final long errorId;
    @Nullable public final String errorMessage;

    private FlowRunsScreenState(List<FlowRunSummary> runs, boolean loading, boolean changing,
                                long errorId, @Nullable String errorMessage) {
        this.runs = Collections.unmodifiableList(new ArrayList<>(runs));
        this.loading = loading;
        this.changing = changing;
        this.errorId = errorId;
        this.errorMessage = errorMessage;
    }

    public static FlowRunsScreenState idle() {
        return new FlowRunsScreenState(Collections.emptyList(), false, false, 0L, null);
    }

    public FlowRunsScreenState withRuns(List<FlowRunSummary> values) {
        return new FlowRunsScreenState(values, false, false, errorId, errorMessage);
    }

    public FlowRunsScreenState withLoading() {
        return new FlowRunsScreenState(runs, true, changing, errorId, errorMessage);
    }

    public FlowRunsScreenState withChanging() {
        return new FlowRunsScreenState(runs, loading, true, errorId, errorMessage);
    }

    public FlowRunsScreenState withError(long id, String message) {
        return new FlowRunsScreenState(runs, false, false, id, message);
    }

    public FlowRunsScreenState acknowledgeError(long id) {
        return errorId != id ? this : new FlowRunsScreenState(runs, loading, changing, 0L, null);
    }
}
