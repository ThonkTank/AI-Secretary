package de.thonktank.autosecretary;

import java.util.Objects;

/** Typed presentation command identity used for de-duplication and reward anchoring. */
public final class UiCommand {
    public enum Kind { REFRESH, CREATE, UPDATE, MOVE, DELETE, LOAD_EDITOR, COMPLETE,
        COMPLETE_REMAINING, HARVEST, UNDO, TOGGLE_STEP, RECORD_REPETITION_RESULT,
        CORRECT_REPETITION_RESULT, DEFER, CLOSE }
    public final Kind kind;
    public final String id;

    public UiCommand(Kind kind, String id) {
        if (kind == null) throw new IllegalArgumentException("Command kind is required");
        this.kind = kind;
        this.id = id == null ? "" : id;
    }

    public RewardAnchorKey rewardAnchor() {
        RewardAnchorKey.Kind anchor;
        switch (kind) {
            case HARVEST: anchor = RewardAnchorKey.Kind.VESSEL; break;
            case COMPLETE_REMAINING: anchor = RewardAnchorKey.Kind.REST; break;
            case CLOSE: anchor = RewardAnchorKey.Kind.TASK; break;
            case TOGGLE_STEP: case RECORD_REPETITION_RESULT: case CORRECT_REPETITION_RESULT:
                anchor = RewardAnchorKey.Kind.STEP; break;
            default: anchor = RewardAnchorKey.Kind.OCCURRENCE;
        }
        return new RewardAnchorKey(anchor, id);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof UiCommand)) return false;
        UiCommand value = (UiCommand) other;
        return kind == value.kind && id.equals(value.id);
    }
    @Override public int hashCode() { return Objects.hash(kind, id); }
    @Override public String toString() { return kind.name() + "(" + id + ")"; }
}
