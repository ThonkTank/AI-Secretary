package com.autosecretary.ui.ai;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.R;

public final class AiTermsDialogFragment extends DialogFragment {
    public static final String TAG = "ai-terms";
    public interface Host { AiViewModel aiViewModel(); }

    @NonNull
    @Override public Dialog onCreateDialog(Bundle state) {
        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.gemma_terms_title)
                .setMessage(R.string.gemma_terms_message)
                .setPositiveButton(R.string.accept, (dialog, which) ->
                        ((Host) requireActivity()).aiViewModel().acceptTermsAndInstall())
                .setNegativeButton(R.string.cancel, null)
                .create();
    }
}
