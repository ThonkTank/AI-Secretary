package com.autosecretary.features.budget.ui.internal;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.autosecretary.shared.ui.DialogHelper;

import com.autosecretary.R;
import com.autosecretary.features.budget.domain.recurring.RecurringSuggestion;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manages the "recurring payment suggestions" dialog shown after a CSV/PDF import.
 * The dialog lists patterns detected by {@code RecurringPatternDetector}, each shown with
 * payee, schedule description, transaction count, confidence score, and average amount.
 * The user can check/uncheck individual suggestions before confirming; all are pre-selected
 * by default. Confirmed suggestions are converted into {@code BudgetRecurringTemplateEntity}
 * records by the caller via {@link Listener#onApplyRecurringSuggestions}.
 */
public class BudgetRecurringSuggestionsDialogController {

    // Confidence scores range from 0.0 to 1.0, produced by RecurringPatternDetector.
    // ≥ 0.7 → green (high confidence, safe to auto-apply)
    // ≥ 0.5 → yellow/warning (plausible but worth reviewing)
    // < 0.5 → grey/neutral (low confidence, shown for user awareness)
    private static final double HIGH_CONFIDENCE_THRESHOLD   = 0.7;
    private static final double MEDIUM_CONFIDENCE_THRESHOLD = 0.5;


    public interface Listener {
        void onApplyRecurringSuggestions(List<RecurringSuggestion> suggestions);
    }

    private final Fragment fragment;
    private final Listener listener;

    public BudgetRecurringSuggestionsDialogController(Fragment fragment, Listener listener) {
        this.fragment = fragment;
        this.listener = listener;
    }

    public void show(List<RecurringSuggestion> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return;
        }

        Context ctx = fragment.requireContext();
        LayoutInflater inflater = LayoutInflater.from(ctx);
        View dialogView = inflater.inflate(R.layout.budget_recurring_suggestions_dialog, null);
        LinearLayout listContainer = dialogView.findViewById(R.id.BudgetRecurringSuggestionList);
        TextView selectionInfo = dialogView.findViewById(R.id.BudgetRecurringSelectionInfo);

        List<CheckBox> rows = bindSuggestionRows(ctx, inflater, listContainer, suggestions);

        updateSelectionInfo(selectionInfo, rows);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.budget_recurring_title)
                .setView(dialogView)
                .setPositiveButton(fragment.getString(R.string.budget_recurring_create, countSelected(rows)),
                        null)
                .setNegativeButton(R.string.budget_recurring_skip, null)
                .create();

        DialogHelper.showWithValidation(dialog, () -> {
            List<RecurringSuggestion> selected = new ArrayList<>();
            for (int i = 0; i < suggestions.size(); i++) {
                if (rows.get(i).isChecked()) {
                    selected.add(suggestions.get(i));
                }
            }
            if (!selected.isEmpty()) {
                listener.onApplyRecurringSuggestions(selected);
            }
            dialog.dismiss();
        }, dlg -> {
            Button createButton = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
            for (int i = 0; i < rows.size(); i++) {
                CheckBox checkbox = rows.get(i);
                View rowView = listContainer.getChildAt(i);
                rowView.setOnClickListener(rv -> {
                    checkbox.toggle();
                    updateSelectionInfo(selectionInfo, rows);
                    updateCreateButton(createButton, rows);
                });
            }
        });
    }

    private List<CheckBox> bindSuggestionRows(Context ctx, LayoutInflater inflater,
                                              LinearLayout listContainer,
                                              List<RecurringSuggestion> suggestions) {
        List<CheckBox> rows = new ArrayList<>();
        for (RecurringSuggestion suggestion : suggestions) {
            View rowView = inflater.inflate(R.layout.budget_recurring_suggestion_item, listContainer, false);

            CheckBox checkbox = rowView.findViewById(R.id.BudgetSuggestionCheckbox);
            TextView payee = rowView.findViewById(R.id.BudgetSuggestionPayee);
            TextView pattern = rowView.findViewById(R.id.BudgetSuggestionPattern);
            TextView count = rowView.findViewById(R.id.BudgetSuggestionCount);
            TextView confidence = rowView.findViewById(R.id.BudgetSuggestionConfidence);
            TextView amount = rowView.findViewById(R.id.BudgetSuggestionAmount);

            checkbox.setChecked(true); // Pre-select all suggestions; user can uncheck unwanted ones.
            rows.add(checkbox);

            String patternDesc = getPatternDescription(suggestion);
            String countText = fragment.getString(R.string.budget_recurring_transactions_count,
                    suggestion.transactionIds().size());
            String confidenceText = fragment.getString(R.string.budget_recurring_confidence,
                    suggestion.confidenceScore() * 100);
            String amountText = CurrencyFormatter.eurosMagnitude(suggestion.avgAmountCents());

            payee.setText(suggestion.displayPayee());
            pattern.setText(patternDesc);
            count.setText(countText);
            confidence.setText(confidenceText);
            amount.setText(amountText);

            // avgAmountCents is positive for income-side recurring transactions, negative for expenses.
            amount.setTextColor(ContextCompat.getColor(ctx,
                    suggestion.avgAmountCents() >= 0 ? R.color.budget_positive : R.color.budget_negative));
            confidence.setTextColor(ContextCompat.getColor(ctx,
                    confidenceColorRes(suggestion.confidenceScore())));

            // Grouped contentDescription so screen readers announce the full suggestion in one pass.
            rowView.setContentDescription(
                    suggestion.displayPayee() + ", "
                    + patternDesc + ", "
                    + countText + ", "
                    + confidenceText + ", "
                    + amountText);
            listContainer.addView(rowView);
        }
        return rows;
    }

    private void updateSelectionInfo(TextView info, List<CheckBox> rows) {
        info.setText(fragment.getString(R.string.budget_recurring_selection_info,
                countSelected(rows), rows.size()));
    }

    private void updateCreateButton(Button button, List<CheckBox> rows) {
        int count = countSelected(rows);
        button.setText(fragment.getString(R.string.budget_recurring_create, count));
        button.setEnabled(count > 0);
    }

    private int countSelected(List<CheckBox> rows) {
        return (int) rows.stream().filter(CheckBox::isChecked).count();
    }

    private static int confidenceColorRes(double score) {
        if (score >= HIGH_CONFIDENCE_THRESHOLD)   return R.color.budget_positive;
        if (score >= MEDIUM_CONFIDENCE_THRESHOLD) return R.color.budget_warning;
        return R.color.budget_neutral;
    }

    private String getPatternDescription(RecurringSuggestion suggestion) {
        if (suggestion.suggestedType() == null) {
            return fragment.getString(R.string.budget_recurring_pattern_unknown);
        }
        return switch (suggestion.suggestedType()) {
            case MONTHLY_DAY -> fragment.getString(R.string.budget_recurring_pattern_monthly_day,
                    suggestion.suggestedValue());
            case MONTHLY_LAST -> fragment.getString(R.string.budget_recurring_pattern_monthly_last);
            case WEEKLY -> fragment.getString(R.string.budget_recurring_pattern_weekly,
                    suggestion.suggestedDayOfWeek() != null
                            ? suggestion.suggestedDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.GERMAN)
                            : "");
            case INTERVAL -> fragment.getString(R.string.budget_recurring_pattern_interval,
                    suggestion.suggestedValue());
        };
    }

}
