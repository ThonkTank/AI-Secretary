package com.autosecretary.ui.editor;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.autosecretary.application.TimeProvider;
import com.autosecretary.application.WorkItemRepository;
import com.autosecretary.domain.WorkItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** Retains and persists only work-item editor state. */
public final class EditorViewModel extends ViewModel {
    private static final String EDITOR = "editor.state";
    private final SavedStateHandle savedState;
    private final WorkItemRepository repository;
    private final TimeProvider clock;
    private final ExecutorService databaseExecutor;
    private final Executor uiExecutor;
    private final MutableLiveData<EditorUiState> state;
    private final MutableLiveData<EditorUiEffect> effects = new MutableLiveData<>();
    private Future<?> running;
    private long effectSequence;

    public EditorViewModel(
            SavedStateHandle savedState,
            WorkItemRepository repository,
            TimeProvider clock,
            ExecutorService databaseExecutor,
            Executor uiExecutor) {
        this.savedState = savedState;
        this.repository = repository;
        this.clock = clock;
        this.databaseExecutor = databaseExecutor;
        this.uiExecutor = uiExecutor;
        ObligationEditorState restored = savedState.get(EDITOR);
        state = new MutableLiveData<>(restored == null
                ? new EditorUiState.Closed() : new EditorUiState.Editing(restored));
    }

    public LiveData<EditorUiState> state() { return state; }
    public LiveData<EditorUiEffect> effects() { return effects; }
    public LocalDate today() { return localNow().toLocalDate(); }

    public ObligationEditorState editor() {
        EditorUiState value = state.getValue();
        if (value instanceof EditorUiState.Editing editing) return editing.editor();
        if (value instanceof EditorUiState.Saving saving) return saving.editor();
        if (value instanceof EditorUiState.Failed failed) return failed.editor();
        return null;
    }

    public void open(boolean routine, WorkItem existing) {
        setEditor(ObligationEditorState.initial(routine, existing, localNow()));
    }

    public void edit(ObligationEditorState editor) { setEditor(editor); }

    public void addStep() {
        ObligationEditorState editor = editor();
        if (editor != null) setEditor(editor.addStep());
    }

    public void removeStep(String stepId) {
        ObligationEditorState editor = editor();
        if (editor != null) setEditor(editor.removeStep(stepId));
    }

    public void moveStep(String stepId, int delta) {
        ObligationEditorState editor = editor();
        if (editor != null) setEditor(editor.moveStep(stepId, delta));
    }

    public void submit(ObligationEditorState form) {
        ObligationEditorState checked = form.validated(localNow());
        setEditor(checked);
        if (!checked.valid()) return;
        run(checked, () -> repository.save(checked.toWorkItem()), false);
    }

    public void delete() {
        ObligationEditorState editor = editor();
        if (editor == null || editor.existingId() == null) return;
        run(editor, () -> repository.delete(editor.existingId()), true);
    }

    public void close() {
        if (running != null && !running.isDone()) running.cancel(true);
        savedState.set(EDITOR, null);
        state.setValue(new EditorUiState.Closed());
    }

    public void consumeEffect(long id) {
        EditorUiEffect effect = effects.getValue();
        if (effect != null && effect.id() == id) effects.setValue(null);
    }

    private void run(ObligationEditorState editor, Runnable action, boolean deleting) {
        state.setValue(new EditorUiState.Saving(editor));
        running = databaseExecutor.submit(() -> {
            try {
                action.run();
                uiExecutor.execute(() -> {
                    savedState.set(EDITOR, null);
                    effects.setValue(deleting ? new EditorUiEffect.Deleted(++effectSequence)
                            : new EditorUiEffect.Saved(++effectSequence));
                    state.setValue(new EditorUiState.Closed());
                });
            } catch (Throwable error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                uiExecutor.execute(() -> {
                    state.setValue(new EditorUiState.Failed(editor, message));
                    effects.setValue(new EditorUiEffect.Error(++effectSequence, message));
                });
            }
        });
    }

    private void setEditor(ObligationEditorState editor) {
        savedState.set(EDITOR, editor);
        state.setValue(new EditorUiState.Editing(editor));
    }

    private LocalDateTime localNow() {
        return LocalDateTime.ofInstant(clock.now(), clock.zone());
    }

    @Override protected void onCleared() {
        if (running != null) running.cancel(true);
    }
}
