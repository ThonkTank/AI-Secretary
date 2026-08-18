package de.thonktank.autosecretary;

import de.thonktank.autosecretary.domain.model.RewardReceipt;

/** Typed, acknowledgeable presentation effect for one reward transaction. */
public final class RewardEffect {
    public final String id;
    public final int signedXp;
    public final RewardReceipt.Target target;
    public final RewardAnchorKey source;

    private RewardEffect(String id, int signedXp, RewardReceipt.Target target,
                         RewardAnchorKey source) {
        this.id = id; this.signedXp = signedXp; this.target = target; this.source = source;
    }

    public static RewardEffect from(RewardReceipt receipt, UiCommand command) {
        if (receipt == null || receipt.target == RewardReceipt.Target.NONE
                || receipt.transactionId.isEmpty()) return null;
        return new RewardEffect(receipt.transactionId, receipt.xp, receipt.target,
                command.rewardAnchor());
    }
}
