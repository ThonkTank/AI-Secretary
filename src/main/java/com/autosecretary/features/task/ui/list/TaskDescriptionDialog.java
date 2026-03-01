package com.autosecretary.features.task.ui.list;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.R;

/**
 * Dialog-based popup displaying the selected task title and description.
 */
public class TaskDescriptionDialog extends DialogFragment {
    public static final String TAG = "task_description_dialog";

    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_DESCRIPTION = "arg_description";

    public static TaskDescriptionDialog newInstance(String title, String description) {
        TaskDescriptionDialog fragment = new TaskDescriptionDialog();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_DESCRIPTION, description);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        String title = args != null ? args.getString(ARG_TITLE) : "";
        String description = args != null ? args.getString(ARG_DESCRIPTION) : null;

        String resolvedDescription = TextUtils.isEmpty(description)
                ? getString(R.string.task_description_fallback)
                : description;

        return new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(resolvedDescription)
                .setPositiveButton(R.string.action_ok, null)
                .create();
    }
}
