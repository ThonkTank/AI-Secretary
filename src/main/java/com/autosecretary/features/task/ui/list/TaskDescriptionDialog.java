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
 * <p>An optional "Bearbeiten" action opens the task editor, and an optional undo action reverts
 * the last check-off phase. Both callbacks are set via {@link #setOnEdit(Runnable)} /
 * {@link #setOnUndo(String, Runnable)} before {@code show()}; they are not restored after process
 * death, so a button is simply omitted when its callback is absent.
 */
public class TaskDescriptionDialog extends DialogFragment {
    public static final String TAG = "task_description_dialog";

    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_DESCRIPTION = "arg_description";

    private Runnable onEdit;
    private String undoLabel;
    private Runnable onUndo;

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

    /**
     * Sets the undo action shown as a labelled button (e.g. "Erledigung zurücknehmen" /
     * "Start zurücknehmen"), reverting the last check-off phase. Enables the button only when
     * both {@code label} and {@code onUndo} are present.
     */
    public void setOnUndo(String label, Runnable onUndo) {
        this.undoLabel = label;
        this.onUndo = onUndo;
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
        if (onUndo != null && !TextUtils.isEmpty(undoLabel)) {
            builder.setNegativeButton(undoLabel, (dialog, which) -> onUndo.run());
        }
        return builder.create();
    }
}
