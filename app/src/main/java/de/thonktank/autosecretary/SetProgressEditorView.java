package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.TaskStepUiModel;
import de.thonktank.autosecretary.presentation.SetProgressUiModel;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Inline set editor. It renders state and reports state changes without owning drafts. */
public final class SetProgressEditorView extends LinearLayout {
    public interface Listener {
        void onStateChanged(SetProgressEditorState state);
        void onSave(String stepId, List<Integer> repetitions);
        void onFinish(String stepId);
        void onReopen(String stepId, List<Integer> repetitions);
    }

    private final UiStyle style;
    private final TextView progress;
    private final EditText repetitions;
    private final LinearLayout actionRow;
    private final TextView save;
    private final TextView toggleDone;
    private boolean binding;
    private TaskStepUiModel boundStep;
    private SetProgressEditorState boundState = SetProgressEditorState.closed();
    private Listener listener;

    public SetProgressEditorView(Context context) {
        super(context);
        style = new UiStyle(context);
        setId(R.id.set_progress_editor);
        setOrientation(VERTICAL);
        setPadding(getResources().getDimensionPixelSize(R.dimen.focus_step_text_start),
                0, 0, style.dp(8));
        progress = style.sans("", 14, 0, false);
        addView(progress, new LayoutParams(-1, -2));
        repetitions = new EditText(context);
        repetitions.setId(R.id.set_progress_input);
        repetitions.setSingleLine(true);
        repetitions.setTextSize(17);
        repetitions.setTypeface(style.sans);
        repetitions.setInputType(InputType.TYPE_CLASS_TEXT);
        repetitions.setMinHeight(style.dp(48));
        LayoutParams input = new LayoutParams(-1, style.dp(48));
        input.topMargin = style.dp(4);
        addView(repetitions, input);
        actionRow = new LinearLayout(context);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        save = new TextView(context);
        save.setId(R.id.set_progress_save);
        save.setGravity(Gravity.CENTER);
        save.setMinHeight(style.dp(48));
        save.setPadding(style.dp(16), 0, style.dp(16), 0);
        save.setTypeface(style.sansBold);
        save.setTextSize(17);
        save.setMinWidth(style.dp(48));
        AccessibilityRoles.button(save);
        actionRow.addView(save, new LayoutParams(-2, style.dp(48)));
        toggleDone = new TextView(context);
        toggleDone.setId(R.id.set_progress_toggle_done);
        toggleDone.setGravity(Gravity.CENTER);
        toggleDone.setMinHeight(style.dp(48));
        toggleDone.setTextSize(17);
        toggleDone.setTypeface(style.sans);
        toggleDone.setMinWidth(style.dp(48));
        AccessibilityRoles.button(toggleDone);
        LayoutParams toggle = new LayoutParams(-2, style.dp(48));
        toggle.leftMargin = style.dp(12);
        actionRow.addView(toggleDone, toggle);
        LayoutParams actionParams = new LayoutParams(-1, -2);
        actionParams.topMargin = style.dp(7);
        addView(actionRow, actionParams);
        setVisibility(GONE);
        repetitions.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                if (binding || boundStep == null || listener == null) return;
                boundState = boundState.withDraft(boundStep.id, value.toString());
                listener.onStateChanged(boundState);
            }
            @Override public void afterTextChanged(Editable value) { }
        });
    }

    public void bind(TaskStepUiModel step, DayPalette palette,
                     SetProgressEditorState state, Listener callbacks) {
        SetProgressUiModel setProgress = step.setProgress;
        if (setProgress == null)
            throw new IllegalArgumentException("Set editor requires set progress");
        boundStep = step;
        boundState = state;
        listener = callbacks;
        adaptActionLayout();
        boolean expanded = state.isExpanded(step.id);
        setVisibility(expanded ? VISIBLE : GONE);
        if (!expanded) return;
        progress.setText(getResources().getQuantityString(R.plurals.step_progress,
                setProgress.plannedSets,
                setProgress.actualRepetitions.size(), setProgress.plannedSets));
        progress.setTextColor(palette.muted);
        String draft = state.draft(step.id, join(setProgress.actualRepetitions));
        if (!draft.equals(repetitions.getText().toString())) {
            binding = true;
            repetitions.setText(draft);
            repetitions.setSelection(repetitions.length());
            binding = false;
        }
        repetitions.setHint(String.valueOf(setProgress.plannedRepetitions));
        repetitions.setTextColor(palette.ink);
        repetitions.setHintTextColor(palette.hint);
        repetitions.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        repetitions.setContentDescription(getContext().getString(R.string.set_edit_hint));
        repetitions.setError(state.error(step.id));
        save.setText(R.string.set_progress_save);
        save.setTextColor(palette.accentText);
        save.setBackground(style.pill(palette.accent, 24));
        toggleDone.setText(step.done ? R.string.set_reopen : R.string.set_finish);
        toggleDone.setTextColor(palette.ink2);
        save.setOnClickListener(view -> withParsed(values -> callbacks.onSave(step.id, values)));
        toggleDone.setOnClickListener(view -> {
            if (step.done) withParsed(values -> callbacks.onReopen(step.id, values));
            else callbacks.onFinish(step.id);
        });
        WoodGrainView.applyTextHalo(progress, palette.leaf1);
        WoodGrainView.applyTextHalo(toggleDone, palette.leaf1);
    }

    List<View> grainTextViews() {
        return Arrays.asList(progress, repetitions, save, toggleDone);
    }

    private void adaptActionLayout() {
        android.content.res.Configuration configuration = getResources().getConfiguration();
        boolean stacked = configuration.screenWidthDp <= 360 || configuration.fontScale >= 1.3f;
        actionRow.setOrientation(stacked ? VERTICAL : HORIZONTAL);
        LayoutParams saveParams = (LayoutParams) save.getLayoutParams();
        saveParams.width = stacked ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT;
        saveParams.height = stacked ? LayoutParams.WRAP_CONTENT : style.dp(48);
        save.setLayoutParams(saveParams);
        LayoutParams toggleParams = (LayoutParams) toggleDone.getLayoutParams();
        toggleParams.width = stacked ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT;
        toggleParams.height = stacked ? LayoutParams.WRAP_CONTENT : style.dp(48);
        toggleParams.leftMargin = stacked ? 0 : style.dp(12);
        toggleParams.topMargin = stacked ? style.dp(4) : 0;
        toggleDone.setLayoutParams(toggleParams);
    }

    private void withParsed(java.util.function.Consumer<List<Integer>> action) {
        List<Integer> values = parse();
        if (values != null) action.accept(values);
    }

    private List<Integer> parse() {
        String raw = repetitions.getText().toString().trim();
        List<Integer> values = new ArrayList<>();
        if (!raw.isEmpty()) for (String part : raw.split("[,; ]+")) {
            try {
                int value = Integer.parseInt(part);
                if (value <= 0) throw new NumberFormatException();
                values.add(value);
            } catch (NumberFormatException error) {
                return reject(getContext().getString(R.string.err_set_zero));
            }
        }
        if (boundStep.setProgress != null
                && values.size() > boundStep.setProgress.plannedSets)
            return reject(getContext().getString(R.string.err_set_count));
        boundState = boundState.withError(boundStep.id, null);
        listener.onStateChanged(boundState);
        return values;
    }

    private List<Integer> reject(String message) {
        repetitions.setError(message);
        boundState = boundState.withError(boundStep.id, message);
        listener.onStateChanged(boundState);
        return null;
    }

    static String join(List<Integer> values) {
        StringBuilder result = new StringBuilder();
        for (Integer value : values) {
            if (result.length() > 0) result.append(", ");
            result.append(value);
        }
        return result.toString();
    }
}
