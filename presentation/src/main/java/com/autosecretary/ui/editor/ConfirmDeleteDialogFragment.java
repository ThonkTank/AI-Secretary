package com.autosecretary.ui.editor;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.presentation.R;
import com.autosecretary.ui.FeatureViewModels;
import com.autosecretary.ui.MainViewModel;

/** Confirmation stays attached to the editor's child FragmentManager. */
public final class ConfirmDeleteDialogFragment extends DialogFragment {
    public static final String TAG = "confirm-editor-delete";

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle state) {
        MainViewModel viewModel = FeatureViewModels.main(this);
        ObligationEditorState editor = viewModel.state().getValue() == null
                ? null : viewModel.state().getValue().editor();
        String title = editor == null ? "diesen Eintrag" : editor.titleInput();
        return new AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.confirm_delete, title))
                .setPositiveButton(R.string.delete,
                        (dialog, which) -> viewModel.deleteEditor())
                .setNegativeButton(R.string.cancel, null)
                .create();
    }
}
