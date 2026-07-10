package com.autosecretary.features.budget.ui;

import com.autosecretary.features.budget.application.LoadBudgetWidgetSummaryUseCase;
import com.autosecretary.shared.ContentDocumentReader;

import java.util.concurrent.ExecutorService;

public interface BudgetDependencies {
    BudgetViewModelFactory getBudgetViewModelFactory();

    ContentDocumentReader getContentDocumentReader();

    ExecutorService getIoExecutor();

    LoadBudgetWidgetSummaryUseCase createLoadBudgetWidgetSummaryUseCase();
}
