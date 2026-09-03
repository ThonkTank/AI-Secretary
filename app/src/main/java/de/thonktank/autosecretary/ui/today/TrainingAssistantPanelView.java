package de.thonktank.autosecretary.ui.today;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
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
import de.thonktank.autosecretary.presentation.today.TrainingAssistantUiAction;
import de.thonktank.autosecretary.presentation.today.TrainingContextUiModel;
import de.thonktank.autosecretary.ui.leaf.WoodGrainView;

/** Owns Today assistant status, load question, answer field, history and undo controls. */
public final class TrainingAssistantPanelView {
    private final Context context;
    private final UiStyle style;
    private final TextView summary;
    private final LinearLayout details;
    private final TextView loadQuestion;
    private final EditText loadAnswer;
    private final TextLinkView applyLoadAnswer;
    private final TextLinkView unavailableLoadAnswer;
    private final TextLinkView laterLoadAnswer;
    private final TextView historyTitle;
    private final LinearLayout history;
    private final TextLinkView undoAdjustment;
    private String boundTemplateId;

    public TrainingAssistantPanelView(Context context, LinearLayout host) {
        if (context == null || host == null)
            throw new IllegalArgumentException("Training assistant panel host is required");
        this.context = context;
        style = new UiStyle(context);

        summary = style.sans("", 14, 0, false);
        summary.setMaxLines(3);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, -2);
        summaryParams.setMargins(style.dp(52), style.dp(6), 0, 0);
        host.addView(summary, summaryParams);

        details = new LinearLayout(context);
        details.setOrientation(LinearLayout.VERTICAL);
        loadQuestion = style.sans("", 15, 0, true);
        details.addView(loadQuestion, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout loadAnswerRow = new LinearLayout(context);
        loadAnswerRow.setGravity(Gravity.CENTER_VERTICAL);
        loadAnswer = new EditText(context);
        loadAnswer.setSingleLine(true);
        loadAnswer.setTextSize(16);
        loadAnswer.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        loadAnswer.setHint(context.getString(R.string.training_load_answer_hint));
        loadAnswerRow.addView(loadAnswer,
                new LinearLayout.LayoutParams(0, style.dp(48), 1));
        applyLoadAnswer = trainingLink(context.getString(R.string.training_load_apply));
        loadAnswerRow.addView(applyLoadAnswer,
                new LinearLayout.LayoutParams(-2, style.dp(48)));
        details.addView(loadAnswerRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout answerActions = new LinearLayout(context);
        answerActions.setGravity(Gravity.CENTER_VERTICAL);
        unavailableLoadAnswer = trainingLink("");
        laterLoadAnswer = trainingLink(context.getString(R.string.training_load_later));
        answerActions.addView(unavailableLoadAnswer,
                new LinearLayout.LayoutParams(0, style.dp(44), 1));
        answerActions.addView(laterLoadAnswer,
                new LinearLayout.LayoutParams(-2, style.dp(44)));
        details.addView(answerActions, new LinearLayout.LayoutParams(-1, -2));
        historyTitle = style.sans(context.getString(R.string.training_history_title), 14, 0, true);
        LinearLayout.LayoutParams historyTitleParams = new LinearLayout.LayoutParams(-1, -2);
        historyTitleParams.topMargin = style.dp(8);
        details.addView(historyTitle, historyTitleParams);
        history = new LinearLayout(context);
        history.setOrientation(LinearLayout.VERTICAL);
        details.addView(history, new LinearLayout.LayoutParams(-1, -2));
        undoAdjustment = trainingLink(context.getString(R.string.training_undo));
        details.addView(undoAdjustment,
                new LinearLayout.LayoutParams(-2, style.dp(44)));
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(-1, -2);
        detailsParams.setMargins(style.dp(52), style.dp(6), 0, 0);
        host.addView(details, detailsParams);
    }

    public void bind(TrainingContextUiModel context, boolean active, DayPalette palette,
                     Consumer<TrainingAssistantUiAction> actions) {
        bindTrainingLink(applyLoadAnswer, palette);
        bindTrainingLink(unavailableLoadAnswer, palette);
        bindTrainingLink(laterLoadAnswer, palette);
        bindTrainingLink(undoAdjustment, palette);
        summary.setText("");
        loadQuestion.setText("");
        loadQuestion.setVisibility(android.view.View.GONE);
        loadAnswer.setVisibility(android.view.View.GONE);
        applyLoadAnswer.setVisibility(android.view.View.GONE);
        applyLoadAnswer.setOnClickListener(null);
        unavailableLoadAnswer.setVisibility(android.view.View.GONE);
        unavailableLoadAnswer.setOnClickListener(null);
        laterLoadAnswer.setVisibility(android.view.View.GONE);
        laterLoadAnswer.setOnClickListener(null);
        history.removeAllViews();
        historyTitle.setVisibility(android.view.View.GONE);
        history.setVisibility(android.view.View.GONE);
        undoAdjustment.setVisibility(android.view.View.GONE);
        undoAdjustment.setOnClickListener(null);
        summary.setVisibility(context == null ? android.view.View.GONE : android.view.View.VISIBLE);
        details.setVisibility(context != null && active
                ? android.view.View.VISIBLE : android.view.View.GONE);
        if (context == null) {
            boundTemplateId = null;
            loadAnswer.setText("");
            return;
        }
        String summaryText = context.latestAdjustmentLabel.isEmpty() ? context.statusLabel
                : context.statusLabel + " · " + context.latestAdjustmentLabel;
        summary.setText(summaryText);
        summary.setTextColor(palette.muted);
        WoodGrainView.applyTextHalo(summary, palette.leaf1);
        if (!context.templateId.equals(boundTemplateId)) loadAnswer.setText("");
        if (!active) {
            boundTemplateId = context.templateId;
            return;
        }
        boolean open = context.hasOpenLoadRequest();
        loadQuestion.setVisibility(open ? android.view.View.VISIBLE : android.view.View.GONE);
        loadAnswer.setVisibility(open ? android.view.View.VISIBLE : android.view.View.GONE);
        applyLoadAnswer.setVisibility(open ? android.view.View.VISIBLE : android.view.View.GONE);
        boolean unavailableChoice = open && context.openDirection
                == de.thonktank.autosecretary.domain.model.TrainingDecision.LoadDirection.PROGRESS;
        unavailableLoadAnswer.setVisibility(unavailableChoice
                ? android.view.View.VISIBLE : android.view.View.GONE);
        laterLoadAnswer.setVisibility(open ? android.view.View.VISIBLE : android.view.View.GONE);
        if (open) {
            loadQuestion.setText(this.context.getString(
                    context.openDirection
                            == de.thonktank.autosecretary.domain.model.TrainingDecision.LoadDirection.PROGRESS
                            ? R.string.training_load_question_higher
                            : R.string.training_load_question_lower,
                    formatLoad(context.openCurrentLoad)));
            loadQuestion.setTextColor(palette.ink);
            loadAnswer.setTextColor(palette.ink);
            loadAnswer.setHintTextColor(palette.hint);
            unavailableLoadAnswer.setText(R.string.training_load_no_higher);
            applyLoadAnswer.setOnClickListener(view -> actions.accept(
                    new TrainingAssistantUiAction.ApplyLoad(
                            context.templateId,
                            loadAnswer.getText().toString(),
                            context.openCurrentLoad.mode,
                            context.openCurrentLoad.unit)));
            unavailableLoadAnswer.setOnClickListener(unavailableChoice ? view -> actions.accept(
                    new TrainingAssistantUiAction.NoHigherLoad(context.templateId)) : null);
            laterLoadAnswer.setOnClickListener(view -> {
                details.setVisibility(android.view.View.GONE);
                actions.accept(new TrainingAssistantUiAction.Later(context.templateId));
            });
        }
        boundTemplateId = context.templateId;
        historyTitle.setTextColor(palette.muted);
        historyTitle.setVisibility(context.historyLabels.isEmpty()
                ? android.view.View.GONE : android.view.View.VISIBLE);
        history.setVisibility(context.historyLabels.isEmpty()
                ? android.view.View.GONE : android.view.View.VISIBLE);
        for (String label : context.historyLabels) {
            TextView item = style.sans("· " + label, 13, 0, false);
            item.setTextColor(palette.hint);
            item.setPadding(0, style.dp(2), 0, style.dp(2));
            history.addView(item, new LinearLayout.LayoutParams(-1, -2));
        }
        undoAdjustment.setVisibility(context.canUndo
                ? android.view.View.VISIBLE : android.view.View.GONE);
        undoAdjustment.setOnClickListener(context.canUndo ? view -> actions.accept(
                new TrainingAssistantUiAction.Undo(context.templateId)) : null);
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
