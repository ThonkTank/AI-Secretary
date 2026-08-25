package de.thonktank.autosecretary;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Atomic editor state: the complete draft/navigation model and ordered host work. */
public final class TaskEditorScreenState {
    public final EditorUiState content;
    public final List<TaskEditorRequest> requests;

    public TaskEditorScreenState(EditorUiState content, List<TaskEditorRequest> requests) {
        if (content == null || requests == null)
            throw new IllegalArgumentException("Complete editor screen state is required");
        this.content = content;
        this.requests = Collections.unmodifiableList(new ArrayList<>(requests));
    }

    TaskEditorScreenState withContent(EditorUiState value) {
        return new TaskEditorScreenState(value, requests);
    }

    TaskEditorScreenState enqueue(TaskEditorRequest value) {
        for (TaskEditorRequest request : requests) if (request.sameWorkAs(value)) return this;
        ArrayList<TaskEditorRequest> next = new ArrayList<>(requests);
        next.add(value);
        return new TaskEditorScreenState(content, next);
    }

    TaskEditorScreenState acknowledge(String id) {
        ArrayList<TaskEditorRequest> next = new ArrayList<>(requests.size());
        for (TaskEditorRequest request : requests) if (!request.id.equals(id)) next.add(request);
        return next.size() == requests.size() ? this : new TaskEditorScreenState(content, next);
    }

    @Nullable public TaskEditorRequest firstRequest() {
        return requests.isEmpty() ? null : requests.get(0);
    }
}
