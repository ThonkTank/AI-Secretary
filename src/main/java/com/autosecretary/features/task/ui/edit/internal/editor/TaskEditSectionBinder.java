package com.autosecretary.features.task.ui.edit.internal.editor;

import android.app.DatePickerDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.fragment.app.DialogFragment;

import com.autosecretary.R;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;
import com.autosecretary.features.task.ui.edit.TaskEditPresenter;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class TaskEditSectionBinder {

    private final DialogFragment fragment;
    private final View rootView;
    private final TaskEditState editState;
    private final TaskEditPresenter presenter;

    public TaskEditSectionBinder(
        DialogFragment fragment,
        View rootView,
        TaskEditState editState,
        TaskEditPresenter presenter
    ) {
        this.fragment = fragment;
        this.rootView = rootView;
        this.editState = editState;
        this.presenter = presenter;
    }

    public BasicInfoViews bindBasicInfo() {
        EditText titleView = rootView.findViewById(R.id.EditTitle);
        EditText descriptionView = rootView.findViewById(R.id.EditDescription);
        Spinner priorityView = rootView.findViewById(R.id.EditPriority);

        titleView.setText(editState.title);
        descriptionView.setText(editState.description);

        ArrayAdapter<Priority> adapter = new ArrayAdapter<>(
            fragment.requireContext(),
            android.R.layout.simple_spinner_item,
            Priority.values()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        priorityView.setAdapter(adapter);
        priorityView.setSelection(editState.priority.ordinal());

        return new BasicInfoViews(titleView, descriptionView, priorityView);
    }

    public SchedulingViews bindScheduling() {
        EditText deadlineView = rootView.findViewById(R.id.EditDeadline);
        com.google.android.material.textfield.TextInputLayout deadlineInputLayout = rootView.findViewById(R.id.DeadlineInputLayout);
        ImageButton clearDeadline = rootView.findViewById(R.id.ClearDeadline);
        CheckBox closeOnMissView = rootView.findViewById(R.id.EditCloseOnMiss);
        EditText minDurationView = rootView.findViewById(R.id.EditMinDuration);
        EditText maxDurationView = rootView.findViewById(R.id.EditMaxDuration);
        EditText cooldownView = rootView.findViewById(R.id.EditCooldown);
        CheckBox adaptiveView = rootView.findViewById(R.id.EditAdaptive);

        SchedulingViews views = new SchedulingViews(
            deadlineView,
            deadlineInputLayout,
            closeOnMissView,
            minDurationView,
            maxDurationView,
            cooldownView,
            adaptiveView
        );

        updateDeadlineDisplay(views);
        deadlineView.setOnClickListener(v -> showDatePicker(views));
        deadlineInputLayout.setEndIconOnClickListener(v -> showDatePicker(views));
        clearDeadline.setOnClickListener(v -> {
            presenter.setEditableDeadline(null);
            updateDeadlineDisplay(views);
        });

        closeOnMissView.setChecked(editState.closeOnMiss);
        minDurationView.setText(String.valueOf(editState.minDuration));
        maxDurationView.setText(String.valueOf(editState.maxDuration));
        cooldownView.setText(String.valueOf(editState.cooldown));
        adaptiveView.setChecked(editState.adaptive);

        return views;
    }

    public RepetitionViews bindRepetition(Runnable onRepetitionChanged) {
        CheckBox toggleRepetition = rootView.findViewById(R.id.ToggleRepetition);
        LinearLayout repetitionContainer = rootView.findViewById(R.id.RepetitionContainer);
        EditText repsView = rootView.findViewById(R.id.EditReps);
        EditText perPeriodView = rootView.findViewById(R.id.EditPerPeriod);
        Spinner periodUnitView = rootView.findViewById(R.id.EditPeriodUnit);

        RepetitionViews views = new RepetitionViews(
            toggleRepetition,
            repetitionContainer,
            repsView,
            perPeriodView,
            periodUnitView
        );

        boolean hasRepetition = editState.reps > 0;
        toggleRepetition.setChecked(hasRepetition);
        repetitionContainer.setVisibility(hasRepetition ? View.VISIBLE : View.GONE);

        repsView.setText(String.valueOf(editState.reps > 0 ? editState.reps : 1));
        perPeriodView.setText(String.valueOf(editState.perPeriod > 0 ? editState.perPeriod : 1));

        ArrayAdapter<Period> periodAdapter = new ArrayAdapter<>(
            fragment.requireContext(),
            android.R.layout.simple_spinner_item,
            Period.values()
        );
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        periodUnitView.setAdapter(periodAdapter);
        periodUnitView.setSelection((editState.periodUnit != null ? editState.periodUnit : Period.DAY).ordinal());

        presenter.initializeRepetitionState(
            toggleRepetition.isChecked(),
            repsView.getText().toString(),
            perPeriodView.getText().toString(),
            (Period) periodUnitView.getSelectedItem()
        );

        toggleRepetition.setOnCheckedChangeListener((btn, checked) -> {
            repetitionContainer.setVisibility(checked ? View.VISIBLE : View.GONE);
            onRepetitionChanged.run();
        });

        TextWatcher repWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                onRepetitionChanged.run();
            }
        };
        repsView.addTextChangedListener(repWatcher);
        perPeriodView.addTextChangedListener(repWatcher);

        periodUnitView.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onRepetitionChanged.run();
            }
        });

        return views;
    }

    public ProgressViews bindProgress() {
        CheckBox toggleProgress = rootView.findViewById(R.id.ToggleProgress);
        LinearLayout progressContainer = rootView.findViewById(R.id.ProgressContainer);
        EditText unitView = rootView.findViewById(R.id.EditUnit);
        EditText targetView = rootView.findViewById(R.id.EditTarget);
        EditText currentView = rootView.findViewById(R.id.EditCurrent);
        CheckBox resetPerRepView = rootView.findViewById(R.id.EditResetPerRep);
        EditText minPerRepView = rootView.findViewById(R.id.EditMinPerRep);
        EditText maxPerRepView = rootView.findViewById(R.id.EditMaxPerRep);

        ProgressViews views = new ProgressViews(
            toggleProgress,
            progressContainer,
            unitView,
            targetView,
            currentView,
            resetPerRepView,
            minPerRepView,
            maxPerRepView
        );

        boolean hasProgress = editState.target > 0;
        toggleProgress.setChecked(hasProgress);
        progressContainer.setVisibility(hasProgress ? View.VISIBLE : View.GONE);

        unitView.setText(editState.unit != null ? editState.unit : "");
        targetView.setText(String.valueOf(editState.target));
        currentView.setText(String.valueOf(editState.current));
        resetPerRepView.setChecked(editState.resetPerRep);
        minPerRepView.setText(String.valueOf(editState.minPerRep));
        maxPerRepView.setText(String.valueOf(editState.maxPerRep));

        toggleProgress.setOnCheckedChangeListener((btn, checked) ->
            progressContainer.setVisibility(checked ? View.VISIBLE : View.GONE));

        return views;
    }

    private void updateDeadlineDisplay(SchedulingViews views) {
        if (presenter.getEditableDeadline() != null) {
            String deadlineText = presenter.getEditableDeadline().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            views.deadlineView.setText(deadlineText);
            views.deadlineView.setContentDescription(
                fragment.getString(R.string.task_edit_deadline_content_description, deadlineText)
            );
        } else {
            String noDeadlineText = fragment.getString(R.string.task_edit_deadline_none);
            views.deadlineView.setText(noDeadlineText);
            views.deadlineView.setContentDescription(
                fragment.getString(R.string.task_edit_deadline_content_description, noDeadlineText)
            );
        }
    }

    private void showDatePicker(SchedulingViews views) {
        LocalDate current = presenter.getEditableDeadline() != null ? presenter.getEditableDeadline() : LocalDate.now();
        new DatePickerDialog(fragment.requireContext(), (picker, year, month, day) -> {
            presenter.setEditableDeadline(LocalDate.of(year, month + 1, day));
            updateDeadlineDisplay(views);
        }, current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth()).show();
    }

    public static final class BasicInfoViews {
        public final EditText titleView;
        public final EditText descriptionView;
        public final Spinner priorityView;

        private BasicInfoViews(EditText titleView, EditText descriptionView, Spinner priorityView) {
            this.titleView = titleView;
            this.descriptionView = descriptionView;
            this.priorityView = priorityView;
        }
    }

    public static final class SchedulingViews {
        public final EditText deadlineView;
        public final com.google.android.material.textfield.TextInputLayout deadlineInputLayout;
        public final CheckBox closeOnMissView;
        public final EditText minDurationView;
        public final EditText maxDurationView;
        public final EditText cooldownView;
        public final CheckBox adaptiveView;

        private SchedulingViews(
            EditText deadlineView,
            com.google.android.material.textfield.TextInputLayout deadlineInputLayout,
            CheckBox closeOnMissView,
            EditText minDurationView,
            EditText maxDurationView,
            EditText cooldownView,
            CheckBox adaptiveView
        ) {
            this.deadlineView = deadlineView;
            this.deadlineInputLayout = deadlineInputLayout;
            this.closeOnMissView = closeOnMissView;
            this.minDurationView = minDurationView;
            this.maxDurationView = maxDurationView;
            this.cooldownView = cooldownView;
            this.adaptiveView = adaptiveView;
        }
    }

    public static final class RepetitionViews {
        public final CheckBox toggleRepetition;
        public final LinearLayout repetitionContainer;
        public final EditText repsView;
        public final EditText perPeriodView;
        public final Spinner periodUnitView;

        private RepetitionViews(
            CheckBox toggleRepetition,
            LinearLayout repetitionContainer,
            EditText repsView,
            EditText perPeriodView,
            Spinner periodUnitView
        ) {
            this.toggleRepetition = toggleRepetition;
            this.repetitionContainer = repetitionContainer;
            this.repsView = repsView;
            this.perPeriodView = perPeriodView;
            this.periodUnitView = periodUnitView;
        }
    }

    public static final class ProgressViews {
        public final CheckBox toggleProgress;
        public final LinearLayout progressContainer;
        public final EditText unitView;
        public final EditText targetView;
        public final EditText currentView;
        public final CheckBox resetPerRepView;
        public final EditText minPerRepView;
        public final EditText maxPerRepView;

        private ProgressViews(
            CheckBox toggleProgress,
            LinearLayout progressContainer,
            EditText unitView,
            EditText targetView,
            EditText currentView,
            CheckBox resetPerRepView,
            EditText minPerRepView,
            EditText maxPerRepView
        ) {
            this.toggleProgress = toggleProgress;
            this.progressContainer = progressContainer;
            this.unitView = unitView;
            this.targetView = targetView;
            this.currentView = currentView;
            this.resetPerRepView = resetPerRepView;
            this.minPerRepView = minPerRepView;
            this.maxPerRepView = maxPerRepView;
        }
    }

    private static abstract class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    private static abstract class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
        @Override public void onNothingSelected(AdapterView<?> parent) {}
    }
}
