package com.autosecretary.ui;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.presentation.R;

public final class ErrorDialogFragment extends DialogFragment {
    public static final String TAG = "error-message";
    private static final String MESSAGE = "message";

    public static ErrorDialogFragment create(String message) {
        ErrorDialogFragment fragment = new ErrorDialogFragment();
        Bundle arguments = new Bundle();
        arguments.putString(MESSAGE, message);
        fragment.setArguments(arguments);
        return fragment;
    }

    @NonNull
    @Override public Dialog onCreateDialog(Bundle state) {
        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.error)
                .setMessage(requireArguments().getString(MESSAGE, "Unbekannter Fehler"))
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }
}
