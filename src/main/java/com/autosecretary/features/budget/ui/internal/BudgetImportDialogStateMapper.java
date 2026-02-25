package com.autosecretary.features.budget.ui.internal;

import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.ui.state.BudgetImportDialogState;

public class BudgetImportDialogStateMapper {

    public BudgetImportDialogState map(BudgetImportUseCase.ImportResult importResult) {
        if (importResult == null) {
            return null;
        }
        return new BudgetImportDialogState(importResult.recurringSuggestions());
    }
}
