package com.autosecretary.features.task.ui;

import androidx.lifecycle.ViewModel;

import com.autosecretary.features.task.application.config.TaskCategoryRepository;
import com.autosecretary.features.task.application.config.TaskCategoryWindowRepository;
import com.autosecretary.features.task.domain.model.TaskCategory;
import com.autosecretary.features.task.domain.model.TaskCategoryWindow;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

/**
 * ViewModel owner for the category-window dialog. Loads the existing per-weekday windows plus the
 * available categories (to populate each block's category picker) and persists edited windows.
 */
public class TaskCategoryWindowViewModel extends ViewModel {

    private final TaskCategoryWindowRepository windowRepository;
    private final TaskCategoryRepository categoryRepository;
    private final ExecutorService workerExecutor;
    private final Executor callbackDispatcher;

    public TaskCategoryWindowViewModel(
            TaskCategoryWindowRepository windowRepository,
            TaskCategoryRepository categoryRepository,
            ExecutorService workerExecutor,
            Executor callbackDispatcher) {
        this.windowRepository = windowRepository;
        this.categoryRepository = categoryRepository;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
    }

    /** Loads the current windows and the categories available to assign them to. */
    public void load(BiConsumer<List<TaskCategoryWindow>, List<TaskCategory>> onLoaded) {
        workerExecutor.execute(() -> {
            List<TaskCategoryWindow> windows = windowRepository.loadAll();
            List<TaskCategory> categories = categoryRepository.loadAll();
            callbackDispatcher.execute(() -> onLoaded.accept(windows, categories));
        });
    }

    public void save(List<TaskCategoryWindow> windows, List<String> deletedIds, Runnable onSaved) {
        workerExecutor.execute(() -> {
            windowRepository.saveWindows(windows, deletedIds);
            callbackDispatcher.execute(onSaved);
        });
    }
}
