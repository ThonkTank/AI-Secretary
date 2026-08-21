package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** Immutable result of applying one combo multiplier to one XP base value. */
public final class RewardBreakdown {
    public final int baseXp;
    public final int comboStage;
    public final double multiplier;
    public final int resultXp;

    private RewardBreakdown(int baseXp, int comboStage, double multiplier, int resultXp) {
        this.baseXp = baseXp;
        this.comboStage = comboStage;
        this.multiplier = multiplier;
        this.resultXp = resultXp;
    }

    public static RewardBreakdown from(int baseXp, ComboProgress combo) {
        return fromStage(baseXp, combo == null ? 0 : combo.level());
    }

    public static RewardBreakdown fromStage(int baseXp, int comboStage) {
        int normalizedBase = Math.max(0, baseXp);
        int normalizedStage = Math.max(0, comboStage);
        double multiplier = 1d + normalizedStage * .5d;
        int result = (int) Math.round(normalizedBase * multiplier);
        return new RewardBreakdown(normalizedBase, normalizedStage, multiplier, result);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RewardBreakdown)) return false;
        RewardBreakdown that = (RewardBreakdown) other;
        return baseXp == that.baseXp && comboStage == that.comboStage
                && Double.compare(multiplier, that.multiplier) == 0 && resultXp == that.resultXp;
    }

    @Override public int hashCode() {
        return Objects.hash(baseXp, comboStage, multiplier, resultXp);
    }
}
