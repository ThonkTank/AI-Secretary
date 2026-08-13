package com.autosecretary.ui.ai;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.R;
import com.autosecretary.application.ai.BulkChange;
import com.autosecretary.application.ai.BulkChangeProposal;
import com.autosecretary.ui.MainViewModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Preview selection is keyed by typed change entries, never label prefixes. */
public final class AiProposalDialogFragment extends DialogFragment {
    public static final String TAG = "ai-proposal";
    private static final String SELECTED = "selected";

    public interface Host {
        MainViewModel mainViewModel();
        AiViewModel aiViewModel();
    }

    @NonNull
    @Override public Dialog onCreateDialog(Bundle state) {
        Host host = (Host) requireActivity();
        BulkChangeProposal proposal = requireProposal(host.aiViewModel());
        String[] labels = proposal.changes().stream().map(BulkChange::previewLabel)
                .toArray(String[]::new);
        boolean[] selected = state == null ? null : state.getBooleanArray(SELECTED);
        if (selected == null || selected.length != labels.length) {
            selected = new boolean[labels.length];
            Arrays.fill(selected, true);
        }
        boolean[] selection = selected;
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.confirm_changes)
                .setMessage(proposal.summary())
                .setMultiChoiceItems(labels, selection,
                        (ignored, which, checked) -> selection[which] = checked)
                .setPositiveButton(R.string.apply_changes, null)
                .setNegativeButton(R.string.cancel,
                        (ignored, which) -> host.aiViewModel().consumeProposal())
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!proposal.changes().isEmpty());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                List<BulkChange> changes = new ArrayList<>();
                for (int index = 0; index < selection.length; index++) {
                    if (selection[index]) changes.add(proposal.changes().get(index));
                }
                if (changes.isEmpty()) {
                    dialog.setMessage(proposal.summary() + "\n\nBitte mindestens eine Änderung auswählen.");
                    return;
                }
                host.mainViewModel().applyChangeSet(changes,
                        "KI-Änderungen rückgängig machen");
                host.aiViewModel().consumeProposal();
                dialog.dismiss();
            });
        });
        return dialog;
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null && dialog.getListView() != null) {
            boolean[] selected = new boolean[dialog.getListView().getCount()];
            for (int index = 0; index < selected.length; index++) {
                selected[index] = dialog.getListView().isItemChecked(index);
            }
            outState.putBooleanArray(SELECTED, selected);
        }
        super.onSaveInstanceState(outState);
    }

    @Override public void onCancel(@NonNull android.content.DialogInterface dialog) {
        ((Host) requireActivity()).aiViewModel().consumeProposal();
        super.onCancel(dialog);
    }

    private static BulkChangeProposal requireProposal(AiViewModel viewModel) {
        if (viewModel.state().getValue() == null || viewModel.state().getValue().proposal() == null) {
            throw new IllegalStateException("KI-Vorschlag fehlt");
        }
        return viewModel.state().getValue().proposal();
    }
}
