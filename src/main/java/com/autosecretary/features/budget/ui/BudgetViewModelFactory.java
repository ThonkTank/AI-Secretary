package com.autosecretary.features.budget.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.budget.application.importing.ApplyRecurringSuggestionsUseCase;
import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.application.BudgetSeedService;
import com.autosecretary.features.budget.application.BudgetTransactionMutationUseCase;
import com.autosecretary.features.budget.application.CalculateEffectiveBudgetLimitUseCase;
import com.autosecretary.features.budget.application.CreateTransferUseCase;
import com.autosecretary.features.budget.application.LoadBudgetLimitOverviewUseCase;
import com.autosecretary.features.budget.application.LoadBudgetOverviewUseCase;
import com.autosecretary.features.budget.application.ResolveBudgetAccountUseCase;
import com.autosecretary.features.budget.domain.BudgetRepository;

import java.util.concurrent.ExecutorService;

/**
 * {@link ViewModelProvider.Factory} for {@link BudgetViewModel}.
 *
 * <p>Android's {@code ViewModelProvider} requires a factory when the ViewModel has a
 * non-default constructor. This factory is created by {@code AppCompositionRoot} and
 * receives the shared infrastructure dependencies ({@code repository}, executors)
 * that are managed at the app level.
 *
 * <p>App-scoped use cases are injected from {@code AppCompositionRoot}; cheap application
 * helpers ({@link CalculateEffectiveBudgetLimitUseCase}, {@link BudgetSeedService}) are still
 * created inside {@link #create} because they are stateless wrappers around the repository.
 */
public class BudgetViewModelFactory implements ViewModelProvider.Factory {

    private final BudgetRepository repository;
    private final ExecutorService dbExecutor;
    private final ExecutorService ioExecutor;
    private final BudgetImportUseCase importUseCase;
    private final ApplyRecurringSuggestionsUseCase applyRecurringUseCase;
    private final CreateTransferUseCase createTransferUseCase;
    private final LoadBudgetOverviewUseCase loadBudgetOverviewUseCase;

    public BudgetViewModelFactory(BudgetRepository repository,
                                  ExecutorService dbExecutor,
                                  ExecutorService ioExecutor,
                                  BudgetImportUseCase importUseCase,
                                  ApplyRecurringSuggestionsUseCase applyRecurringUseCase,
                                  CreateTransferUseCase createTransferUseCase,
                                  LoadBudgetOverviewUseCase loadBudgetOverviewUseCase) {
        this.repository = repository;
        this.dbExecutor = dbExecutor;
        this.ioExecutor = ioExecutor;
        this.importUseCase = importUseCase;
        this.applyRecurringUseCase = applyRecurringUseCase;
        this.createTransferUseCase = createTransferUseCase;
        this.loadBudgetOverviewUseCase = loadBudgetOverviewUseCase;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(BudgetViewModel.class)) {
            BudgetViewModel.Infrastructure infrastructure =
                    new BudgetViewModel.Infrastructure(dbExecutor, ioExecutor);
            CalculateEffectiveBudgetLimitUseCase calculateEffectiveLimitUseCase =
                    new CalculateEffectiveBudgetLimitUseCase(repository);
            BudgetViewModel.UseCases useCases = new BudgetViewModel.UseCases(
                    importUseCase,
                    applyRecurringUseCase,
                    createTransferUseCase,
                    new BudgetTransactionMutationUseCase(repository),
                    new ResolveBudgetAccountUseCase(repository),
                    new LoadBudgetLimitOverviewUseCase(repository, calculateEffectiveLimitUseCase),
                    new BudgetSeedService(repository)
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
