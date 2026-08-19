package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.TaskStepUiModel;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable presentation state for the inline set-progress editor. */
public final class SetProgressEditorState {
    @Nullable public final String expandedStepId;
    private final Map<String, String> drafts;
    private final Map<String, String> errors;

    private SetProgressEditorState(@Nullable String expandedStepId,
                                   Map<String, String> drafts,
                                   Map<String, String> errors) {
        this.expandedStepId = expandedStepId;
        this.drafts = Collections.unmodifiableMap(new LinkedHashMap<>(drafts));
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    public static SetProgressEditorState closed() {
        return new SetProgressEditorState(null, Collections.emptyMap(), Collections.emptyMap());
    }

    public boolean isExpanded(String stepId) {
        return stepId != null && stepId.equals(expandedStepId);
    }

    public String draft(String stepId, String fallback) {
        String value = drafts.get(stepId);
        return value == null ? fallback : value;
    }

    @Nullable public String error(String stepId) { return errors.get(stepId); }

    public SetProgressEditorState toggle(String stepId, String initialDraft) {
        if (isExpanded(stepId)) return new SetProgressEditorState(null, drafts, errors);
        Map<String, String> nextDrafts = new LinkedHashMap<>(drafts);
        nextDrafts.putIfAbsent(stepId, initialDraft == null ? "" : initialDraft);
        Map<String, String> nextErrors = new LinkedHashMap<>(errors);
        nextErrors.remove(stepId);
        return new SetProgressEditorState(stepId, nextDrafts, nextErrors);
    }

    public SetProgressEditorState withDraft(String stepId, String draft) {
        Map<String, String> nextDrafts = new LinkedHashMap<>(drafts);
        nextDrafts.put(stepId, draft == null ? "" : draft);
        Map<String, String> nextErrors = new LinkedHashMap<>(errors);
        nextErrors.remove(stepId);
        return new SetProgressEditorState(expandedStepId, nextDrafts, nextErrors);
    }

    public SetProgressEditorState withError(String stepId, @Nullable String error) {
        Map<String, String> nextErrors = new LinkedHashMap<>(errors);
        if (error == null || error.isEmpty()) nextErrors.remove(stepId);
        else nextErrors.put(stepId, error);
        return new SetProgressEditorState(expandedStepId, drafts, nextErrors);
    }

    public SetProgressEditorState closeIfMissing(Iterable<TaskSnapshot> tasks) {
        if (expandedStepId == null) return this;
        for (TaskSnapshot task : tasks)
            for (TaskStepUiModel step : task.steps)
                if (expandedStepId.equals(step.id)) return this;
        return new SetProgressEditorState(null, drafts, errors);
    }
}
