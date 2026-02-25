package com.autosecretary.features.task.ui.edit;

import android.widget.CheckBox;
import android.widget.EditText;

public class TaskEditFormViews {
    public final EditText titleView;
    public final EditText minDurationView, maxDurationView, cooldownView;
    public final CheckBox toggleRepetition;
    public final EditText repsView, perPeriodView;
    public final CheckBox toggleProgress;
    public final EditText targetView, currentView, minPerRepView, maxPerRepView;

    public TaskEditFormViews(EditText titleView, EditText minDurationView, EditText maxDurationView,
                             EditText cooldownView, CheckBox toggleRepetition, EditText repsView,
                             EditText perPeriodView, CheckBox toggleProgress, EditText targetView,
                             EditText currentView, EditText minPerRepView, EditText maxPerRepView) {
        this.titleView = titleView;
        this.minDurationView = minDurationView; this.maxDurationView = maxDurationView;
        this.cooldownView = cooldownView; this.toggleRepetition = toggleRepetition;
        this.repsView = repsView; this.perPeriodView = perPeriodView;
        this.toggleProgress = toggleProgress;
        this.targetView = targetView; this.currentView = currentView;
        this.minPerRepView = minPerRepView; this.maxPerRepView = maxPerRepView;
    }
}
