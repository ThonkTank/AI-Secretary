package com.autosecretary.ui;

import java.util.List;

/** Pure expansion policy for the Today surface. */
public final class TodayRowVisibility {
    private TodayRowVisibility() { }

    public static List<TodayRow> visible(Dashboard dashboard, boolean expanded) {
        return expanded ? dashboard.today()
                : dashboard.today().subList(0, Math.min(3, dashboard.today().size()));
    }
}
