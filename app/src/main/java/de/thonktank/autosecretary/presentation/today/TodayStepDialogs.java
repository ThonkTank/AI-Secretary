package de.thonktank.autosecretary.presentation.today;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;

import de.thonktank.autosecretary.EditorStepState;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.StepEditorValidator;
import de.thonktank.autosecretary.domain.model.StepNote;
import de.thonktank.autosecretary.domain.usecase.TodayUseCases;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.UiTextProvider;

import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns Today's single step dialog on the main thread. Domain calls run on the worker. */
final class TodayStepDialogs {
    enum Kind { NOTE, STEP }

    private static final String SAVED = "today.stepDialog";
    private static final String LEGACY_STEP = "today.stepEdit";
    private static final String LEGACY_NOTE = "today.stepNote";

    private final TodayUseCases today;
    private final SavedStateHandle saved;
    private final Executor worker;
    private final UiTextProvider texts;
    private final AppLogger logger;
    private final Consumer<StepDialogDraft> publish;
    private final BiConsumer<TodayRequest.Kind, String> feedback;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable private StepDialogDraft draft;
    @Nullable private Kind loading;
    private long generation;
    private boolean available = true;
    private boolean closed;

    TodayStepDialogs(TodayUseCases today, SavedStateHandle saved, Executor worker,
                     UiTextProvider texts, AppLogger logger, Consumer<StepDialogDraft> publish,
                     BiConsumer<TodayRequest.Kind, String> feedback) {
        this.today = today;
        this.saved = saved;
        this.worker = worker;
        this.texts = texts;
        this.logger = logger;
        this.publish = publish;
        this.feedback = feedback;
        Bundle value = saved.get(SAVED);
        if (value != null) {
            draft = StepEditDraft.restore(value.getBundle("step"));
            if (draft == null) draft = StepNoteDraft.restore(value.getBundle("note"));
        } else if (saved.contains(LEGACY_STEP) || saved.contains(LEGACY_NOTE)) {
            // Before the shared slot, a full edit took precedence over a pending note.
            draft = StepEditDraft.restore(saved.get(LEGACY_STEP));
            if (draft == null) draft = StepNoteDraft.restore(saved.get(LEGACY_NOTE));
            persist();
        }
        saved.remove(LEGACY_STEP);
        saved.remove(LEGACY_NOTE);
    }

    @Nullable StepDialogDraft draft() { return draft; }

    @AnyThread void open(Kind kind, String stepId) {
        // Today actions can also be dispatched outside the UI thread.
        if (Looper.myLooper() != main.getLooper()) {
            main.post(() -> open(kind, stepId));
            return;
        }
        if (closed || !available || draft != null || loading == Kind.STEP) return;
        loading = kind;
        generation++;
        submit("load " + kind, () -> {
            if (kind == Kind.STEP) return StepEditDraft.from(today.editStep.load(stepId));
            StepNote note = today.editStepNote.load(stepId);
            if (note == null) throw new IllegalArgumentException("Step no longer exists");
            return new StepNoteDraft(note.id, note.note, "", false);
        }, this::setDraft, error -> {
            loading = null;
            feedback.accept(TodayRequest.Kind.ERROR, message(kind, error));
        });
    }

    void setAvailable(boolean value) {
        available = value;
        if (!value && loading != null) {
            loading = null;
            generation++;
        }
    }

    void changeNote(String text) {
        if (draft instanceof StepNoteDraft && !draft.saving && !closed)
            setDraft(new StepNoteDraft(draft.id, text, "", false));
    }

    void changeStep(EditorStepState value) {
        if (!(draft instanceof StepEditDraft) || draft.saving || closed) return;
        StepEditDraft step = (StepEditDraft) draft;
        if (step.step.id.equals(value.id)) setDraft(step.change(value));
    }

    void cancel(Kind kind) {
        if (closed || (draft != null && draft.saving)) return;
        if (loading != kind && !editing(kind)) return;
        generation++;
        setDraft(null);
    }

    void save(Kind kind) {
        if (closed || !editing(kind) || draft.saving) return;
        if (kind == Kind.NOTE) {
            StepNoteDraft note = (StepNoteDraft) draft;
            setDraft(new StepNoteDraft(note.id, note.text, "", true));
            submit("save note", () -> {
                today.editStepNote.save(note.id, note.text);
                return false;
            }, this::savedSuccessfully, error ->
                    setDraft(new StepNoteDraft(note.id, note.text, message(kind, error), false)));
        } else {
            StepEditDraft step = (StepEditDraft) draft;
            if (!StepEditorValidator.issues(step.step, step.showCadence()).isEmpty()) {
                setDraft(step.status("", false));
                return;
            }
            setDraft(step.status("", true));
            submit("save step", () -> today.editStep.save(step.id,
                    step.original.definition(0, step.once), step.step.definition(0, step.once)),
                    this::savedSuccessfully, error -> setDraft(step.status(message(kind, error), false)));
        }
    }

    private boolean editing(Kind kind) {
        return kind == Kind.STEP ? draft instanceof StepEditDraft : draft instanceof StepNoteDraft;
    }

    private void savedSuccessfully(boolean deferred) {
        setDraft(null);
        if (deferred) feedback.accept(TodayRequest.Kind.INFO, texts.text(R.string.step_edit_deferred));
    }

    private String message(Kind kind, RuntimeException error) {
        if (kind == Kind.STEP && error instanceof IllegalArgumentException && error.getMessage() != null)
            return error.getMessage();
        return texts.text(R.string.error_change_save);
    }

    private <T> void submit(String operation, Supplier<T> work, Consumer<T> success,
                            Consumer<RuntimeException> failure) {
        long session = generation;
        worker.execute(() -> {
            try {
                T result = work.get();
                deliver(session, () -> success.accept(result));
            } catch (RuntimeException error) {
                logger.error("TodayStepDialogs", operation, error);
                deliver(session, () -> failure.accept(error));
            }
        });
    }

    @AnyThread private void deliver(long session, Runnable result) {
        main.post(() -> {
            if (!closed && generation == session) result.run();
        });
    }

    private void setDraft(@Nullable StepDialogDraft value) {
        loading = null;
        draft = value;
        persist();
        publish.accept(value);
    }

    private void persist() {
        if (draft == null) {
            saved.remove(SAVED);
            return;
        }
        Bundle value = new Bundle();
        value.putBundle(draft instanceof StepEditDraft ? "step" : "note", draft.saved());
        saved.set(SAVED, value);
    }

    void close() {
        closed = true;
        main.removeCallbacksAndMessages(null);
    }
}
