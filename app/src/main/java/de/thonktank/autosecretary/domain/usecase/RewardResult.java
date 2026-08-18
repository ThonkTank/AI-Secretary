package de.thonktank.autosecretary.domain.usecase;

/** Result used by presentation to animate a successfully persisted reward change. */
public final class RewardResult {
    public enum Target { NONE, VESSEL, HEAD }
    public final int xp;
    public final Target target;
    public final boolean reversed;

    public RewardResult(int xp, Target target, boolean reversed) {
        this.xp = Math.max(0, xp);
        this.target = target;
        this.reversed = reversed;
    }

    public static RewardResult none() { return new RewardResult(0, Target.NONE, false); }
}
