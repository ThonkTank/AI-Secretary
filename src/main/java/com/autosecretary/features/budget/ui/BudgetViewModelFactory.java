package com.autosecretary.features.budget.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.budget.application.importing.ApplyRecurringSuggestionsUseCase;
import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.application.BudgetSeedService;
import com.autosecretary.features.budget.application.BudgetTransactionMutationUseCase;
import com.autosecretary.features.budget.application.CreateTransferUseCase;
import com.autosecretary.features.budget.application.LoadBudgetLimitOverviewUseCase;
import com.autosecretary.features.budget.application.LoadBudgetOverviewUseCase;
import com.autosecretary.features.budget.application.ResolveBudgetAccountUseCase;

import java.util.concurrent.ExecutorService;

/**
 * {@link ViewModelProvider.Factory} for {@link BudgetViewModel}.
 *
 * <p>Android's {@code ViewModelProvider} requires a factory when the ViewModel has a
 * non-default constructor. This factory is created by {@code AppCompositionRoot} and
 * receives the shared infrastructure dependencies and use cases that are managed at
 * the app level.
 *
 * <p>App-scoped use cases are injected from {@code AppCompositionRoot}; the factory
 * only assembles the ViewModel constructor object graph.
 */
public class BudgetViewModelFactory implements ViewModelProvider.Factory {

    private final ExecutorService dbExecutor;
    private final ExecutorService ioExecutor;
    private final BudgetImportUseCase importUseCase;
    private final ApplyRecurringSuggestionsUseCase applyRecurringUseCase;
    private final CreateTransferUseCase createTransferUseCase;
    private final BudgetTransactionMutationUseCase transactionMutationUseCase;
    private final ResolveBudgetAccountUseCase resolveBudgetAccountUseCase;
    private final LoadBudgetLimitOverviewUseCase loadBudgetLimitOverviewUseCase;
    private final BudgetSeedService budgetSeedService;
    private final LoadBudgetOverviewUseCase loadBudgetOverviewUseCase;

    public BudgetViewModelFactory(ExecutorService dbExecutor,
                                  ExecutorService ioExecutor,
                                  BudgetImportUseCase importUseCase,
                                  ApplyRecurringSuggestionsUseCase applyRecurringUseCase,
                                  CreateTransferUseCase createTransferUseCase,
                                  BudgetTransactionMutationUseCase transactionMutationUseCase,
                                  ResolveBudgetAccountUseCase resolveBudgetAccountUseCase,
                                  LoadBudgetLimitOverviewUseCase loadBudgetLimitOverviewUseCase,
                                  BudgetSeedService budgetSeedService,
                                  LoadBudgetOverviewUseCase loadBudgetOverviewUseCase) {
        this.dbExecutor = dbExecutor;
        this.ioExecutor = ioExecutor;
        this.importUseCase = importUseCase;
        this.applyRecurringUseCase = applyRecurringUseCase;
        this.createTransferUseCase = createTransferUseCase;
        this.transactionMutationUseCase = transactionMutationUseCase;
        this.resolveBudgetAccountUseCase = resolveBudgetAccountUseCase;
        this.loadBudgetLimitOverviewUseCase = loadBudgetLimitOverviewUseCase;
        this.budgetSeedService = budgetSeedService;
        this.loadBudgetOverviewUseCase = loadBudgetOverviewUseCase;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(BudgetViewModel.class)) {
            BudgetViewModel.Infrastructure infrastructure =
                    new BudgetViewModel.Infrastructure(dbExecutor, ioExecutor);
            BudgetViewModel.UseCases useCases = new BudgetViewModel.UseCases(
                    importUseCase,
                    applyRecurringUseCase,
                    createTransferUseCase,
                    transactionMutationUseCase,
                    resolveBudgetAccountUseCase,
                    loadBudgetLimitOverviewUseCase,
                    budgetSeedService
            );
            BudgetViewModel.Presentation presentation = new BudgetViewModel.Presentation(
                    loadBudgetOverviewUseCase
            );
            return modelClass.cast(new BudgetViewModel(
                    infrastructure,
                    useCases,
                    presentation));
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
