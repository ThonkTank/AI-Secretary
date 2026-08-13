package com.autosecretary.ui.editor;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.R;
import com.autosecretary.databinding.DialogObligationBinding;
import com.autosecretary.databinding.RowStepEditorBinding;
import com.autosecretary.ui.MainViewModel;

import java.util.ArrayList;
import java.util.List;

/** Lifecycle-safe editor; every raw input is mirrored to SavedStateHandle. */
public final class ObligationEditorDialogFragment extends DialogFragment {
    public static final String TAG = "obligation-editor";

    public interface Host { MainViewModel mainViewModel(); }

    private DialogObligationBinding binding;
    private MainViewModel viewModel;
    private final List<StepRow> rows = new ArrayList<>();
    private boolean rendering;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle state) {
        viewModel = ((Host) requireActivity()).mainViewModel();
        binding = DialogObligationBinding.inflate(requireActivity().getLayoutInflater());
        ObligationEditorState source = requireEditor();
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(source.routine() ? R.string.edit_routine : R.string.edit_task)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, (ignored, which) -> viewModel.closeEditor())
                .setNeutralButton(source.existingId() == null ? R.string.empty : R.string.delete, null)
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                sync();
                viewModel.submitEditor(requireEditor());
            });
            if (source.existingId() == null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);
            } else {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view ->
                        new ConfirmDeleteDialogFragment().show(
                                getChildFragmentManager(), ConfirmDeleteDialogFragment.TAG));
            }
        });

        binding.TaskFields.setVisibility(source.routine() ? View.GONE : View.VISIBLE);
        binding.RoutineFields.setVisibility(source.routine() ? View.VISIBLE : View.GONE);
        binding.AddStep.setOnClickListener(view -> {
            sync();
            viewModel.addEditorStep();
        });
        installInputListeners();
        render(source, true);
        viewModel.state().observe(this, value -> {
            ObligationEditorState editor = value.editor();
            if (editor == null) {
                dismissAllowingStateLoss();
                return;
            }
            List<String> ids = editor.steps().stream().map(StepEditorState::id)
                    .collect(java.util.stream.Collectors.toList());
            List<String> currentIds = rows.stream().map(row -> row.state().id())
                    .collect(java.util.stream.Collectors.toList());
            render(editor, !ids.equals(currentIds));
        });
        return dialog;
    }

    @Override
    public void onCancel(@NonNull android.content.DialogInterface dialog) {
        viewModel.closeEditor();
        super.onCancel(dialog);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        rows.clear();
        super.onDestroyView();
    }

    private void installInputListeners() {
        watch(binding.EditTitle);
        watch(binding.EditDuration);
        watch(binding.EditDeadline);
        watch(binding.EditCadence);
        watch(binding.EditNextDue);
        binding.EditFlexible.setOnCheckedChangeListener((button, checked) -> sync());
        binding.EditTimePreference.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sync();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { sync(); }
        });
    }

    private void render(ObligationEditorState state, boolean rebuildRows) {
        if (binding == null) return;
        rendering = true;
        try {
            if (rebuildRows || rows.isEmpty() && !state.steps().isEmpty()) {
                binding.StepList.removeAllViews();
                rows.clear();
                for (int index = 0; index < state.steps().size(); index++) {
                    StepEditorState step = state.steps().get(index);
                    RowStepEditorBinding row = RowStepEditorBinding.inflate(
                            getLayoutInflater(), binding.StepList, false);
                    row.StepTitle.setText(step.titleInput());
                    row.StepDays.setText(step.daysInput());
                    StepRow holder = new StepRow(step, row);
                    rows.add(holder);
                    row.StepEarlier.setEnabled(index > 0);
                    row.StepLater.setEnabled(index < state.steps().size() - 1);
                    row.StepEarlier.setOnClickListener(view -> changeStep(step.id(), -1));
                    row.StepLater.setOnClickListener(view -> changeStep(step.id(), 1));
                    row.StepDelete.setOnClickListener(view -> deleteStep(step.id()));
                    watch(row.StepTitle);
                    watch(row.StepDays);
                    binding.StepList.addView(row.getRoot());
                }
            }
            if (binding.EditTitle.getText().toString().equals(state.titleInput()) == false) {
                binding.EditTitle.setText(state.titleInput());
            }
            if (!binding.EditDuration.getText().toString().equals(state.durationInput())) {
                binding.EditDuration.setText(state.durationInput());
            }
            if (!binding.EditDeadline.getText().toString().equals(state.deadlineInput())) {
                binding.EditDeadline.setText(state.deadlineInput());
            }
            if (!binding.EditCadence.getText().toString().equals(state.cadenceInput())) {
                binding.EditCadence.setText(state.cadenceInput());
            }
            if (!binding.EditNextDue.getText().toString().equals(state.nextDueInput())) {
                binding.EditNextDue.setText(state.nextDueInput());
            }
            binding.EditTimePreference.setSelection(selection(state.timePreferenceInput()));
            binding.EditFlexible.setChecked(state.flexible());
            binding.EditTitle.setError(state.errors().get("title"));
            binding.EditDuration.setError(state.errors().get("duration"));
            binding.EditDeadline.setError(state.errors().get("deadline"));
            binding.EditCadence.setError(state.errors().get("cadence"));
            binding.EditNextDue.setError(state.errors().get("nextDue"));
            for (int index = 0; index < Math.min(rows.size(), state.steps().size()); index++) {
                StepEditorState step = state.steps().get(index);
                rows.get(index).binding().StepTitle.setError(step.titleError());
                rows.get(index).binding().StepDays.setError(step.daysError());
            }
        } finally {
            rendering = false;
        }
    }

    private void sync() {
        if (rendering || binding == null || viewModel == null
                || viewModel.state().getValue() == null
                || viewModel.state().getValue().editor() == null) return;
        ObligationEditorState source = viewModel.state().getValue().editor();
        List<StepEditorState> stepValues = rows.stream().map(row -> row.state().edit(
                row.binding().StepTitle.getText().toString(),
                row.binding().StepDays.getText().toString()))
                .collect(java.util.stream.Collectors.toList());
        viewModel.editEditor(source.edit(
                binding.EditTitle.getText().toString(),
                binding.EditDuration.getText().toString(),
                binding.EditDeadline.getText().toString(),
                preference(binding.EditTimePreference.getSelectedItemPosition()),
                binding.EditFlexible.isChecked(),
                binding.EditCadence.getText().toString(),
                binding.EditNextDue.getText().toString(),
                stepValues));
    }

    private void watch(EditText field) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { sync(); }
            @Override public void afterTextChanged(Editable value) { }
        });
    }

    private void changeStep(String id, int delta) {
        sync();
        viewModel.moveEditorStep(id, delta);
    }

    private void deleteStep(String id) {
        sync();
        viewModel.removeEditorStep(id);
    }

    private ObligationEditorState requireEditor() {
        if (viewModel == null || viewModel.state().getValue() == null
                || viewModel.state().getValue().editor() == null) {
            throw new IllegalStateException("Editorzustand fehlt");
        }
        return viewModel.state().getValue().editor();
    }

    private static int selection(String value) {
        return switch (value == null ? "" : value) {
            case "MORNING" -> 1;
            case "MIDDAY" -> 2;
            case "EVENING" -> 3;
            default -> 0;
        };
    }

    private static String preference(int selection) {
        return switch (selection) {
            case 1 -> "MORNING";
            case 2 -> "MIDDAY";
            case 3 -> "EVENING";
            default -> "";
        };
    }

    private record StepRow(StepEditorState state, RowStepEditorBinding binding) { }
}
