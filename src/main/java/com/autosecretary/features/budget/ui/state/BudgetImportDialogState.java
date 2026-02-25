package com.autosecretary.features.budget.ui.state;

import com.autosecretary.features.budget.domain.RecurringSuggestion;

import java.util.List;

public class BudgetImportDialogState {
    private final List<RecurringSuggestion> recurringSuggestions;

    public BudgetImportDialogState(List<RecurringSuggestion> recurringSuggestions) {
        this.recurringSuggestions = recurringSuggestions;
    }

    public List<RecurringSuggestion> getRecurringSuggestions() {
        return recurringSuggestions;
    }

    public boolean shouldShowRecurringSuggestionsDialog() {
        return recurringSuggestions != null && !recurringSuggestions.isEmpty();
    }
}
