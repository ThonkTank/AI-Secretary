package de.thonktank.autosecretary.presentation.today;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Complete presentation state owned by the Today feature boundary. */
public final class TodayFeatureState {
    public enum Feedback { REORDER_FAILED, REORDER_INTERRUPTED }

    public final TodayUiModel today;
    public final Reorder reorder;
    @Nullable public final Feedback feedback;

    public TodayFeatureState(TodayUiModel today, Reorder reorder,
                             @Nullable Feedback feedback) {
        if (today == null || reorder == null)
            throw new IllegalArgumentException("Today feature state is required");
        this.today = today;
        this.reorder = reorder;
        this.feedback = feedback;
    }

    public static TodayFeatureState idle(TodayUiModel today) {
        return new TodayFeatureState(today, Reorder.idle(openStepIds(today)), null);
    }

    public static List<String> openStepIds(TodayUiModel today) {
        if (today.focus == null) return Collections.emptyList();
        List<String> ids = new ArrayList<>();
        for (de.thonktank.autosecretary.presentation.FocusStepUiModel step
                : today.focus.steps) if (!step.isDone()) ids.add(step.id);
        return Collections.unmodifiableList(ids);
    }

    public static final class Reorder {
        public enum Phase { IDLE, DRAGGING, PERSISTING }

        public final Phase phase;
        public final List<String> canonicalOrder;
        public final List<String> previewOrder;
        @Nullable public final String movingStepId;
        @Nullable public final String commandId;

        private Reorder(Phase phase, List<String> canonicalOrder,
                        List<String> previewOrder, @Nullable String movingStepId,
                        @Nullable String commandId) {
            this.phase = phase;
            this.canonicalOrder = immutable(canonicalOrder);
            this.previewOrder = immutable(previewOrder);
            this.movingStepId = movingStepId;
            this.commandId = commandId;
        }

        static Reorder idle(List<String> canonical) {
            return new Reorder(Phase.IDLE, canonical, canonical, null, null);
        }

        static Reorder dragging(List<String> canonical, List<String> preview,
                                String movingStepId) {
            return new Reorder(Phase.DRAGGING, canonical, preview, movingStepId, null);
        }

        static Reorder persisting(List<String> canonical, List<String> preview,
                                  String movingStepId, String commandId) {
            return new Reorder(Phase.PERSISTING, canonical, preview, movingStepId, commandId);
        }

        private static List<String> immutable(List<String> source) {
            return Collections.unmodifiableList(new ArrayList<>(source));
        }
    }
}
