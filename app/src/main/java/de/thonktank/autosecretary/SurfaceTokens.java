package de.thonktank.autosecretary;

public final class SurfaceTokens {
    public final int background;
    public final int leafPrimary;
    public final int leafSecondary;
    public final int leafTertiary;
    public final int accent;
    public final int accentContent;
    public final int lightAccent;
    public final int lightAccentContent;
    public final int calendar;
    public final int calendarContent;
    public final int calendarLabel;

    public SurfaceTokens(int background, int leafPrimary, int leafSecondary, int leafTertiary,
                         int accent, int accentContent, int lightAccent,
                         int lightAccentContent, int calendar, int calendarContent,
                         int calendarLabel) {
        this.background = background;
        this.leafPrimary = leafPrimary;
        this.leafSecondary = leafSecondary;
        this.leafTertiary = leafTertiary;
        this.accent = accent;
        this.accentContent = accentContent;
        this.lightAccent = lightAccent;
        this.lightAccentContent = lightAccentContent;
        this.calendar = calendar;
        this.calendarContent = calendarContent;
        this.calendarLabel = calendarLabel;
    }
}
