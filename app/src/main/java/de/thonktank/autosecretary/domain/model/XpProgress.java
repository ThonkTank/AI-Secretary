package de.thonktank.autosecretary.domain.model;

/** Derived global XP level displayed by the head leaf. */
public final class XpProgress {
    public final int total;
    public final int level;
    public final int inLevel;
    public final int required;
    public final float ratio;

    public XpProgress(int total) {
        this.total = Math.max(0, total);
        int nextLevel = 1;
        int remaining = this.total;
        while (remaining >= 100 * nextLevel) {
            remaining -= 100 * nextLevel;
            nextLevel++;
        }
        level = nextLevel;
        inLevel = remaining;
        required = 100 * level;
        ratio = required == 0 ? 0f : remaining / (float) required;
    }
}
