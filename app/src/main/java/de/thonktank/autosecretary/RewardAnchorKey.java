package de.thonktank.autosecretary;

import java.util.Objects;

/** Typed identity of a visual reward source or destination. */
public final class RewardAnchorKey {
    public enum Kind { HEAD, VESSEL, TASK, OCCURRENCE, REST, STEP }
    public final Kind kind;
    public final String id;

    public RewardAnchorKey(Kind kind, String id) {
        if (kind == null) throw new IllegalArgumentException("Reward anchor kind is required");
        this.kind = kind;
        this.id = id == null ? "" : id;
    }

    public static RewardAnchorKey head() { return new RewardAnchorKey(Kind.HEAD, ""); }

    @Override public boolean equals(Object other) {
        if (!(other instanceof RewardAnchorKey)) return false;
        RewardAnchorKey value = (RewardAnchorKey) other;
        return kind == value.kind && id.equals(value.id);
    }
    @Override public int hashCode() { return Objects.hash(kind, id); }
}
