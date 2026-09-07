package de.thonktank.autosecretary.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Task projection containing exactly the data consumed by widget layouts. */
public final class WidgetTaskUiModel {
    public final String taskId;
    public final String itemId;
    public final String title;
    public final boolean overdue;
    public final boolean terminalCondition;
    public final String primaryActionLabel;
    public final List<WidgetStepUiModel> steps;
    public final boolean requiresApp;

    private WidgetTaskUiModel(String taskId, String itemId, String title, boolean overdue,
                              boolean terminalCondition, String primaryActionLabel,
                              List<WidgetStepUiModel> steps, boolean requiresApp) {
        if (taskId == null || taskId.isEmpty() || itemId == null || title == null
                || title.trim().isEmpty() || primaryActionLabel == null || steps == null)
            throw new IllegalArgumentException("Widget task content is required");
        this.taskId = taskId;
        this.itemId = itemId;
        this.title = title;
        this.overdue = overdue;
        this.terminalCondition = terminalCondition;
        this.primaryActionLabel = primaryActionLabel;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.requiresApp = requiresApp;
    }

    public static WidgetTaskUiModel of(String taskId, String itemId, String title,
                                       boolean overdue, boolean terminalCondition,
                                       String primaryActionLabel,
                                       List<WidgetStepUiModel> steps) {
        return new WidgetTaskUiModel(taskId, itemId, title, overdue, terminalCondition,
                primaryActionLabel, steps, false);
    }

    public static WidgetTaskUiModel requiringApp(String taskId, String itemId, String title,
                                                  boolean overdue, String primaryActionLabel,
                                                  List<WidgetStepUiModel> steps) {
        return new WidgetTaskUiModel(taskId, itemId, title, overdue, false,
                primaryActionLabel, steps, true);
    }
}
