package de.thonktank.autosecretary.presentation.today;

import androidx.annotation.Nullable;

import de.thonktank.autosecretary.RepetitionInputState;
import de.thonktank.autosecretary.RewardEffectQueue;
import de.thonktank.autosecretary.UiCommand;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.timer.TimerManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Complete atomic state for the Today screen and its acknowledgeable effects. */
public final class TodayScreenState {
    public final TodayFeatureState feature;
    public final boolean loading;
    public final Set<UiCommand> runningActions;
    public final RepetitionInputState repetitionInput;
    public final FocusStepLimit focusStepLimit;
    public final TimerManager.Snapshot timers;
    public final RewardEffectQueue.Snapshot rewards;
    public final List<TodayRequest> requests;

    public TodayScreenState(TodayFeatureState feature, boolean loading,
                            Set<UiCommand> runningActions,
                            RepetitionInputState repetitionInput,
                            FocusStepLimit focusStepLimit,
                            TimerManager.Snapshot timers,
                            RewardEffectQueue.Snapshot rewards,
                            List<TodayRequest> requests) {
        if (feature == null || runningActions == null || repetitionInput == null
                || focusStepLimit == null || timers == null || rewards == null
                || requests == null)
            throw new IllegalArgumentException("Complete Today state is required");
        this.feature = feature;
        this.loading = loading;
        this.runningActions = Collections.unmodifiableSet(
                new LinkedHashSet<>(runningActions));
        this.repetitionInput = repetitionInput;
        this.focusStepLimit = focusStepLimit;
        this.timers = timers;
        this.rewards = rewards;
        this.requests = Collections.unmodifiableList(new ArrayList<>(requests));
    }

    public TodayUiModel today() { return feature.today; }
    public boolean isRunning(UiCommand key) { return runningActions.contains(key); }

    public TodayScreenState withFeature(TodayFeatureState value) {
        return copy(value, false, runningActions,
                repetitionInput.reconcile(value.today.focus), focusStepLimit,
                timers, rewards, requests);
    }

    public TodayScreenState withLoading(boolean value) {
        return copy(feature, value, runningActions, repetitionInput, focusStepLimit,
                timers, rewards, requests);
    }

    public TodayScreenState withRunningActions(Set<UiCommand> value) {
        return copy(feature, loading, value, repetitionInput, focusStepLimit,
                timers, rewards, requests);
    }

    public TodayScreenState withRepetitionInput(RepetitionInputState value) {
        return copy(feature, loading, runningActions, value, focusStepLimit,
                timers, rewards, requests);
    }

    public TodayScreenState withFocusStepLimit(FocusStepLimit value) {
        return copy(feature, loading, runningActions, repetitionInput, value,
                timers, rewards, requests);
    }

    public TodayScreenState withTimers(TimerManager.Snapshot value) {
        return copy(feature, loading, runningActions, repetitionInput, focusStepLimit,
                value, rewards, requests);
    }

    public TodayScreenState withRewards(RewardEffectQueue.Snapshot value) {
        return copy(feature, loading, runningActions, repetitionInput, focusStepLimit,
                timers, value, requests);
    }

    public TodayScreenState enqueue(TodayRequest value) {
        for (TodayRequest request : requests) if (request.sameWorkAs(value)) return this;
        ArrayList<TodayRequest> next = new ArrayList<>(requests);
        next.add(value);
        return withRequests(next);
    }

    public TodayScreenState acknowledge(String id) {
        ArrayList<TodayRequest> next = new ArrayList<>(requests.size());
        for (TodayRequest request : requests) if (!request.id.equals(id)) next.add(request);
        return next.size() == requests.size() ? this : withRequests(next);
    }

    public TodayScreenState replace(String id, TodayRequest value) {
        ArrayList<TodayRequest> next = new ArrayList<>(requests.size());
        boolean replaced = false;
        for (TodayRequest request : requests) {
            if (request.id.equals(id)) {
                next.add(value);
                replaced = true;
            } else next.add(request);
        }
        return replaced ? withRequests(next) : this;
    }

    @Nullable public TodayRequest request(String id) {
        for (TodayRequest request : requests) if (request.id.equals(id)) return request;
        return null;
    }

    @Nullable public TodayRequest firstRequest() {
        return requests.isEmpty() ? null : requests.get(0);
    }

    private TodayScreenState withRequests(List<TodayRequest> value) {
        return copy(feature, loading, runningActions, repetitionInput, focusStepLimit,
                timers, rewards, value);
    }

    private static TodayScreenState copy(TodayFeatureState feature, boolean loading,
                                         Set<UiCommand> runningActions,
                                         RepetitionInputState repetitionInput,
                                         FocusStepLimit focusStepLimit,
                                         TimerManager.Snapshot timers,
                                         RewardEffectQueue.Snapshot rewards,
                                         List<TodayRequest> requests) {
        return new TodayScreenState(feature, loading, runningActions, repetitionInput,
                focusStepLimit, timers, rewards, requests);
    }
}
