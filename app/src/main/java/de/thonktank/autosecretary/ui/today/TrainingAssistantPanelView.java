package de.thonktank.autosecretary.ui.today;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.function.Consumer;

import de.thonktank.autosecretary.AccessibilityRoles;
import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.UiStyle;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.TrainingDecision;
import de.thonktank.autosecretary.presentation.today.TrainingAssistantUiAction;
import de.thonktank.autosecretary.presentation.today.TrainingPromptUiModel;
import de.thonktank.autosecretary.ui.leaf.WoodGrainView;

/** Renders only the actionable Today question; history belongs to the task editor. */
public final class TrainingAssistantPanelView {
    private final Context context;
    private final UiStyle style;
    private final LinearLayout panel;
    private final TextView question;
    private final TextLinkView answerToggle;
    private final LinearLayout answer;
    private final EditText loadAnswer;
    private final TextLinkView applyLoadAnswer;
    private final TextLinkView unavailableLoadAnswer;
    private final TextLinkView laterLoadAnswer;
    private String boundStepId;
    private String boundTemplateId;

    public TrainingAssistantPanelView(Context context, LinearLayout host) {
        if (context == null || host == null)
            throw new IllegalArgumentException("Training assistant panel host is required");
        this.context = context;
        style = new UiStyle(context);

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        question = style.sans("", 16, 0, true);
        question.setMaxLines(4);
        panel.addView(question, new LinearLayout.LayoutParams(-1, -2));
        answerToggle = trainingLink(context.getString(R.string.training_answer));
        answerToggle.setId(R.id.training_answer_toggle);
        panel.addView(answerToggle, new LinearLayout.LayoutParams(-2, style.dp(44)));

        answer = new LinearLayout(context);
        answer.setId(R.id.training_answer_actions);
        answer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout loadAnswerRow = new LinearLayout(context);
        loadAnswerRow.setGravity(Gravity.CENTER_VERTICAL);
        loadAnswer = new EditText(context);
        loadAnswer.setId(R.id.training_answer_input);
        loadAnswer.setSingleLine(true);
        loadAnswer.setTextSize(16);
        loadAnswer.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        loadAnswer.setHint(context.getString(R.string.training_load_answer_hint));
        loadAnswerRow.addView(loadAnswer, new LinearLayout.LayoutParams(0, style.dp(48), 1));
        applyLoadAnswer = trainingLink(context.getString(R.string.training_load_apply));
        loadAnswerRow.addView(applyLoadAnswer,
                new LinearLayout.LayoutParams(-2, style.dp(48)));
        answer.addView(loadAnswerRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout answerActions = new LinearLayout(context);
        answerActions.setGravity(Gravity.CENTER_VERTICAL);
        unavailableLoadAnswer = trainingLink("");
        laterLoadAnswer = trainingLink(context.getString(R.string.training_load_later));
        answerActions.addView(unavailableLoadAnswer,
                new LinearLayout.LayoutParams(0, style.dp(44), 1));
        answerActions.addView(laterLoadAnswer,
                new LinearLayout.LayoutParams(-2, style.dp(44)));
        answer.addView(answerActions, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(answer, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(style.dp(52), style.dp(6), 0, 0);
        host.addView(panel, params);
    }

    public void bind(String stepId, TrainingPromptUiModel value, boolean active,
                     boolean answerExpanded,
                     DayPalette palette, Runnable toggleAnswer,
                     Consumer<TrainingAssistantUiAction> actions) {
        boolean open = value != null && active;
        panel.setVisibility(open ? View.VISIBLE : View.GONE);
        answer.setVisibility(open && answerExpanded ? View.VISIBLE : View.GONE);
        bindTrainingLink(answerToggle, palette);
        bindTrainingLink(applyLoadAnswer, palette);
        bindTrainingLink(unavailableLoadAnswer, palette);
        bindTrainingLink(laterLoadAnswer, palette);
        answerToggle.setOnClickListener(null);
        applyLoadAnswer.setOnClickListener(null);
        unavailableLoadAnswer.setOnClickListener(null);
        laterLoadAnswer.setOnClickListener(null);
        if (!open) {
            question.setText("");
            if (value == null || !active || !stepId.equals(boundStepId)
                    || !value.templateId.equals(boundTemplateId))
                loadAnswer.setText("");
            boundStepId = stepId;
            boundTemplateId = value == null ? null : value.templateId;
            return;
        }
        if (!stepId.equals(boundStepId) || !value.templateId.equals(boundTemplateId))
            loadAnswer.setText("");
        boundStepId = stepId;
        boundTemplateId = value.templateId;
        question.setText(context.getString(
                value.direction == TrainingDecision.LoadDirection.PROGRESS
                        ? R.string.training_load_question_higher
                        : R.string.training_load_question_lower,
                formatLoad(value.currentLoad)));
        question.setTextColor(palette.ink);
        WoodGrainView.applyTextHalo(question, palette.leaf1);
        answerToggle.setText(answerExpanded
                ? R.string.training_answer_close : R.string.training_answer);
        answerToggle.setContentDescription(context.getString(answerExpanded
                ? R.string.training_answer_close : R.string.training_answer));
        answerToggle.setOnClickListener(ignored -> toggleAnswer.run());
        loadAnswer.setTextColor(palette.ink);
        loadAnswer.setHintTextColor(palette.hint);
        unavailableLoadAnswer.setText(R.string.training_load_no_higher);
        applyLoadAnswer.setOnClickListener(ignored -> actions.accept(
                new TrainingAssistantUiAction.ApplyLoad(value.templateId,
                        loadAnswer.getText().toString(), value.currentLoad.mode,
                        value.currentLoad.unit)));
        boolean unavailableChoice = value.direction
                == TrainingDecision.LoadDirection.PROGRESS;
        unavailableLoadAnswer.setVisibility(unavailableChoice ? View.VISIBLE : View.GONE);
        unavailableLoadAnswer.setOnClickListener(unavailableChoice ? ignored -> actions.accept(
                new TrainingAssistantUiAction.NoHigherLoad(value.templateId)) : null);
        laterLoadAnswer.setOnClickListener(ignored -> actions.accept(
                new TrainingAssistantUiAction.Later(value.templateId)));
    }

    public void resetTransientState() {
        loadAnswer.setText("");
    }

    private TextLinkView trainingLink(String text) {
        TextLinkView view = new TextLinkView(context);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        AccessibilityRoles.button(view);
        return view;
    }

    private static void bindTrainingLink(TextLinkView view, DayPalette palette) {
        view.bind(palette.hint, palette.dot);
    }

    private String formatLoad(ResistanceLoad load) {
        if (load.mode == ResistanceLoad.Mode.BODYWEIGHT)
            return context.getString(R.string.training_load_bodyweight_short);
        double value = (load.milliUnits == null ? 0L : load.milliUnits) / 1000d;
        String unit = load.unit == ResistanceLoad.Unit.LB ? "lb" : "kg";
        String prefix = load.mode == ResistanceLoad.Mode.BODYWEIGHT_PLUS ? "+"
                : load.mode == ResistanceLoad.Mode.ASSISTED_BODYWEIGHT ? "−" : "";
        return prefix + String.format(Locale.getDefault(), "%.1f %s", value, unit);
    }
}
