package com.autosecretary.features.task.ui.edit.internal.editor;

import android.app.DatePickerDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;

import androidx.fragment.app.DialogFragment;

import com.autosecretary.R;
import com.autosecretary.constants.Period;
import com.autosecretary.constants.Priority;
import com.autosecretary.features.task.ui.edit.TaskEditPresenter;
import com.autosecretary.features.task.ui.state.TaskEditState;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class TaskEditSectionBinder {

    private final DialogFragment fragment;
    private final View rootView;
    private final TaskEditState editState;
    private final TaskEditPresenter presenter;
    private final TaskEditFormViews formViews;

    public TaskEditSectionBinder(
        DialogFragment fragment,
        View rootView,
        TaskEditState editState,
        TaskEditPresenter presenter,
        TaskEditFormViews formViews
    ) {
        this.fragment = fragment;
        this.rootView = rootView;
        this.editState = editState;
        this.presenter = presenter;
        this.formViews = formViews;
    }

    public void bindBasicInfo() {
        formViews.titleView = rootView.findViewById(R.id.EditTitle);
        formViews.descriptionView = rootView.findViewById(R.id.EditDescription);
        formViews.priorityView = rootView.findViewById(R.id.EditPriority);

        formViews.titleView.setText(editState.title);
        formViews.descriptionView.setText(editState.description);

        ArrayAdapter<Priority> adapter = new ArrayAdapter<>(
            fragment.requireContext(),
            android.R.layout.simple_spinner_item,
            Priority.values()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        formViews.priorityView.setAdapter(adapter);
        formViews.priorityView.setSelection(editState.priority.ordinal());
    }

    public void bindScheduling() {
        formViews.deadlineView = rootView.findViewById(R.id.EditDeadline);
        formViews.deadlineInputLayout = rootView.findViewById(R.id.DeadlineInputLayout);
        ImageButton clearDeadline = rootView.findViewById(R.id.ClearDeadline);
        formViews.closeOnMissView = rootView.findViewById(R.id.EditCloseOnMiss);
        formViews.minDurationView = rootView.findViewById(R.id.EditMinDuration);
        formViews.maxDurationView = rootView.findViewById(R.id.EditMaxDuration);
        formViews.cooldownView = rootView.findViewById(R.id.EditCooldown);
        formViews.adaptiveView = rootView.findViewById(R.id.EditAdaptive);

        updateDeadlineDisplay();
        formViews.deadlineView.setOnClickListener(v -> showDatePicker());
        formViews.deadlineInputLayout.setEndIconOnClickListener(v -> showDatePicker());
        clearDeadline.setOnClickListener(v -> {
            presenter.setEditableDeadline(null);
            updateDeadlineDisplay();
        });

        formViews.closeOnMissView.setChecked(editState.closeOnMiss);
        formViews.minDurationView.setText(String.valueOf(editState.minDuration));
        formViews.maxDurationView.setText(String.valueOf(editState.maxDuration));
        formViews.cooldownView.setText(String.valueOf(editState.cooldown));
        formViews.adaptiveView.setChecked(editState.adaptive);
    }

    public void bindRepetition(Runnable onRepetitionChanged) {
        formViews.toggleRepetition = rootView.findViewById(R.id.ToggleRepetition);
        formViews.repetitionContainer = rootView.findViewById(R.id.RepetitionContainer);
        formViews.repsView = rootView.findViewById(R.id.EditReps);
        formViews.perPeriodView = rootView.findViewById(R.id.EditPerPeriod);
        formViews.periodUnitView = rootView.findViewById(R.id.EditPeriodUnit);

        boolean hasRepetition = editState.reps > 0;
        formViews.toggleRepetition.setChecked(hasRepetition);
        formViews.repetitionContainer.setVisibility(hasRepetition ? View.VISIBLE : View.GONE);

        formViews.repsView.setText(String.valueOf(editState.reps > 0 ? editState.reps : 1));
        formViews.perPeriodView.setText(String.valueOf(editState.perPeriod > 0 ? editState.perPeriod : 1));

        ArrayAdapter<Period> periodAdapter = new ArrayAdapter<>(
            fragment.requireContext(),
            android.R.layout.simple_spinner_item,
            Period.values()
        );
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        formViews.periodUnitView.setAdapter(periodAdapter);
        formViews.periodUnitView.setSelection((editState.periodUnit != null ? editState.periodUnit : Period.DAY).ordinal());

        presenter.initializeRepetitionState(
            formViews.toggleRepetition.isChecked(),
            formViews.repsView.getText().toString(),
            formViews.perPeriodView.getText().toString(),
            (Period) formViews.periodUnitView.getSelectedItem()
        );

        formViews.toggleRepetition.setOnCheckedChangeListener((btn, checked) -> {
            formViews.repetitionContainer.setVisibility(checked ? View.VISIBLE : View.GONE);
            onRepetitionChanged.run();
        });

        TextWatcher repWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                onRepetitionChanged.run();
            }
        };
        formViews.repsView.addTextChangedListener(repWatcher);
        formViews.perPeriodView.addTextChangedListener(repWatcher);

        formViews.periodUnitView.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onRepetitionChanged.run();
            }
        });
    }

    public void bindProgress() {
        formViews.toggleProgress = rootView.findViewById(R.id.ToggleProgress);
        formViews.progressContainer = rootView.findViewById(R.id.ProgressContainer);
        formViews.unitView = rootView.findViewById(R.id.EditUnit);
        formViews.targetView = rootView.findViewById(R.id.EditTarget);
        formViews.currentView = rootView.findViewById(R.id.EditCurrent);
        formViews.resetPerRepView = rootView.findViewById(R.id.EditResetPerRep);
        formViews.minPerRepView = rootView.findViewById(R.id.EditMinPerRep);
        formViews.maxPerRepView = rootView.findViewById(R.id.EditMaxPerRep);

        boolean hasProgress = editState.target > 0;
        formViews.toggleProgress.setChecked(hasProgress);
        formViews.progressContainer.setVisibility(hasProgress ? View.VISIBLE : View.GONE);

        formViews.unitView.setText(editState.unit != null ? editState.unit : "");
        formViews.targetView.setText(String.valueOf(editState.target));
        formViews.currentView.setText(String.valueOf(editState.current));
        formViews.resetPerRepView.setChecked(editState.resetPerRep);
        formViews.minPerRepView.setText(String.valueOf(editState.minPerRep));
        formViews.maxPerRepView.setText(String.valueOf(editState.maxPerRep));

        formViews.toggleProgress.setOnCheckedChangeListener((btn, checked) ->
            formViews.progressContainer.setVisibility(checked ? View.VISIBLE : View.GONE));
    }

    private void updateDeadlineDisplay() {
        if (presenter.getEditableDeadline() != null) {
            String deadlineText = presenter.getEditableDeadline().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            formViews.deadlineView.setText(deadlineText);
            formViews.deadlineView.setContentDescription("Frist auswählen. Aktuell: " + deadlineText);
        } else {
            formViews.deadlineView.setText("Keine Frist");
            formViews.deadlineView.setContentDescription("Frist auswählen. Aktuell: Keine Frist");
        }
    }

    private void showDatePicker() {
        LocalDate current = presenter.getEditableDeadline() != null ? presenter.getEditableDeadline() : LocalDate.now();
        new DatePickerDialog(fragment.requireContext(), (picker, year, month, day) -> {
            presenter.setEditableDeadline(LocalDate.of(year, month + 1, day));
            updateDeadlineDisplay();
        }, current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth()).show();
    }

    private static abstract class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    private static abstract class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
        @Override public void onNothingSelected(AdapterView<?> parent) {}
    }
}
