package de.thonktank.autosecretary;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable validation, prompt and persistence feedback. */
public final class TaskEditorFeedback {
    public final Set<ValidationIssue> issues;
    public final Set<EditorUiState.Page> attemptedPages;
    public final Set<String> attemptedStepIds;
    public final EditorUiState.Prompt prompt;
    public final String storageError;

    public TaskEditorFeedback(Set<ValidationIssue> issues,
                              Set<EditorUiState.Page> attemptedPages,
                              Set<String> attemptedStepIds, EditorUiState.Prompt prompt,
                              String storageError) {
        this.issues = Collections.unmodifiableSet(new LinkedHashSet<>(issues));
        this.attemptedPages = Collections.unmodifiableSet(new LinkedHashSet<>(attemptedPages));
        this.attemptedStepIds = Collections.unmodifiableSet(new LinkedHashSet<>(attemptedStepIds));
        this.prompt = prompt == null ? EditorUiState.Prompt.NONE : prompt;
        this.storageError = storageError == null ? "" : storageError;
    }

    Bundle toBundle() {
        Bundle bundle = new Bundle();
        ArrayList<Bundle> values = new ArrayList<>();
        for (ValidationIssue issue : issues) values.add(issue.toBundle());
        bundle.putParcelableArrayList("issues", values);
        ArrayList<String> pages = new ArrayList<>();
        for (EditorUiState.Page value : attemptedPages) pages.add(value.name());
        bundle.putStringArrayList("attempted_pages", pages);
        bundle.putStringArrayList("attempted_steps", new ArrayList<>(attemptedStepIds));
        bundle.putString("prompt", prompt.name());
        bundle.putString("storage_error", storageError);
        return bundle;
    }

    static TaskEditorFeedback fromBundle(Bundle bundle) {
        if (bundle == null) return empty();
        Set<ValidationIssue> issues = new LinkedHashSet<>();
        ArrayList<Bundle> values = bundle.getParcelableArrayList("issues");
        if (values != null) for (Bundle value : values) {
            ValidationIssue issue = ValidationIssue.fromBundle(value);
            if (issue != null) issues.add(issue);
        }
        Set<EditorUiState.Page> pages = new LinkedHashSet<>();
        ArrayList<String> pageValues = bundle.getStringArrayList("attempted_pages");
        if (pageValues != null) for (String value : pageValues)
            pages.add(BundleValues.enumValue(EditorUiState.Page.class, value,
                    EditorUiState.Page.TITLE));
        ArrayList<String> stepValues = bundle.getStringArrayList("attempted_steps");
        return new TaskEditorFeedback(issues, pages,
                stepValues == null ? Collections.emptySet() : new LinkedHashSet<>(stepValues),
                BundleValues.enumValue(EditorUiState.Prompt.class, bundle.getString("prompt"),
                        EditorUiState.Prompt.NONE), bundle.getString("storage_error", ""));
    }

    static TaskEditorFeedback empty() {
        return new TaskEditorFeedback(Collections.emptySet(), Collections.emptySet(),
                Collections.emptySet(), EditorUiState.Prompt.NONE, "");
    }
}
