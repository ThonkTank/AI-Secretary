package com.autosecretary.ui.editor;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.presentation.R;
import com.autosecretary.presentation.databinding.DialogObligationBinding;
import com.autosecretary.presentation.databinding.RowStepEditorBinding;
import com.autosecretary.ui.FeatureViewModels;
import com.autosecretary.ui.LeafActionMenu;
import com.google.android.material.chip.Chip;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Full-screen Waldmorgen editor backed by the retained immutable editor state. */
public final class ObligationEditorDialogFragment extends DialogFragment {
    public static final String TAG = "obligation-editor";
    private static final String EDITOR_PAGE = "editor_page";
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd.MM.");

    private DialogObligationBinding binding;
    private EditorViewModel viewModel;
    private ObligationEditorState initial;
    private ObligationEditorState previousStepOrder;
    private final List<StepRow> rows = new ArrayList<>();
    private boolean rendering;
    private int page;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle state) {
        viewModel = FeatureViewModels.editor(this);
        binding = DialogObligationBinding.inflate(requireActivity().getLayoutInflater());
        initial = requireEditor();
        page = state == null ? 0 : Math.max(0, Math.min(2, state.getInt(EDITOR_PAGE, 0)));
        Dialog dialog = new Dialog(requireContext(), R.style.WaldEditorDialog);
        dialog.setContentView(binding.getRoot());
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnKeyListener((ignored, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                if (isWizard() && page > 0) {
                    page--;
                    render(requireEditor(), false);
                } else {
                    requestClose();
                }
                return true;
            }
            return false;
        });

        installActions();
        installInputListeners();
        render(initial, true);
        viewModel.state().observe(this, value -> {
            ObligationEditorState editor = viewModel.editor();
            if (value instanceof EditorUiState.Closed) {
                dismissAllowingStateLoss();
                return;
            }
            if (editor == null) return;
            List<String> ids = editor.steps().stream().map(StepEditorState::id)
                    .collect(java.util.stream.Collectors.toList());
            List<String> currentIds = rows.stream().map(row -> row.state().id())
                    .collect(java.util.stream.Collectors.toList());
            render(editor, !ids.equals(currentIds));
        });
        viewModel.effects().observe(this, effect -> {
            if (effect == null) return;
            viewModel.consumeEffect(effect.id());
            if (effect instanceof EditorUiEffect.Saved
                    || effect instanceof EditorUiEffect.Deleted) {
                getParentFragmentManager().setFragmentResult(
                        EditorResultContract.CHANGED, new Bundle());
            } else if (effect instanceof EditorUiEffect.Error error) {
                new AlertDialog.Builder(requireContext()).setMessage(error.message())
                        .setPositiveButton(android.R.string.ok, null).show();
            }
        });
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = requireDialog().getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    public void onCancel(@NonNull android.content.DialogInterface dialog) {
        requestClose();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        rows.clear();
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(EDITOR_PAGE, page);
        super.onSaveInstanceState(outState);
    }

    private void installActions() {
        binding.CancelEditor.setOnClickListener(view -> {
            if (isWizard() && page > 0) {
                page--;
                render(requireEditor(), false);
            } else {
                requestClose();
            }
        });
        binding.DiscardEditor.setOnClickListener(view -> requestClose());
        binding.SaveEditor.setOnClickListener(view -> advanceOrSubmit());
        binding.DeleteEditor.setVisibility(initial.existingId() == null ? View.GONE : View.VISIBLE);
        binding.DeleteEditor.setOnClickListener(view ->
                new ConfirmDeleteDialogFragment().show(
                        getChildFragmentManager(), ConfirmDeleteDialogFragment.TAG));
        binding.AddStep.setOnClickListener(view -> {
            sync();
            viewModel.addStep();
        });
        binding.UndoStepOrder.setOnClickListener(view -> {
            if (previousStepOrder == null) return;
            viewModel.edit(previousStepOrder);
            previousStepOrder = null;
            binding.StepUndoBar.setVisibility(View.GONE);
        });
        binding.NextDuePick.setOnClickListener(view -> showDatePicker(true));
    }

    private void installInputListeners() {
        watch(binding.EditTitle);
        watch(binding.EditDuration);
        watch(binding.EditCadence);
        watch(binding.EditNextDue);
        durationChip(binding.Duration15, "15");
        durationChip(binding.Duration30, "30");
        durationChip(binding.Duration45, "45");
        durationChip(binding.Duration60, "60");
        binding.DurationCustom.setOnClickListener(view -> {
            if (rendering) return;
            binding.EditDuration.setVisibility(View.VISIBLE);
            binding.EditDuration.requestFocus();
            sync();
        });
        binding.DeadlineNone.setOnClickListener(view -> setDeadline(""));
        binding.DeadlineToday.setOnClickListener(view -> setDeadline(viewModel.today().toString()));
        binding.DeadlineTomorrow.setOnClickListener(view ->
                setDeadline(viewModel.today().plusDays(1).toString()));
        binding.DeadlineDate.setOnClickListener(view -> {
            if (!rendering) showDatePicker(false);
        });
        cadenceChip(binding.CadenceDaily, "1");
        cadenceChip(binding.CadenceWeekly, "7");
        cadenceChip(binding.CadenceFortnightly, "14");
        binding.CadenceCustom.setOnClickListener(view -> {
            if (rendering) return;
            binding.EditCadence.setVisibility(View.VISIBLE);
            binding.EditCadence.requestFocus();
            sync();
        });
        binding.PreferenceChoices.setOnCheckedStateChangeListener((group, checkedIds) -> sync());
        binding.EditLearning.setOnCheckedChangeListener((button, checked) -> sync());
    }

    private void durationChip(Chip chip, String value) {
        chip.setOnClickListener(view -> {
            if (rendering) return;
            setText(binding.EditDuration, value);
            binding.EditDuration.setVisibility(View.GONE);
            sync();
        });
    }

    private void cadenceChip(Chip chip, String value) {
        chip.setOnClickListener(view -> {
            if (rendering) return;
            setText(binding.EditCadence, value);
            binding.EditCadence.setVisibility(View.GONE);
            sync();
        });
    }

    private void setDeadline(String value) {
        if (rendering) return;
        ObligationEditorState source = requireEditor();
        viewModel.edit(source.edit(
                binding.EditTitle.getText().toString(),
                binding.EditDuration.getText().toString(),
                value, source.timePreferenceInput(), source.flexible(),
                binding.EditCadence.getText().toString(),
                binding.EditNextDue.getText().toString(), currentSteps()));
    }

    private void showDatePicker(boolean nextDue) {
        LocalDate current = parseDate(nextDue
                ? binding.EditNextDue.getText().toString()
                : requireEditor().deadlineInput());
        if (current == null) current = viewModel.today();
        new DatePickerDialog(requireContext(), (view, year, month, day) -> {
            LocalDate selected = LocalDate.of(year, month + 1, day);
            if (nextDue) {
                setText(binding.EditNextDue, selected.toString());
                sync();
            } else {
                setDeadline(selected.toString());
            }
        }, current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth()).show();
    }

    private void render(ObligationEditorState state, boolean rebuildRows) {
        if (binding == null) return;
        rendering = true;
        try {
            boolean routine = state.routine();
            binding.EditorKind.setText(state.existingId() == null
                    ? routine ? "neue Routine" : "neue Aufgabe"
                    : routine ? "Routine bearbeiten" : "Aufgabe bearbeiten");
            binding.WhatMarker.setText(routine ? "was wiederholt sich" : "was steht an");
            binding.ProgressTips.setVisibility(routine ? View.VISIBLE : View.GONE);
            binding.TaskFields.setVisibility(routine ? View.GONE : View.VISIBLE);
            binding.RoutineFields.setVisibility(routine ? View.VISIBLE : View.GONE);
            binding.StepSection.setVisibility(routine ? View.VISIBLE : View.GONE);
            binding.EditorInference.setText(routine
                    ? "Die Dauer gilt für den ganzen Block. Abgehakt wird später einzeln."
                    : inference(state));

            setText(binding.EditTitle, state.titleInput());
            setText(binding.EditDuration, state.durationInput());
            setText(binding.EditCadence, state.cadenceInput());
            setText(binding.EditNextDue, state.nextDueInput());
            selectDuration(state.durationInput());
            selectDeadline(state.deadlineInput());
            selectCadence(state.cadenceInput());
            selectPreference(state.timePreferenceInput());
            binding.EditLearning.setChecked(state.flexible());
            showError(binding.TitleError, state.errors().get("title"));
            showError(binding.DurationError, state.errors().get("duration"));
            showError(binding.DeadlineError, state.errors().get("deadline"));
            showError(binding.CadenceError, state.errors().get("cadence"));
            showError(binding.NextDueError, state.errors().get("nextDue"));
            binding.SaveEditor.setEnabled(!state.titleInput().trim().isEmpty());

            if (rebuildRows || rows.isEmpty() && !state.steps().isEmpty()) {
                rebuildStepRows(state);
            } else {
                for (int index = 0; index < Math.min(rows.size(), state.steps().size()); index++) {
                    rows.get(index).update(state.steps().get(index), index);
                }
            }
            renderPage(state);
        } finally {
            rendering = false;
        }
    }

    private void rebuildStepRows(ObligationEditorState state) {
        binding.StepList.removeAllViews();
        rows.clear();
        for (int index = 0; index < state.steps().size(); index++) {
            StepEditorState step = state.steps().get(index);
            RowStepEditorBinding row = RowStepEditorBinding.inflate(
                    getLayoutInflater(), binding.StepList, false);
            StepRow holder = new StepRow(step, row);
            rows.add(holder);
            holder.update(step, index);
            row.StepSummary.setRotation(index % 2 == 0 ? 0.8f : -0.8f);
            row.StepSummary.setBackgroundResource(index % 2 == 0
                    ? R.drawable.bg_leaf_middle : R.drawable.bg_leaf_middle_mirror);
            watch(row.StepTitle);
            for (Chip day : dayChips(row)) {
                day.setOnCheckedChangeListener((button, checked) -> {
                    if (rendering) return;
                    row.StepDays.setText(encodedDays(row));
                    sync();
                });
            }
            row.StepMenu.setOnClickListener(view -> showStepMenu(holder));
            row.StepSummary.setOnClickListener(view -> openStep(holder));
            row.StepReorder.setOnClickListener(view -> showReorderMenu(holder));
            row.StepReorder.setOnLongClickListener(view -> {
                showEdgeMenu(holder);
                return true;
            });
            row.StepApply.setOnClickListener(view -> {
                row.StepDays.setText(encodedDays(row));
                sync();
                row.StepEditorPanel.setVisibility(View.GONE);
            });
            row.StepCancel.setOnClickListener(view -> {
                holder.update(holder.state(), rows.indexOf(holder));
                row.StepEditorPanel.setVisibility(View.GONE);
            });
            row.StepDelete.setOnClickListener(view -> deleteStep(holder.state().id()));
            binding.StepList.addView(row.getRoot());
            if (step.titleInput().isBlank()) openStep(holder);
        }
    }

    private void openStep(StepRow holder) {
        holder.binding().StepEditorPanel.setVisibility(View.VISIBLE);
        holder.binding().StepTitle.requestFocus();
    }

    private void showStepMenu(StepRow holder) {
        LeafActionMenu.show(holder.binding().StepMenu, List.of(
                new LeafActionMenu.Action("Bearbeiten", false, () -> openStep(holder)),
                new LeafActionMenu.Action("Schritt löschen", true,
                        () -> deleteStep(holder.state().id()))));
    }

    private void showReorderMenu(StepRow holder) {
        int index = rows.indexOf(holder);
        List<LeafActionMenu.Action> actions = new ArrayList<>();
        if (index > 0) actions.add(new LeafActionMenu.Action(
                "eine Stelle nach oben", false,
                () -> moveStep(holder.state().id(), -1)));
        if (index < rows.size() - 1) actions.add(new LeafActionMenu.Action(
                "eine Stelle nach unten", false,
                () -> moveStep(holder.state().id(), 1)));
        if (!actions.isEmpty()) LeafActionMenu.show(holder.binding().StepReorder, actions);
    }

    private void showEdgeMenu(StepRow holder) {
        int index = rows.indexOf(holder);
        List<LeafActionMenu.Action> actions = new ArrayList<>();
        if (index > 0) actions.add(new LeafActionMenu.Action(
                "an den Anfang", false,
                () -> moveStep(holder.state().id(), -rows.size())));
        if (index < rows.size() - 1) actions.add(new LeafActionMenu.Action(
                "ans Ende", false,
                () -> moveStep(holder.state().id(), rows.size())));
        if (!actions.isEmpty()) LeafActionMenu.show(holder.binding().StepReorder, actions);
    }

    private void moveStep(String id, int delta) {
        sync();
        previousStepOrder = requireEditor();
        viewModel.moveStep(id, delta);
        binding.StepUndoBar.setVisibility(View.VISIBLE);
    }

    private void deleteStep(String id) {
        sync();
        viewModel.removeStep(id);
    }

    private void submit() {
        sync();
        viewModel.submit(requireEditor());
    }

    private void advanceOrSubmit() {
        if (!isWizard() || page == 2) {
            submit();
            return;
        }
        sync();
        if (page == 0 && !validWhatPage()) return;
        if (page == 1 && !validWhenPage()) return;
        page++;
        render(requireEditor(), false);
    }

    private boolean validWhatPage() {
        String title = binding.EditTitle.getText().toString().trim();
        int duration;
        try { duration = Integer.parseInt(binding.EditDuration.getText().toString().trim()); }
        catch (RuntimeException error) { duration = 0; }
        showError(binding.TitleError, title.isEmpty()
                ? "Ohne Titel lässt sich die Routine nicht speichern." : null);
        showError(binding.DurationError, duration < 1
                ? "Die Dauer braucht mindestens eine Minute." : null);
        return !title.isEmpty() && duration >= 1;
    }

    private boolean validWhenPage() {
        int cadence;
        try { cadence = Integer.parseInt(binding.EditCadence.getText().toString().trim()); }
        catch (RuntimeException error) { cadence = 0; }
        LocalDate due = parseDate(binding.EditNextDue.getText().toString());
        showError(binding.CadenceError, cadence < 1
                ? "Der Rhythmus braucht mindestens einen Tag." : null);
        showError(binding.NextDueError, due == null
                ? "Bitte eine nächste Fälligkeit wählen." : null);
        return cadence >= 1 && due != null;
    }

    private void renderPage(ObligationEditorState state) {
        if (!isWizard()) {
            binding.EditorLeaf.setVisibility(View.VISIBLE);
            binding.WhatFields.setVisibility(View.VISIBLE);
            binding.TaskFields.setVisibility(state.routine() ? View.GONE : View.VISIBLE);
            binding.RoutineFields.setVisibility(state.routine() ? View.VISIBLE : View.GONE);
            binding.PlanningFields.setVisibility(View.VISIBLE);
            binding.StepSection.setVisibility(state.routine() ? View.VISIBLE : View.GONE);
            binding.EditorInference.setVisibility(View.VISIBLE);
            binding.SaveEditor.setText("Speichern");
            binding.CancelEditor.setText("abbrechen");
            return;
        }
        binding.EditorKind.setText("Routine · " + (page + 1) + " von 3");
        binding.EditorLeaf.setVisibility(page == 2 ? View.GONE : View.VISIBLE);
        binding.WhatFields.setVisibility(page == 0 ? View.VISIBLE : View.GONE);
        binding.TaskFields.setVisibility(View.GONE);
        binding.RoutineFields.setVisibility(page == 1 ? View.VISIBLE : View.GONE);
        binding.PlanningFields.setVisibility(page == 1 ? View.VISIBLE : View.GONE);
        binding.StepSection.setVisibility(page == 2 ? View.VISIBLE : View.GONE);
        binding.EditorInference.setVisibility(page == 0 ? View.VISIBLE : View.GONE);
        binding.SaveEditor.setText(page == 2 ? "Speichern" : "Weiter");
        binding.CancelEditor.setText(page == 0 ? "abbrechen" : "zurück");
        binding.SaveEditor.setEnabled(page != 0 || !state.titleInput().trim().isEmpty());
        binding.ProgressTipOne.setBackgroundResource(R.drawable.bg_progress_tip_active);
        binding.ProgressTipTwo.setBackgroundResource(page >= 1
                ? R.drawable.bg_progress_tip_active : R.drawable.bg_progress_tip_open);
        binding.ProgressTipThree.setBackgroundResource(page >= 2
                ? R.drawable.bg_progress_tip_active : R.drawable.bg_progress_tip_open);
    }

    private boolean isWizard() {
        return initial.routine() && initial.existingId() == null;
    }

    private void requestClose() {
        sync();
        ObligationEditorState current = requireEditor();
        if (sameInputs(initial, current)) {
            viewModel.close();
            return;
        }
        String changed = current.routine()
                ? "Titel, Rhythmus oder Schritte gehen verloren."
                : "Titel, Dauer oder Deadline gehen verloren.";
        AlertDialog warning = new AlertDialog.Builder(requireContext())
                .setTitle("Änderungen verwerfen?")
                .setMessage(changed)
                .setPositiveButton("Speichern", (dialog, which) -> submit())
                .setNegativeButton("weiter bearbeiten", null)
                .setNeutralButton("Verwerfen", (dialog, which) -> viewModel.close())
                .create();
        warning.show();
        warning.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setTextColor(requireContext().getColor(R.color.danger));
    }

    private void sync() {
        if (rendering || binding == null || viewModel == null
                || viewModel.editor() == null) return;
        ObligationEditorState source = viewModel.editor();
        viewModel.edit(source.edit(
                binding.EditTitle.getText().toString(),
                binding.EditDuration.getText().toString(),
                source.routine() ? "" : source.deadlineInput(),
                selectedPreference(), binding.EditLearning.isChecked(),
                binding.EditCadence.getText().toString(),
                binding.EditNextDue.getText().toString(), currentSteps()));
    }

    private List<StepEditorState> currentSteps() {
        return rows.stream().map(row -> row.state().edit(
                row.binding().StepTitle.getText().toString(),
                encodedDays(row.binding())))
                .collect(java.util.stream.Collectors.toList());
    }

    private void watch(EditText field) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                sync();
            }
            @Override public void afterTextChanged(Editable value) { }
        });
    }

    private void selectDuration(String value) {
        int id = switch (value) {
            case "15" -> R.id.Duration15;
            case "30" -> R.id.Duration30;
            case "45" -> R.id.Duration45;
            case "60" -> R.id.Duration60;
            default -> R.id.DurationCustom;
        };
        binding.DurationChoices.check(id);
        binding.EditDuration.setVisibility(id == R.id.DurationCustom ? View.VISIBLE : View.GONE);
    }

    private void selectCadence(String value) {
        int id = switch (value) {
            case "1" -> R.id.CadenceDaily;
            case "7" -> R.id.CadenceWeekly;
            case "14" -> R.id.CadenceFortnightly;
            default -> R.id.CadenceCustom;
        };
        binding.CadenceChoices.check(id);
        binding.EditCadence.setVisibility(id == R.id.CadenceCustom ? View.VISIBLE : View.GONE);
    }

    private void selectPreference(String value) {
        int id = switch (value) {
            case "MORNING" -> R.id.PreferenceMorning;
            case "MIDDAY" -> R.id.PreferenceMidday;
            case "EVENING" -> R.id.PreferenceEvening;
            default -> R.id.PreferenceAny;
        };
        binding.PreferenceChoices.check(id);
    }

    private String selectedPreference() {
        int id = binding.PreferenceChoices.getCheckedChipId();
        if (id == R.id.PreferenceMorning) return "MORNING";
        if (id == R.id.PreferenceMidday) return "MIDDAY";
        if (id == R.id.PreferenceEvening) return "EVENING";
        return "";
    }

    private void selectDeadline(String value) {
        LocalDate date = parseDate(value);
        int id;
        if (date == null) id = R.id.DeadlineNone;
        else if (date.equals(viewModel.today())) id = R.id.DeadlineToday;
        else if (date.equals(viewModel.today().plusDays(1))) id = R.id.DeadlineTomorrow;
        else id = R.id.DeadlineDate;
        binding.DeadlineChoices.check(id);
        if (id == R.id.DeadlineDate) {
            String time = "18:00";
            if (value != null && value.trim().length() > 10) {
                try {
                    time = java.time.LocalDateTime.parse(value.trim(),
                            ObligationEditorState.INPUT_DATE_TIME)
                            .format(DateTimeFormatter.ofPattern("HH:mm"));
                } catch (RuntimeException ignored) { }
            }
            binding.DeadlineDate.setText(date.format(DISPLAY_DATE) + " · " + time);
        } else {
            binding.DeadlineDate.setText("Datum…");
        }
    }

    private String inference(ObligationEditorState state) {
        LocalDate date = parseDate(state.deadlineInput());
        if (date == null) return "Wird vorgeschlagen, sobald Platz ist.";
        if (date.equals(viewModel.today())) return "Wird für heute vorgeschlagen, sobald Platz ist.";
        if (date.equals(viewModel.today().plusDays(1))) return "Wird für morgen vorgeschlagen.";
        return "Wird vor der Deadline vorgeschlagen, sobald ein Fenster passt.";
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value.substring(0, 10)); }
        catch (RuntimeException ignored) { return null; }
    }

    private static void showError(TextView view, String error) {
        view.setText(error == null ? "" : error);
        view.setVisibility(error == null ? View.GONE : View.VISIBLE);
    }

    private static void setText(EditText field, String value) {
        if (!field.getText().toString().equals(value)) field.setText(value);
    }

    private static boolean sameInputs(ObligationEditorState left, ObligationEditorState right) {
        return left.titleInput().equals(right.titleInput())
                && left.durationInput().equals(right.durationInput())
                && left.deadlineInput().equals(right.deadlineInput())
                && left.timePreferenceInput().equals(right.timePreferenceInput())
                && left.flexible() == right.flexible()
                && left.cadenceInput().equals(right.cadenceInput())
                && left.nextDueInput().equals(right.nextDueInput())
                && left.steps().equals(right.steps());
    }

    private static List<Chip> dayChips(RowStepEditorBinding row) {
        return List.of(row.DayMo, row.DayDi, row.DayMi, row.DayDo,
                row.DayFr, row.DaySa, row.DaySo);
    }

    private static String encodedDays(RowStepEditorBinding row) {
        StringBuilder value = new StringBuilder();
        String[] labels = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
        List<Chip> chips = dayChips(row);
        for (int index = 0; index < chips.size(); index++) {
            if (!chips.get(index).isChecked()) continue;
            if (value.length() > 0) value.append(',');
            value.append(labels[index]);
        }
        return value.toString();
    }

    private ObligationEditorState requireEditor() {
        if (viewModel == null || viewModel.editor() == null) {
            throw new IllegalStateException("Editorzustand fehlt");
        }
        return viewModel.editor();
    }

    private static final class StepRow {
        private StepEditorState state;
        private final RowStepEditorBinding binding;

        StepRow(StepEditorState state, RowStepEditorBinding binding) {
            this.state = state;
            this.binding = binding;
        }

        StepEditorState state() { return state; }
        RowStepEditorBinding binding() { return binding; }

        void update(StepEditorState value, int index) {
            state = value;
            binding.StepNumber.setText(Integer.toString(index + 1));
            binding.StepTitleDisplay.setText(value.titleInput().isBlank()
                    ? "neuer Schritt" : value.titleInput());
            binding.StepDaysDisplay.setText(value.daysInput().replace(",", " · "));
            binding.StepDaysDisplay.setVisibility(
                    value.daysInput().isBlank() ? View.GONE : View.VISIBLE);
            setText(binding.StepTitle, value.titleInput());
            binding.StepDays.setText(value.daysInput());
            String normalized = "," + value.daysInput().replace(" ", "") + ",";
            String[] labels = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
            List<Chip> chips = dayChips(binding);
            for (int day = 0; day < labels.length; day++) {
                chips.get(day).setChecked(normalized.contains("," + labels[day] + ","));
            }
            showError(binding.StepTitleError, value.titleError());
            binding.StepTitle.setError(value.daysError());
        }
    }
}
