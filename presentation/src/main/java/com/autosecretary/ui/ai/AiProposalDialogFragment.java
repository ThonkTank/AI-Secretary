package com.autosecretary.ui.ai;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.presentation.R;
import com.autosecretary.application.ai.BulkChange;
import com.autosecretary.application.ai.BulkChangeProposal;
import com.autosecretary.presentation.databinding.DialogAiProposalBinding;
import com.autosecretary.presentation.databinding.RowAiChangeBinding;
import com.autosecretary.ui.FeatureViewModels;
import com.autosecretary.ui.MainViewModel;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Typed proposal rows expose symbol, word, color and optional per-change selection. */
public final class AiProposalDialogFragment extends DialogFragment {
    public static final String TAG = "ai-proposal";
    private static final String SELECTED = "selected";
    private static final String REVIEWING = "reviewing";

    private DialogAiProposalBinding binding;
    private boolean[] selected;
    private boolean reviewing;
    private final List<RowAiChangeBinding> rows = new ArrayList<>();

    @NonNull
    @Override public Dialog onCreateDialog(Bundle state) {
        MainViewModel mainViewModel = FeatureViewModels.main(this);
        AiViewModel aiViewModel = FeatureViewModels.ai(this);
        BulkChangeProposal proposal = requireProposal(aiViewModel);
        binding = DialogAiProposalBinding.inflate(requireActivity().getLayoutInflater());
        selected = state == null ? null : state.getBooleanArray(SELECTED);
        if (selected == null || selected.length != proposal.changes().size()) {
            selected = new boolean[proposal.changes().size()];
            Arrays.fill(selected, true);
        }
        reviewing = state != null && state.getBoolean(REVIEWING);
        Dialog dialog = new Dialog(requireContext(), R.style.WaldEditorDialog);
        dialog.setContentView(binding.getRoot());
        AiUiState aiState = aiViewModel.state().getValue();
        binding.ProposalWish.setText(aiState == null ? "" : aiState.instruction());
        binding.ProposalSummary.setText(proposal.summary());
        for (int index = 0; index < proposal.changes().size(); index++) {
            addRow(proposal.changes().get(index), index);
        }
        renderSelection();
        binding.ProposalReview.setOnClickListener(view -> {
            reviewing = true;
            renderSelection();
        });
        binding.ProposalBack.setOnClickListener(view -> discard(aiViewModel));
        binding.ProposalDiscard.setOnClickListener(view -> discard(aiViewModel));
        binding.ProposalApply.setOnClickListener(
                view -> apply(mainViewModel, aiViewModel, proposal));
        return dialog;
    }

    @Override public void onStart() {
        super.onStart();
        Window window = requireDialog().getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private void addRow(BulkChange change, int index) {
        RowAiChangeBinding row = RowAiChangeBinding.inflate(
                getLayoutInflater(), binding.ProposalChanges, false);
        int color = switch (change.type()) {
            case ADD -> requireContext().getColor(R.color.forest);
            case UPDATE -> requireContext().getColor(R.color.calendar_ink);
            case DELETE -> requireContext().getColor(R.color.danger);
        };
        row.ChangeStripe.setBackgroundColor(color);
        row.ChangeSymbol.setText(switch (change.type()) {
            case ADD -> "＋";
            case UPDATE -> "≠";
            case DELETE -> "−";
        });
        row.ChangeKind.setText(switch (change.type()) {
            case ADD -> "neu";
            case UPDATE -> "ändern";
            case DELETE -> "löschen";
        });
        row.ChangeSymbol.setTextColor(color);
        row.ChangeKind.setTextColor(color);
        row.ChangeTitle.setText(change.upsert() == null
                ? change.previewLabel() : change.upsert().title());
        row.ChangeDetail.setText(change.previewLabel());
        row.ChangeSelected.setOnCheckedChangeListener((button, checked) -> {
            selected[index] = checked;
            row.getRoot().setAlpha(checked ? 1f : .45f);
            updateApplyLabel();
        });
        row.getRoot().setRotation(index % 2 == 0 ? 0.7f : -0.7f);
        rows.add(row);
        binding.ProposalChanges.addView(row.getRoot());
    }

    private void renderSelection() {
        for (int index = 0; index < rows.size(); index++) {
            RowAiChangeBinding row = rows.get(index);
            row.ChangeSelected.setVisibility(reviewing ? View.VISIBLE : View.GONE);
            row.ChangeSelected.setChecked(selected[index]);
            row.getRoot().setAlpha(selected[index] ? 1f : .45f);
        }
        binding.ProposalReview.setVisibility(reviewing ? View.GONE : View.VISIBLE);
        updateApplyLabel();
    }

    private void updateApplyLabel() {
        int count = 0;
        for (boolean value : selected) if (value) count++;
        binding.ProposalApply.setText(reviewing ? count + " übernehmen" : "Übernehmen");
        binding.ProposalNothing.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    private void apply(MainViewModel mainViewModel, AiViewModel aiViewModel,
            BulkChangeProposal proposal) {
        List<BulkChange> changes = new ArrayList<>();
        for (int index = 0; index < selected.length; index++) {
            if (selected[index]) changes.add(proposal.changes().get(index));
        }
        if (changes.isEmpty()) {
            binding.ProposalNothing.setVisibility(View.VISIBLE);
            return;
        }
        mainViewModel.applyChangeSet(changes, "KI-Änderungen zurücknehmen");
        aiViewModel.consumeProposal();
        Snackbar.make(requireActivity().findViewById(R.id.Root),
                changes.size() == 1 ? "Eine Änderung übernommen."
                        : changes.size() + " Änderungen übernommen.", Snackbar.LENGTH_LONG)
                .setAction("zurücknehmen", view -> mainViewModel.undo()).show();
        dismiss();
    }

    private void discard(AiViewModel aiViewModel) {
        aiViewModel.consumeProposal();
        Snackbar.make(requireActivity().findViewById(R.id.Root),
                "Nichts wurde geändert.", Snackbar.LENGTH_SHORT).show();
        dismiss();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBooleanArray(SELECTED, selected);
        outState.putBoolean(REVIEWING, reviewing);
        super.onSaveInstanceState(outState);
    }

    @Override public void onCancel(@NonNull android.content.DialogInterface dialog) {
        FeatureViewModels.ai(this).consumeProposal();
    }

    @Override public void onDestroyView() {
        binding = null;
        rows.clear();
        super.onDestroyView();
    }

    private static BulkChangeProposal requireProposal(AiViewModel viewModel) {
        if (viewModel.state().getValue() == null || viewModel.state().getValue().proposal() == null) {
            throw new IllegalStateException("KI-Vorschlag fehlt");
        }
        return viewModel.state().getValue().proposal();
    }
}
