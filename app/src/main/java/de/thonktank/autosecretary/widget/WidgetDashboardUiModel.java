package de.thonktank.autosecretary.widget;

import androidx.annotation.Nullable;

/** Minimal dashboard projection used as input to size-specific widget rendering. */
public final class WidgetDashboardUiModel {
    @Nullable public final WidgetTaskUiModel focus;
    @Nullable public final String afterTitle;

    private WidgetDashboardUiModel(@Nullable WidgetTaskUiModel focus,
                                   @Nullable String afterTitle) {
        this.focus = focus;
        this.afterTitle = afterTitle;
    }

    public static WidgetDashboardUiModel empty() {
        return new WidgetDashboardUiModel(null, null);
    }

    public static WidgetDashboardUiModel of(WidgetTaskUiModel focus,
                                            @Nullable String afterTitle) {
        if (focus == null) throw new IllegalArgumentException("Widget focus is required");
        return new WidgetDashboardUiModel(focus, afterTitle);
    }
}
