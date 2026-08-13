package com.autosecretary.ui.editor;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.ui.MainViewModel;

public final class AddWorkItemDialogFragment extends DialogFragment {
    public static final String TAG = "add-work-item";
    public interface Host { MainViewModel mainViewModel(); }

    @NonNull
    @Override public Dialog onCreateDialog(Bundle state) {
        MainViewModel viewModel = ((Host) requireActivity()).mainViewModel();
        return new AlertDialog.Builder(requireContext())
                .setTitle("Was möchtest du anlegen?")
                .setItems(new String[]{"Aufgabe", "Routine"},
                        (dialog, which) -> viewModel.openEditor(which == 1, null))
                .create();
    }
}
