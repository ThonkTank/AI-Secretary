package de.thonktank.autosecretary;

import androidx.annotation.Nullable;

import de.thonktank.autosecretary.domain.model.StepFlowSetup;
import de.thonktank.autosecretary.domain.model.TaskCatalog;

import java.util.Collections;

/** Complete immutable render state for the flow-setup screen. */
public final class FlowSetupScreenState {
    public final TaskCatalog catalog;
    public final int selectedTaskIndex;
    @Nullable public final StepFlowSetup setup;
    public final FlowSetupDraft draft;
    public final boolean loading;
    public final boolean saving;
    @Nullable public final Feedback feedback;

    private FlowSetupScreenState(TaskCatalog catalog, int selectedTaskIndex,
                                 @Nullable StepFlowSetup setup, FlowSetupDraft draft,
                                 boolean loading, boolean saving,
                                 @Nullable Feedback feedback) {
        this.catalog = catalog;
        this.selectedTaskIndex = selectedTaskIndex;
        this.setup = setup;
        this.draft = draft;
        this.loading = loading;
        this.saving = saving;
        this.feedback = feedback;
    }

    public static FlowSetupScreenState loading() {
        return new FlowSetupScreenState(new TaskCatalog(Collections.emptyList()), -1,
                null, FlowSetupDraft.empty(), true, false, null);
    }

    public FlowSetupScreenState loaded(TaskCatalog value, int index,
                                       @Nullable StepFlowSetup setup,
                                       FlowSetupDraft draft) {
        return new FlowSetupScreenState(value, index, setup, draft,
                false, false, feedback);
    }

    public FlowSetupScreenState loadingTask(int index) {
        return new FlowSetupScreenState(catalog, index, null, FlowSetupDraft.empty(),
                true, false, feedback);
    }

    public FlowSetupScreenState withDraft(FlowSetupDraft value) {
        return new FlowSetupScreenState(catalog, selectedTaskIndex, setup, value,
                loading, saving, feedback);
    }

    public FlowSetupScreenState withSaving() {
        return new FlowSetupScreenState(catalog, selectedTaskIndex, setup, draft,
                loading, true, feedback);
    }

    public FlowSetupScreenState withSetup(StepFlowSetup value, FlowSetupDraft currentDraft) {
        return new FlowSetupScreenState(catalog, selectedTaskIndex, value, currentDraft,
                false, false, feedback);
    }

    public FlowSetupScreenState withFeedback(Feedback value) {
        return new FlowSetupScreenState(catalog, selectedTaskIndex, setup, draft,
                false, false, value);
    }

    public FlowSetupScreenState acknowledgeFeedback(long id) {
        return feedback == null || feedback.id != id ? this
                : new FlowSetupScreenState(catalog, selectedTaskIndex, setup, draft,
                loading, saving, null);
    }

    public static final class Feedback {
        public final long id;
        public final String message;
        public final boolean saved;

        Feedback(long id, String message, boolean saved) {
            this.id = id;
            this.message = message;
            this.saved = saved;
        }
    }
}
