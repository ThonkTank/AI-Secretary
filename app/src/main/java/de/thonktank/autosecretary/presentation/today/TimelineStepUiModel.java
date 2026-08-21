package de.thonktank.autosecretary.presentation.today;

/** Minimal step projection used only for timeline progress bars. */
public final class TimelineStepUiModel {
    public final boolean done;

    private TimelineStepUiModel(boolean done) {
        this.done = done;
    }

    public static TimelineStepUiModel completion(boolean done) {
        return new TimelineStepUiModel(done);
    }
}
