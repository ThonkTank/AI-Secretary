package de.thonktank.autosecretary.presentation.today;

/** Closed identity for a focusable Today card. */
public final class TodayItemTarget {
    public enum Kind { OCCURRENCE, FLOW_TASK_SHEET, TASK }

    public final Kind kind;
    public final String id;

    private TodayItemTarget(Kind kind, String id) {
        if (kind == null || id == null || id.trim().isEmpty())
            throw new IllegalArgumentException("Today item target is required");
        this.kind = kind;
        this.id = id;
    }

    public static TodayItemTarget occurrence(String id) {
        return new TodayItemTarget(Kind.OCCURRENCE, id);
    }

    public static TodayItemTarget flowTaskSheet(String id) {
        return new TodayItemTarget(Kind.FLOW_TASK_SHEET, id);
    }

    public static TodayItemTarget task(String id) {
        return new TodayItemTarget(Kind.TASK, id);
    }
}
