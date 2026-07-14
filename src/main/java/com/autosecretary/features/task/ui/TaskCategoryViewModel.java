package com.autosecretary.features.task.ui;

import androidx.lifecycle.ViewModel;

import com.autosecretary.features.task.application.config.TaskCategoryRepository;
import com.autosecretary.features.task.domain.model.TaskCategory;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * ViewModel owner for the category-management dialog. Delegates persistence to the
 * application-layer {@link TaskCategoryRepository} (UI never touches DAOs directly).
 */
public class TaskCategoryViewModel extends ViewModel {

    private final TaskCategoryRepository repository;
    private final ExecutorService workerExecutor;
    private final Executor callbackDispatcher;

    public TaskCategoryViewModel(
            TaskCategoryRepository repository,
            ExecutorService workerExecutor,
            Executor callbackDispatcher) {
        this.repository = repository;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
    }

    public void loadCategories(Consumer<List<TaskCategory>> onLoaded) {
        workerExecutor.execute(() -> {
            List<TaskCategory> categories = repository.loadAll();
            callbackDispatcher.execute(() -> onLoaded.accept(categories));
        });
    }

    public void saveCategories(List<TaskCategory> categories, List<String> deletedIds, Runnable onSaved) {
        workerExecutor.execute(() -> {
            repository.saveAll(categories, deletedIds);
            callbackDispatcher.execute(onSaved);
        });
    }
}
