package de.thonktank.autosecretary.widget;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.WidgetSizeClassifier;

public final class WidgetUiModel {
    public enum PrimaryAction { NONE, OPEN_EDITOR, COMPLETE_OCCURRENCE, CONFIRM_CLOSE }

    public static final class Step {
        public final String id;
        public final String title;
        public final String subtitle;
        public final boolean done;

        Step(String id, String title, String subtitle, boolean done) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.done = done;
        }
    }

    public static final class CalendarItem {
        public final String time;
        public final String title;

        CalendarItem(String time, String title) {
            this.time = time;
            this.title = title;
        }
    }

    public final WidgetSizeClassifier.Size size;
    public final DayPalette palette;
    public final String marker;
    public final String title;
    public final boolean overdue;
    public final boolean empty;
    public final List<Step> steps;
    public final List<Boolean> progress;
    public final int additionalStepCount;
    @Nullable public final String afterTitle;
    @Nullable public final CalendarItem calendar;
    public final PrimaryAction primaryAction;
    @Nullable public final String primaryActionId;
    @Nullable public final String primaryActionLabel;
    public final boolean showAdd;
    public final String taskTitle;

    WidgetUiModel(WidgetSizeClassifier.Size size, DayPalette palette, String marker,
                  String title, boolean overdue, boolean empty, List<Step> steps,
                  List<Boolean> progress, int additionalStepCount, @Nullable String afterTitle,
                  @Nullable CalendarItem calendar, PrimaryAction primaryAction,
                  @Nullable String primaryActionId, @Nullable String primaryActionLabel,
                  boolean showAdd, String taskTitle) {
        this.size = size;
        this.palette = palette;
        this.marker = marker;
        this.title = title;
        this.overdue = overdue;
        this.empty = empty;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.progress = Collections.unmodifiableList(new ArrayList<>(progress));
        this.additionalStepCount = additionalStepCount;
        this.afterTitle = afterTitle;
        this.calendar = calendar;
        this.primaryAction = primaryAction;
        this.primaryActionId = primaryActionId;
        this.primaryActionLabel = primaryActionLabel;
        this.showAdd = showAdd;
        this.taskTitle = taskTitle;
    }
}
