package de.thonktank.autosecretary;

/** Pure height-budget decision used by {@link FocusStepListLayout}. */
final class FocusStepLayoutPolicy {
    private FocusStepLayoutPolicy() { }

    static int visibleFollowing(int availableHeight, int requiredHeight,
                                int[] followingHeights, int moreStatusHeight,
                                int maximumFollowing) {
        int upperBound = Math.min(Math.max(0, maximumFollowing), followingHeights.length);
        int visible = 0;
        int used = requiredHeight;
        for (int candidate = 0; candidate <= upperBound; candidate++) {
            boolean hidesRows = candidate < followingHeights.length;
            int extent = used + (hidesRows ? moreStatusHeight : 0);
            if (extent <= Math.max(0, availableHeight)) visible = candidate;
            if (candidate < upperBound) used += Math.max(0, followingHeights[candidate]);
        }
        return visible;
    }
}
