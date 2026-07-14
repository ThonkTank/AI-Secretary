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
 *
 * <p>An optional "Bearbeiten" action opens the task editor. The callback is set via
 * {@link #setOnEdit(Runnable)} before {@code show()}; it is not restored after process
 * death, so the edit button is simply omitted when no callback is present.
 */
public class TaskDescriptionDialog extends DialogFragment {
    public static final String TAG = "task_description_dialog";

    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_DESCRIPTION = "arg_description";

    private Runnable onEdit;

    public static TaskDescriptionDialog newInstance(String title, String description) {
        TaskDescriptionDialog fragment = new TaskDescriptionDialog();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_DESCRIPTION, description);
        fragment.setArguments(args);
        return fragment;
    }

    /** Sets the callback invoked when the user taps "Bearbeiten"; enables the edit button. */
    public void setOnEdit(Runnable onEdit) {
        this.onEdit = onEdit;
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

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(resolvedDescription)
                .setPositiveButton(R.string.action_ok, null);
        if (onEdit != null) {
            builder.setNeutralButton(R.string.task_description_edit_action, (dialog, which) -> onEdit.run());
        }
        return builder.create();
    }
}
