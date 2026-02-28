package com.autosecretary.features.budget.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.budget.application.importing.ApplyRecurringSuggestionsUseCase;
import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.application.BudgetSeedService;
import com.autosecretary.features.budget.application.CalculateEffectiveBudgetLimitUseCase;
import com.autosecretary.features.budget.application.CreateTransferUseCase;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.ui.internal.BudgetOverviewLoader;
import com.autosecretary.features.budget.ui.internal.BudgetSummaryPresentationMapper;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * {@link ViewModelProvider.Factory} for {@link BudgetViewModel}.
 *
 * <p>Android's {@code ViewModelProvider} requires a factory when the ViewModel has a
 * non-default constructor. This factory is created by {@code AppCompositionRoot} and
 * receives the shared infrastructure dependencies ({@code repository}, {@code executor},
 * {@code postToMain}) that are managed at the app level.
 *
 * <p>Pure computation helpers ({@link BudgetSummaryPresentationMapper},
 * {@link BudgetOverviewLoader}, {@link CalculateEffectiveBudgetLimitUseCase},
 * {@link BudgetSeedService}) are constructed here inside {@link #create} rather than
 * injected from outside: they are stateless, cheap to construct, and tying them to the
 * factory's constructor would expose implementation details of the ViewModel to the DI root.
 */
public class BudgetViewModelFactory implements ViewModelProvider.Factory {

    private final BudgetRepository repository;
    private final ExecutorService executor;
    private final Consumer<Runnable> postToMain;
    private final BudgetImportUseCase importUseCase;
    private final ApplyRecurringSuggestionsUseCase applyRecurringUseCase;
    private final CreateTransferUseCase createTransferUseCase;

    public BudgetViewModelFactory(BudgetRepository repository,
                                  ExecutorService executor,
                                  Consumer<Runnable> postToMain,
                                  BudgetImportUseCase importUseCase,
                                  ApplyRecurringSuggestionsUseCase applyRecurringUseCase,
                                  CreateTransferUseCase createTransferUseCase) {
        this.repository = repository;
        this.executor = executor;
        this.postToMain = postToMain;
        this.importUseCase = importUseCase;
        this.applyRecurringUseCase = applyRecurringUseCase;
        this.createTransferUseCase = createTransferUseCase;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(BudgetViewModel.class)) {
            BudgetSummaryPresentationMapper summaryPresentationMapper = new BudgetSummaryPresentationMapper();
            BudgetOverviewLoader overviewLoader = new BudgetOverviewLoader(
                    repository,
                    summaryPresentationMapper);
            return modelClass.cast(new BudgetViewModel(
                    repository, executor, postToMain,
                    importUseCase,
                    applyRecurringUseCase,
                    createTransferUseCase,
                    new CalculateEffectiveBudgetLimitUseCase(repository),
                    overviewLoader,
                    summaryPresentationMapper,
                    new BudgetSeedService(repository)));
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
