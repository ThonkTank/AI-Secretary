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
    public final int leafPrimaryEdge;
    public final int leafSecondaryEdge;
    public final int leafTertiaryEdge;
    public final int calendarEdge;
    public final float shadowAlpha;

    public SurfaceTokens(int background, int leafPrimary, int leafSecondary, int leafTertiary,
                         int accent, int accentContent, int lightAccent,
                         int lightAccentContent, int calendar, int calendarContent,
                         int calendarLabel, int leafPrimaryEdge, int leafSecondaryEdge,
                         int leafTertiaryEdge, int calendarEdge, float shadowAlpha) {
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
        this.leafPrimaryEdge = leafPrimaryEdge;
        this.leafSecondaryEdge = leafSecondaryEdge;
        this.leafTertiaryEdge = leafTertiaryEdge;
        this.calendarEdge = calendarEdge;
        this.shadowAlpha = shadowAlpha;
    }
}
