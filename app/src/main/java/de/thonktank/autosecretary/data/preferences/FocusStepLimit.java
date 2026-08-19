package de.thonktank.autosecretary.data.preferences;

public enum FocusStepLimit {
    AUTO(0), ONE(1), TWO(2), THREE(3), FOUR(4), FIVE(5);

    public final int maximumFollowingSteps;

    FocusStepLimit(int maximumFollowingSteps) {
        this.maximumFollowingSteps = maximumFollowingSteps;
    }

    public boolean automatic() { return this == AUTO; }
}
