package com.autosecretary.ui.settings;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.presentation.R;
import com.autosecretary.presentation.databinding.DialogPlanningSettingsBinding;
import com.autosecretary.ui.FeatureViewModels;

/** Lifecycle-safe raw settings editor backed by its own SavedStateHandle. */
public final class PlanningSettingsDialogFragment extends DialogFragment {
    public static final String TAG = "planning-settings";

    private PlanningSettingsViewModel viewModel;
    private DialogPlanningSettingsBinding binding;
    private boolean rendering;
    private boolean saving;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle state) {
        viewModel = FeatureViewModels.planningSettings(this);
        binding = DialogPlanningSettingsBinding.inflate(requireActivity().getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Planungszeiten")
                .setView(binding.getRoot())
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel,
                        (ignored, which) -> viewModel.close())
                .create();
        installWatchers();
        render(requireState());
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    sync();
                    PlanningSettingsEditorState candidate = requireState();
                    saving = candidate.validated().valid();
                    viewModel.submit(candidate);
                }));
        viewModel.state().observe(this, value -> {
            if (value instanceof PlanningSettingsUiState.Closed) {
                if (saving) getParentFragmentManager().setFragmentResult(
                        PlanningSettingsResultContract.CHANGED, new Bundle());
                dismissAllowingStateLoss();
            } else if (value instanceof PlanningSettingsUiState.Failed failed) {
                saving = false;
                render(failed.editor());
            } else if (viewModel.editor() != null) {
                render(viewModel.editor());
            }
        });
        return dialog;
    }

    @Override
    public void onCancel(@NonNull android.content.DialogInterface dialog) {
        viewModel.close();
        super.onCancel(dialog);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void render(PlanningSettingsEditorState value) {
        if (binding == null) return;
        rendering = true;
        try {
            set(binding.DayStart, value.dayStart()); set(binding.DayEnd, value.dayEnd());
            set(binding.MorningStart, value.morningStart()); set(binding.MorningEnd, value.morningEnd());
            set(binding.MiddayStart, value.middayStart()); set(binding.MiddayEnd, value.middayEnd());
            set(binding.EveningStart, value.eveningStart()); set(binding.EveningEnd, value.eveningEnd());
            set(binding.TaskTransition, value.transition());
            set(binding.CalendarBefore, value.calendarBefore());
            set(binding.CalendarAfter, value.calendarAfter());
            set(binding.PlanningHorizon, value.horizon());
            binding.DayEnd.setError(value.errors().get("day"));
            binding.MorningEnd.setError(value.errors().get("morning"));
            binding.MiddayEnd.setError(value.errors().get("midday"));
            binding.EveningEnd.setError(value.errors().get("evening"));
            binding.TaskTransition.setError(value.errors().get("transition"));
            binding.CalendarBefore.setError(value.errors().get("before"));
            binding.CalendarAfter.setError(value.errors().get("after"));
            binding.PlanningHorizon.setError(value.errors().get("horizon"));
        } finally { rendering = false; }
    }

    private void installWatchers() {
        watch(binding.DayStart); watch(binding.DayEnd);
        watch(binding.MorningStart); watch(binding.MorningEnd);
        watch(binding.MiddayStart); watch(binding.MiddayEnd);
        watch(binding.EveningStart); watch(binding.EveningEnd);
        watch(binding.TaskTransition); watch(binding.CalendarBefore);
        watch(binding.CalendarAfter); watch(binding.PlanningHorizon);
    }

    private void watch(EditText field) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { sync(); }
            @Override public void afterTextChanged(Editable value) { }
        });
    }

    private void sync() {
        if (rendering || binding == null || viewModel == null
                || viewModel.editor() == null) return;
        viewModel.edit(requireState().edit(
                text(binding.DayStart), text(binding.DayEnd),
                text(binding.MorningStart), text(binding.MorningEnd),
                text(binding.MiddayStart), text(binding.MiddayEnd),
                text(binding.EveningStart), text(binding.EveningEnd),
                text(binding.TaskTransition), text(binding.CalendarBefore),
                text(binding.CalendarAfter), text(binding.PlanningHorizon)));
    }

    private PlanningSettingsEditorState requireState() {
        if (viewModel == null || viewModel.editor() == null) {
            throw new IllegalStateException("Planungseinstellungen fehlen");
        }
        return viewModel.editor();
    }

    private static void set(EditText field, String value) {
        if (!field.getText().toString().equals(value)) field.setText(value);
    }

    private static String text(EditText field) { return field.getText().toString().trim(); }
}
