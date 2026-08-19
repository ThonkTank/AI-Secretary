package de.thonktank.autosecretary;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.thonktank.autosecretary.presentation.TaskStepUiModel;

/** One focus-card step row, including accessibility and optional set-progress editing. */
public final class FocusStepRowView extends LinearLayout {
    public interface Actions {
        void onToggleStep(String stepId);
        void onEditStepProgress(String stepId, List<Integer> repetitions, boolean done);
        void onFinishExercise(String stepId);
        void onReopenExercise(String stepId, List<Integer> repetitions);
        void onSetProgressEditorStateChanged(SetProgressEditorState state);
    }

    private final UiStyle style;
    private final LinearLayout header;
    private final DewDotView reward;
    private final TextView title;
    private final TextView subtitle;
    private final SetProgressEditorView editor;

    public FocusStepRowView(Context context) {
        super(context);
        style = new UiStyle(context);
        setOrientation(VERTICAL);
        header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        reward = new DewDotView(context);
        LinearLayout.LayoutParams rewardParams = new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.touch_target),
                getResources().getDimensionPixelSize(R.dimen.touch_target));
        rewardParams.setMargins(0,
                getResources().getDimensionPixelOffset(R.dimen.focus_step_dot_top_offset),
                0, getResources().getDimensionPixelOffset(
                        R.dimen.focus_step_dot_bottom_offset));
        header.addView(reward, rewardParams);
        title = style.sans("", 19, 0, false);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.setMargins(getResources().getDimensionPixelSize(
                R.dimen.focus_step_header_text_gap), 0, 0, 0);
        header.addView(title, titleParams);
        addView(header, new LinearLayout.LayoutParams(-1, -2));

        subtitle = style.sans("", 14, 0, false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.setMargins(getResources().getDimensionPixelSize(
                        R.dimen.focus_step_text_start),
                getResources().getDimensionPixelOffset(
                        R.dimen.focus_step_subtitle_top_offset),
                0, getResources().getDimensionPixelSize(
                        R.dimen.focus_step_subtitle_bottom_gap));
        addView(subtitle, subtitleParams);
        editor = new SetProgressEditorView(context);
        addView(editor, new LinearLayout.LayoutParams(-1, -2));
    }

    public void bind(TaskStepUiModel step, DayPalette palette,
                     SetProgressEditorState editorState, Actions actions) {
        reward.bind(step.done, false, palette,
                step.done ? step.earnedXp : step.claimableXp);
        String description = step.title + (step.subtitle.isEmpty()
                ? "" : ", " + step.subtitle);
        reward.setContentDescription((step.done
                ? getContext().getString(R.string.marker_done) + ": " : "") + description);
        title.setText(step.done ? strike(step.title) : step.title);
        title.setTextColor(step.done ? palette.done : palette.ink);
        title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        subtitle.setText(step.subtitle);
        subtitle.setTextColor(step.done ? palette.done : palette.muted);
        subtitle.setVisibility(step.subtitle.isEmpty() ? GONE : VISIBLE);
        subtitle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        WoodGrainView.applyTextHalo(title, palette.leaf1);
        WoodGrainView.applyTextHalo(subtitle, palette.leaf1);
        if (step.setProgress == null) {
            reward.setOnClickListener(view -> actions.onToggleStep(step.id));
            header.setOnClickListener(null);
            title.setOnClickListener(null);
            editor.setVisibility(GONE);
            return;
        }
        View.OnClickListener expand = view -> actions.onSetProgressEditorStateChanged(
                editorState.toggle(step.id,
                        SetProgressEditorView.join(step.setProgress.actualRepetitions)));
        reward.setOnClickListener(expand);
        header.setOnClickListener(expand);
        header.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        title.setOnClickListener(null);
        editor.bind(step, palette, editorState, new SetProgressEditorView.Listener() {
            @Override public void onStateChanged(SetProgressEditorState state) {
                actions.onSetProgressEditorStateChanged(state);
            }

            @Override public void onSave(String stepId, List<Integer> repetitions) {
                actions.onEditStepProgress(stepId, repetitions, step.done);
            }

            @Override public void onFinish(String stepId) {
                actions.onFinishExercise(stepId);
            }

            @Override public void onReopen(String stepId, List<Integer> repetitions) {
                actions.onReopenExercise(stepId, repetitions);
            }
        });
    }

    public View rewardAnchor() { return reward; }

    public List<View> grainTextViews() {
        List<View> views = new ArrayList<>();
        views.add(title);
        if (subtitle.getVisibility() == VISIBLE) views.add(subtitle);
        if (editor.getVisibility() == VISIBLE) views.addAll(editor.grainTextViews());
        return views;
    }

    CharSequence renderedTitle() { return title.getText(); }
    CharSequence renderedSubtitle() { return subtitle.getText(); }
    boolean editorVisible() { return editor.getVisibility() == VISIBLE; }

    private static CharSequence strike(String text) {
        SpannableString value = new SpannableString(text);
        value.setSpan(new StrikethroughSpan(), 0, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return value;
    }
}
