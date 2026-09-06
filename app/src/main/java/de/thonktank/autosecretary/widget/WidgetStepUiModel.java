package de.thonktank.autosecretary.widget;

/** Step projection owned by the home-screen widget. */
public final class WidgetStepUiModel {
    public final String id;
    public final String title;
    public final String subtitle;
    public final boolean done;
    public final boolean requiresApp;

    private WidgetStepUiModel(String id, String title, String subtitle, boolean done,
                              boolean requiresApp) {
        if (id == null || id.isEmpty() || title == null || title.trim().isEmpty()
                || subtitle == null)
            throw new IllegalArgumentException("Widget step content is required");
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.done = done;
        this.requiresApp = requiresApp;
    }

    public static WidgetStepUiModel of(String id, String title, String subtitle, boolean done) {
        return new WidgetStepUiModel(id, title, subtitle, done, false);
    }

    public static WidgetStepUiModel requiringApp(String id, String title, String subtitle,
                                                  boolean done) {
        return new WidgetStepUiModel(id, title, subtitle, done, true);
    }
}
