package com.autosecretary.features.task.ui.edit.internal.editor;

import android.widget.CheckBox;
import android.widget.EditText;

public class TaskEditFormViews {
    public final EditText titleView;
    public final EditText minDurationView, maxDurationView, cooldownView;
    public final CheckBox toggleRepetition;
    public final EditText repsView, perPeriodView;
    public final CheckBox toggleProgress;
    public final EditText targetView, currentView, minPerRepView, maxPerRepView;

    public TaskEditFormViews(
        TaskEditSectionBinder.BasicInfoViews basicInfoViews,
        TaskEditSectionBinder.SchedulingViews schedulingViews,
        TaskEditSectionBinder.RepetitionViews repetitionViews,
        TaskEditSectionBinder.ProgressViews progressViews
    ) {
        this.titleView = basicInfoViews.titleView;
        this.minDurationView = schedulingViews.minDurationView;
        this.maxDurationView = schedulingViews.maxDurationView;
        this.cooldownView = schedulingViews.cooldownView;
        this.toggleRepetition = repetitionViews.toggleRepetition;
        this.repsView = repetitionViews.repsView;
        this.perPeriodView = repetitionViews.perPeriodView;
        this.toggleProgress = progressViews.toggleProgress;
        this.targetView = progressViews.targetView;
        this.currentView = progressViews.currentView;
        this.minPerRepView = progressViews.minPerRepView;
        this.maxPerRepView = progressViews.maxPerRepView;
    }
}
