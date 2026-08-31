package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** Optional template-only assistant policy and durable learning state. */
public final class TrainingAssistantProfile {
    public final TrainingAssistantPolicy policy;
    public final TrainingAssistantState state;

    public TrainingAssistantProfile(TrainingAssistantPolicy policy,
                                    TrainingAssistantState state) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.state = state == null ? TrainingAssistantState.calibrating() : state;
        if (this.state.status == TrainingAssistantState.Status.DISABLED)
            throw new IllegalArgumentException("An enabled profile cannot be disabled");
    }

    @Override public boolean equals(Object other) {
        return other instanceof TrainingAssistantProfile
                && policy.equals(((TrainingAssistantProfile) other).policy)
                && state.equals(((TrainingAssistantProfile) other).state);
    }

    @Override public int hashCode() { return Objects.hash(policy, state); }
}
