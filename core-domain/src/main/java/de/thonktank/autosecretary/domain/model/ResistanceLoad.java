package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** Exact resistance prescription without floating-point or implicit unit conversion. */
public final class ResistanceLoad {
    public enum Mode { UNSPECIFIED, EXTERNAL, BODYWEIGHT, BODYWEIGHT_PLUS, ASSISTED_BODYWEIGHT }
    public enum Unit { NONE, KG, LB }

    public final Mode mode;
    public final Unit unit;
    /** Thousandths of the selected unit. Null for unspecified and plain bodyweight. */
    public final Long milliUnits;

    private ResistanceLoad(Mode mode, Unit unit, Long milliUnits) {
        if (mode == null || unit == null)
            throw new IllegalArgumentException("Resistance load mode and unit are required");
        boolean numeric = mode == Mode.EXTERNAL || mode == Mode.BODYWEIGHT_PLUS
                || mode == Mode.ASSISTED_BODYWEIGHT;
        if (numeric && (unit == Unit.NONE || milliUnits == null || milliUnits < 0))
            throw new IllegalArgumentException("Numeric resistance needs a non-negative value");
        if (!numeric && (unit != Unit.NONE || milliUnits != null))
            throw new IllegalArgumentException("Non-numeric resistance must not carry a value");
        this.mode = mode;
        this.unit = unit;
        this.milliUnits = milliUnits;
    }

    public static ResistanceLoad unspecified() {
        return new ResistanceLoad(Mode.UNSPECIFIED, Unit.NONE, null);
    }

    public static ResistanceLoad bodyweight() {
        return new ResistanceLoad(Mode.BODYWEIGHT, Unit.NONE, null);
    }

    public static ResistanceLoad numeric(Mode mode, Unit unit, long milliUnits) {
        return new ResistanceLoad(mode, unit, milliUnits);
    }

    public static ResistanceLoad restore(String mode, String unit, Long milliUnits) {
        try {
            return new ResistanceLoad(Mode.valueOf(mode), Unit.valueOf(unit), milliUnits);
        } catch (RuntimeException invalid) {
            return unspecified();
        }
    }

    public boolean adjustable() {
        return mode == Mode.EXTERNAL || mode == Mode.BODYWEIGHT_PLUS
                || mode == Mode.ASSISTED_BODYWEIGHT;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof ResistanceLoad)) return false;
        ResistanceLoad value = (ResistanceLoad) other;
        return mode == value.mode && unit == value.unit
                && Objects.equals(milliUnits, value.milliUnits);
    }

    @Override public int hashCode() { return Objects.hash(mode, unit, milliUnits); }
}
