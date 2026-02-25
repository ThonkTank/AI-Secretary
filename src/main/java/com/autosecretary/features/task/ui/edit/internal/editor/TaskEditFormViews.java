package com.autosecretary.features.task.ui.edit.internal.editor;

import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.google.android.material.textfield.TextInputLayout;

public class TaskEditFormViews {
    public EditText titleView;
    public EditText descriptionView;
    public Spinner priorityView;

    public EditText deadlineView;
    public TextInputLayout deadlineInputLayout;
    public CheckBox closeOnMissView;
    public CheckBox adaptiveView;
    public EditText minDurationView;
    public EditText maxDurationView;
    public EditText cooldownView;

    public CheckBox toggleRepetition;
    public LinearLayout repetitionContainer;
    public EditText repsView;
    public EditText perPeriodView;
    public Spinner periodUnitView;

    public CheckBox toggleProgress;
    public LinearLayout progressContainer;
    public EditText unitView;
    public EditText targetView;
    public EditText currentView;
    public EditText minPerRepView;
    public EditText maxPerRepView;
    public CheckBox resetPerRepView;
}
