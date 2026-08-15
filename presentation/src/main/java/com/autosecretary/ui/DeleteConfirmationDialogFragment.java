package com.autosecretary.ui;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.util.ArrayList;
import java.util.List;

/** Lifecycle-safe delete confirmation that reports the decision as a Fragment result. */
public final class DeleteConfirmationDialogFragment extends DialogFragment {
    public static final String TAG = "confirm-work-item-delete";
    public static final String RESULT = "work-item-delete-confirmed";
    public static final String IDS = "ids";
    private static final String TITLE = "title";
    private static final String MESSAGE = "message";

    public static DeleteConfirmationDialogFragment create(
            String title, String message, List<String> ids) {
        DeleteConfirmationDialogFragment fragment = new DeleteConfirmationDialogFragment();
        Bundle arguments = new Bundle();
        arguments.putString(TITLE, title);
        arguments.putString(MESSAGE, message);
        arguments.putStringArrayList(IDS, new ArrayList<>(ids));
        fragment.setArguments(arguments);
        return fragment;
    }

    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        Bundle arguments = requireArguments();
        return new AlertDialog.Builder(requireContext())
                .setTitle(arguments.getString(TITLE, "Eintrag löschen"))
                .setMessage(arguments.getString(MESSAGE, "Wirklich löschen?"))
                .setPositiveButton("Löschen", (ignored, which) ->
                        getParentFragmentManager().setFragmentResult(RESULT, arguments))
                .setNegativeButton("behalten", null)
                .create();
    }
}
