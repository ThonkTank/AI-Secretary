package de.thonktank.autosecretary.presentation.today;

import de.thonktank.autosecretary.domain.model.RewardBreakdown;

/** Complete presentation input for one XP vessel. */
public final class XpVesselUiModel {
    public final RewardBreakdown reward;
    public final int done;
    public final int total;
    public final boolean ready;
    public final String multiplierLabel;
    public final String breakdownLabel;

    private XpVesselUiModel(RewardBreakdown reward, int done, int total, boolean ready,
                            String multiplierLabel, String breakdownLabel) {
        if (reward == null || multiplierLabel == null || breakdownLabel == null)
            throw new IllegalArgumentException("Vessel reward and labels are required");
        this.reward = reward;
        this.done = Math.max(0, done);
        this.total = Math.max(0, total);
        this.ready = ready;
        this.multiplierLabel = multiplierLabel;
        this.breakdownLabel = breakdownLabel;
    }

    public static XpVesselUiModel of(RewardBreakdown reward, int done, int total,
                                     boolean ready, RewardTextFormatter formatter) {
        if (formatter == null) throw new IllegalArgumentException("Reward formatter is required");
        return new XpVesselUiModel(reward, done, total, ready,
                formatter.multiplier(reward.multiplier),
                formatter.breakdown(reward.baseXp, reward.multiplier));
    }
}
