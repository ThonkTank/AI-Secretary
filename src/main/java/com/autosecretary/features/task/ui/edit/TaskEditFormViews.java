package com.autosecretary.features.task.ui.edit;

import android.widget.CheckBox;
import android.widget.EditText;

public class TaskEditFormViews {
    public final EditText titleView;
    public final EditText minDurationView, maxDurationView, cooldownView, budgetRequirementCentsView, fixedDateView, fixedStartView, fixedDurationView;
    public final CheckBox fixedAppointmentView, toggleRepetition;
    public final EditText repsView, perPeriodView;
    public final CheckBox toggleProgress;
    public final EditText targetView, currentView, minPerRepView, maxPerRepView;

    public TaskEditFormViews(EditText titleView, EditText minDurationView, EditText maxDurationView,
                             EditText cooldownView, EditText budgetRequirementCentsView, CheckBox fixedAppointmentView,
                             EditText fixedDateView, EditText fixedStartView, EditText fixedDurationView,
                             CheckBox toggleRepetition, EditText repsView,
                             EditText perPeriodView, CheckBox toggleProgress, EditText targetView,
                             EditText currentView, EditText minPerRepView, EditText maxPerRepView) {
        this.titleView = titleView;
        this.minDurationView = minDurationView; this.maxDurationView = maxDurationView;
        this.cooldownView = cooldownView; this.budgetRequirementCentsView = budgetRequirementCentsView;
        this.fixedAppointmentView = fixedAppointmentView;
        this.fixedDateView = fixedDateView; this.fixedStartView = fixedStartView; this.fixedDurationView = fixedDurationView;
        this.toggleRepetition = toggleRepetition;
        this.repsView = repsView; this.perPeriodView = perPeriodView;
        this.toggleProgress = toggleProgress;
        this.targetView = targetView; this.currentView = currentView;
        this.minPerRepView = minPerRepView; this.maxPerRepView = maxPerRepView;
    }
}
