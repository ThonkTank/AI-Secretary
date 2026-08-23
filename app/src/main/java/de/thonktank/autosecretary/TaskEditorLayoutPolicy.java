package de.thonktank.autosecretary;

import android.content.res.Configuration;
import android.content.res.Resources;

/** Deterministic standard/compact geometry contract for the task editor. */
final class TaskEditorLayoutPolicy {
    static final int COMPACT_WIDTH_DP = 360;
    static final float COMPACT_FONT_SCALE = 1.3f;
    private static final int TALL_PROMPT_HEIGHT_DP = 760;
    private static final int STANDARD_PROMPT_TOP_DP = 250;
    private static final int SAFE_EDGE_DP = 16;

    final boolean compact;
    final int pageStartDp;
    final int pageEndDp;
    final int leafHorizontalPaddingDp;
    final int leafTopPaddingDp;
    final int leafBottomPaddingDp;
    final int footerHeightDp;
    final int weekdayColumns;

    private TaskEditorLayoutPolicy(boolean compact) {
        this.compact = compact;
        pageStartDp = compact ? 18 : 60;
        pageEndDp = compact ? 18 : 22;
        leafHorizontalPaddingDp = compact ? 18 : 26;
        leafTopPaddingDp = compact ? 20 : 26;
        leafBottomPaddingDp = compact ? 22 : 30;
        footerHeightDp = compact ? 112 : 80;
        weekdayColumns = compact ? 4 : 7;
    }

    static TaskEditorLayoutPolicy from(Resources resources) {
        Configuration configuration = resources.getConfiguration();
        int widthDp = configuration.screenWidthDp;
        if (widthDp == Configuration.SCREEN_WIDTH_DP_UNDEFINED || widthDp <= 0) {
            widthDp = Math.round(resources.getDisplayMetrics().widthPixels
                    / resources.getDisplayMetrics().density);
        }
        return new TaskEditorLayoutPolicy(widthDp < COMPACT_WIDTH_DP
                || configuration.fontScale >= COMPACT_FONT_SCALE);
    }

    int promptTopDp(int availableHeightDp, int cardHeightDp) {
        boolean safelyFitsAtReferencePosition = STANDARD_PROMPT_TOP_DP + cardHeightDp
                + SAFE_EDGE_DP <= availableHeightDp;
        if (availableHeightDp >= TALL_PROMPT_HEIGHT_DP && safelyFitsAtReferencePosition)
            return STANDARD_PROMPT_TOP_DP;
        return Math.max(SAFE_EDGE_DP, (availableHeightDp - cardHeightDp) / 2);
    }
}
