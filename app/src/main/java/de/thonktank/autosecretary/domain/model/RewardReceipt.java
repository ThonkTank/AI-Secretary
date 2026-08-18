package de.thonktank.autosecretary.domain.model;

/** Immutable receipt emitted by a transactional XP/combo booking or reversal. */
public final class RewardReceipt {
    public enum Target { NONE, VESSEL, HEAD }

    public final int xp;
    public final int comboPointDelta;
    public final Target target;
    public final boolean reversed;

    public RewardReceipt(int xp, int comboPointDelta, Target target, boolean reversed) {
        this.xp = Math.max(0, xp);
        this.comboPointDelta = comboPointDelta;
        this.target = target == null ? Target.NONE : target;
        this.reversed = reversed;
    }

    public RewardReceipt(int xp, Target target, boolean reversed) {
        this(xp, 0, target, reversed);
    }

    public static RewardReceipt none() {
        return new RewardReceipt(0, 0, Target.NONE, false);
    }
}
