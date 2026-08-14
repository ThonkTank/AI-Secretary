package com.autosecretary.ui.settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.autosecretary.application.PlanningSettingsRepository;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** Owns the planning form independently from dashboard rendering. */
public final class PlanningSettingsViewModel extends ViewModel {
    private static final String EDITOR = "planning-settings.state";
    private final SavedStateHandle savedState;
    private final PlanningSettingsRepository repository;
    private final ExecutorService databaseExecutor;
    private final Executor uiExecutor;
    private final MutableLiveData<PlanningSettingsUiState> state;
    private Future<?> running;

    public PlanningSettingsViewModel(
            SavedStateHandle savedState,
            PlanningSettingsRepository repository,
            ExecutorService databaseExecutor,
            Executor uiExecutor) {
        this.savedState = savedState;
        this.repository = repository;
        this.databaseExecutor = databaseExecutor;
        this.uiExecutor = uiExecutor;
        PlanningSettingsEditorState restored = savedState.get(EDITOR);
        state = new MutableLiveData<>(restored == null
                ? new PlanningSettingsUiState.Closed()
                : new PlanningSettingsUiState.Editing(restored));
    }

    public LiveData<PlanningSettingsUiState> state() { return state; }

    public PlanningSettingsEditorState editor() {
        PlanningSettingsUiState value = state.getValue();
        if (value instanceof PlanningSettingsUiState.Editing editing) return editing.editor();
        if (value instanceof PlanningSettingsUiState.Saving saving) return saving.editor();
        if (value instanceof PlanningSettingsUiState.Failed failed) return failed.editor();
        return null;
    }

    public void open() { setEditor(PlanningSettingsEditorState.from(repository.load())); }
    public void edit(PlanningSettingsEditorState editor) { setEditor(editor); }

    public void submit(PlanningSettingsEditorState form) {
        PlanningSettingsEditorState checked = form.validated();
        setEditor(checked);
        if (!checked.valid()) return;
        state.setValue(new PlanningSettingsUiState.Saving(checked));
        running = databaseExecutor.submit(() -> {
            try {
                repository.save(checked.toSettings());
                uiExecutor.execute(() -> {
                    savedState.set(EDITOR, null);
                    state.setValue(new PlanningSettingsUiState.Closed());
                });
            } catch (Throwable error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                uiExecutor.execute(() ->
                        state.setValue(new PlanningSettingsUiState.Failed(checked, message)));
            }
        });
    }

    public void close() {
        if (running != null && !running.isDone()) running.cancel(true);
        savedState.set(EDITOR, null);
        state.setValue(new PlanningSettingsUiState.Closed());
    }

    private void setEditor(PlanningSettingsEditorState editor) {
        savedState.set(EDITOR, editor);
        state.setValue(new PlanningSettingsUiState.Editing(editor));
    }

    @Override protected void onCleared() {
        if (running != null) running.cancel(true);
    }
}
