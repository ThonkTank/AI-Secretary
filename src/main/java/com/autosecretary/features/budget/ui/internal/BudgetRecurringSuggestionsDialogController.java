package com.autosecretary.features.budget.ui.internal;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.features.budget.domain.RecurringSuggestion;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BudgetRecurringSuggestionsDialogController {

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

        View dialogView = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.budget_recurring_suggestions_dialog, null);
        LinearLayout listContainer = dialogView.findViewById(R.id.BudgetRecurringSuggestionList);
        TextView selectionInfo = dialogView.findViewById(R.id.BudgetRecurringSelectionInfo);

        boolean[] selections = new boolean[suggestions.size()];
        for (int i = 0; i < selections.length; i++) {
            selections[i] = true;
        }

        LayoutInflater inflater = LayoutInflater.from(fragment.requireContext());
        List<CheckBox> checkBoxes = new ArrayList<>();

        for (int i = 0; i < suggestions.size(); i++) {
            RecurringSuggestion suggestion = suggestions.get(i);
            View row = inflater.inflate(R.layout.budget_recurring_suggestion_item, listContainer, false);

            CheckBox checkbox = row.findViewById(R.id.BudgetSuggestionCheckbox);
            TextView payee = row.findViewById(R.id.BudgetSuggestionPayee);
            TextView pattern = row.findViewById(R.id.BudgetSuggestionPattern);
            TextView count = row.findViewById(R.id.BudgetSuggestionCount);
            TextView confidence = row.findViewById(R.id.BudgetSuggestionConfidence);
            TextView amount = row.findViewById(R.id.BudgetSuggestionAmount);

            checkbox.setChecked(true);
            checkBoxes.add(checkbox);

            payee.setText(suggestion.displayPayee());
            pattern.setText(getPatternDescription(suggestion));
            count.setText(fragment.getString(R.string.budget_recurring_transactions_count,
                    suggestion.transactionIds().size()));
            confidence.setText(fragment.getString(R.string.budget_recurring_confidence,
                    suggestion.confidenceScore() * 100));
            amount.setText(String.format(Locale.GERMAN, "%.2f €",
                    Math.abs(suggestion.avgAmountCents()) / 100.0));

            amount.setTextColor(suggestion.avgAmountCents() >= 0
                    ? getColorFromResources(R.color.budget_positive)
                    : getColorFromResources(R.color.budget_negative));

            if (suggestion.confidenceScore() >= 0.7) {
                confidence.setTextColor(getColorFromResources(R.color.budget_positive));
            } else if (suggestion.confidenceScore() >= 0.5) {
                confidence.setTextColor(getColorFromResources(R.color.budget_warning));
            } else {
                confidence.setTextColor(getColorFromResources(R.color.budget_neutral));
            }

            listContainer.addView(row);
        }

        updateSelectionInfo(selectionInfo, selections);

        AlertDialog dialog = new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.budget_recurring_title)
                .setView(dialogView)
                .setPositiveButton(fragment.getString(R.string.budget_recurring_create, countSelected(selections)),
                        null)
                .setNegativeButton(R.string.budget_recurring_skip, null)
                .create();

        dialog.setOnShowListener(d -> {
            Button createButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            createButton.setOnClickListener(v -> {
                List<RecurringSuggestion> selected = new ArrayList<>();
                for (int i = 0; i < suggestions.size(); i++) {
                    if (selections[i]) {
                        selected.add(suggestions.get(i));
                    }
                }
                if (!selected.isEmpty()) {
                    listener.onApplyRecurringSuggestions(selected);
                }
                dialog.dismiss();
            });

            for (int i = 0; i < checkBoxes.size(); i++) {
                int index = i;
                View row = listContainer.getChildAt(i);
                row.setOnClickListener(rv -> {
                    selections[index] = !selections[index];
                    checkBoxes.get(index).setChecked(selections[index]);
                    updateSelectionInfo(selectionInfo, selections);
                    updateCreateButton(createButton, selections);
                });
            }
        });

        dialog.show();
    }

    private void updateSelectionInfo(TextView info, boolean[] selections) {
        info.setText(fragment.getString(R.string.budget_recurring_selection_info,
                countSelected(selections), selections.length));
    }

    private void updateCreateButton(Button button, boolean[] selections) {
        int count = countSelected(selections);
        button.setText(fragment.getString(R.string.budget_recurring_create, count));
        button.setEnabled(count > 0);
    }

    private int countSelected(boolean[] selections) {
        int count = 0;
        for (boolean sel : selections) {
            if (sel) {
                count++;
            }
        }
        return count;
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
                    getDayName(suggestion.suggestedDayOfWeek()));
            case INTERVAL -> fragment.getString(R.string.budget_recurring_pattern_interval,
                    suggestion.suggestedValue());
        };
    }

    private String getDayName(DayOfWeek dow) {
        if (dow == null) {
            return "";
        }
        return switch (dow) {
            case MONDAY -> "Mo";
            case TUESDAY -> "Di";
            case WEDNESDAY -> "Mi";
            case THURSDAY -> "Do";
            case FRIDAY -> "Fr";
            case SATURDAY -> "Sa";
            case SUNDAY -> "So";
        };
    }

    private int getColorFromResources(int colorRes) {
        return ContextCompat.getColor(fragment.requireContext(), colorRes);
    }
}
