package de.thonktank.autosecretary.presentation.today;

/** Historical task row shown only in today's collapsed completion section. */
public final class CompletedTaskUiModel {
    public final String occurrenceId;
    public final String title;
    public final int awardedXp;
    public final boolean undoAvailable;

    private CompletedTaskUiModel(String occurrenceId, String title, int awardedXp,
                                 boolean undoAvailable) {
        if (occurrenceId == null || occurrenceId.isEmpty() || title == null
                || title.trim().isEmpty())
            throw new IllegalArgumentException("Completed occurrence identity is required");
        this.occurrenceId = occurrenceId;
        this.title = title;
        this.awardedXp = Math.max(0, awardedXp);
        this.undoAvailable = undoAvailable;
    }

    public static CompletedTaskUiModel of(String occurrenceId, String title, int awardedXp,
                                           boolean undoAvailable) {
        return new CompletedTaskUiModel(occurrenceId, title, awardedXp, undoAvailable);
    }
}
