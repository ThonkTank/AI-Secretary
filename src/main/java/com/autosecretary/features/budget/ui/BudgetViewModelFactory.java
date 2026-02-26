package com.autosecretary.features.budget.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.budget.application.importing.ApplyRecurringSuggestionsUseCase;
import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.application.importing.StatementFileParser;
import com.autosecretary.features.budget.application.CreateTransferUseCase;
import com.autosecretary.features.budget.domain.AccountBalanceTimelineService;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.CalculateFreeBudgetUseCase;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class BudgetViewModelFactory implements ViewModelProvider.Factory {

    private final BudgetRepository repository;
    private final StatementFileParser parser;
    private final ExecutorService executor;
    private final Consumer<Runnable> postToMain;
    private final BudgetImportUseCase importUseCase;
    private final ApplyRecurringSuggestionsUseCase applyRecurringUseCase;
    private final CreateTransferUseCase createTransferUseCase;
    private final AccountBalanceTimelineService balanceTimelineService;
    private final CalculateFreeBudgetUseCase calculateFreeBudgetUseCase;

    public BudgetViewModelFactory(BudgetRepository repository,
                                  StatementFileParser parser,
                                  ExecutorService executor,
                                  Consumer<Runnable> postToMain,
                                  BudgetImportUseCase importUseCase,
                                  ApplyRecurringSuggestionsUseCase applyRecurringUseCase,
                                  CreateTransferUseCase createTransferUseCase,
                                  AccountBalanceTimelineService balanceTimelineService,
                                  CalculateFreeBudgetUseCase calculateFreeBudgetUseCase) {
        this.repository = repository;
        this.parser = parser;
        this.executor = executor;
        this.postToMain = postToMain;
        this.importUseCase = importUseCase;
        this.applyRecurringUseCase = applyRecurringUseCase;
        this.createTransferUseCase = createTransferUseCase;
        this.balanceTimelineService = balanceTimelineService;
        this.calculateFreeBudgetUseCase = calculateFreeBudgetUseCase;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(BudgetViewModel.class)) {
            return modelClass.cast(new BudgetViewModel(
                    repository, parser, executor, postToMain,
                    importUseCase, applyRecurringUseCase,
                    createTransferUseCase, balanceTimelineService,
                    calculateFreeBudgetUseCase));
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
