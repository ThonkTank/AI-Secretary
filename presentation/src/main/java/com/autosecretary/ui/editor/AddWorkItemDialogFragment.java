package com.autosecretary.ui.editor;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.ui.FeatureViewModels;

public final class AddWorkItemDialogFragment extends DialogFragment {
    public static final String TAG = "add-work-item";
    @NonNull
    @Override public Dialog onCreateDialog(Bundle state) {
        EditorViewModel viewModel = FeatureViewModels.editor(this);
        return new AlertDialog.Builder(requireContext())
                .setTitle("Was möchtest du anlegen?")
                .setItems(new String[]{"Aufgabe", "Routine"},
                        (dialog, which) -> {
                            viewModel.open(which == 1, null);
                            if (getParentFragmentManager().findFragmentByTag(
                                    ObligationEditorDialogFragment.TAG) == null) {
                                new ObligationEditorDialogFragment().show(
                                        getParentFragmentManager(),
                                        ObligationEditorDialogFragment.TAG);
                            }
                        })
                .create();
    }
}
