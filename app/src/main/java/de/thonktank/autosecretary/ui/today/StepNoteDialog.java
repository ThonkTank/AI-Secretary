package de.thonktank.autosecretary.ui.today;

import android.app.AlertDialog;
import android.content.Context;
import de.thonktank.autosecretary.presentation.today.StepNoteDraft;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.presentation.today.TodayViewModel;

/** Small note editor; its draft and save result belong to the retained screen model. */
public final class StepNoteDialog {
    private final Context context;
    private final TodayViewModel model;
    private AlertDialog dialog;
    private EditText input;
    private TextView error;
    private String stepId;

    public StepNoteDialog(Context context, TodayViewModel model) {
        this.context = context; this.model = model;
    }

    public void bind(StepNoteDraft draft) {
        if (draft == null) { dismiss(); return; }
        if (dialog == null || !draft.id.equals(stepId)) {
            dismiss(); stepId = draft.id;
            int padding = Math.round(24 * context.getResources().getDisplayMetrics().density);
            LinearLayout body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(padding, padding / 2, padding, 0);
            input = new EditText(context);
            input.setId(R.id.step_note_input);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            input.setGravity(Gravity.TOP);
            input.setMinLines(3); input.setMaxLines(6);
            input.setHint(R.string.step_note_hint);
            input.setText(draft.text);
            input.setSelection(input.length());
            body.addView(input, new LinearLayout.LayoutParams(-1, -2));
            error = new TextView(context); error.setId(R.id.step_note_error);
            error.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
            body.addView(error, new LinearLayout.LayoutParams(-1, -2));
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    model.changeStepNote(s.toString());
                }
                @Override public void afterTextChanged(Editable value) { }
            });
            dialog = new AlertDialog.Builder(context)
                    .setTitle(R.string.action_edit_step_note)
                    .setView(body)
                    .setNegativeButton(android.R.string.cancel, (d, which) -> model.cancelStepNote())
                    .setPositiveButton(R.string.action_save, null)
                    .setOnCancelListener(d -> model.cancelStepNote()).create();
            dialog.setOnShowListener(d -> {
                AlertDialog shown = (AlertDialog) d;
                if (!shown.isShowing()) return;
                shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> model.saveStepNote());
            });
            // API 26 evaluates the initial IME state when the window gains focus.
            // Configure it before show(), rather than from the queued on-show callback.
            input.requestFocus();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.show();
        }
        boolean saving = draft.saving;
        input.setEnabled(!saving);
        dialog.setCancelable(!saving);
        dialog.setCanceledOnTouchOutside(!saving);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!saving);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!saving);
        String message = draft.error;
        error.setText(message);
        error.setVisibility(message.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    /** Host destruction must not cancel the retained draft. */
    public void dismiss() {
        if (dialog != null) dialog.dismiss();
        dialog = null; stepId = null;
    }
}
