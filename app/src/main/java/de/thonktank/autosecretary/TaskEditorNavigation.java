package de.thonktank.autosecretary;

import android.os.Bundle;

/** Immutable wizard navigation state. */
public final class TaskEditorNavigation {
    public final EditorUiState.Page page;
    public final boolean returnToSummary;
    public final String expandedStepId;

    public TaskEditorNavigation(EditorUiState.Page page, boolean returnToSummary,
                                String expandedStepId) {
        this.page = page == null ? EditorUiState.Page.TITLE : page;
        this.returnToSummary = returnToSummary;
        this.expandedStepId = expandedStepId;
    }

    public TaskEditorNavigation withPage(EditorUiState.Page value, boolean returnValue) {
        return new TaskEditorNavigation(value, returnValue, expandedStepId);
    }

    public TaskEditorNavigation withExpandedStep(String id) {
        return new TaskEditorNavigation(page, returnToSummary, id);
    }

    Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString("page", page.name());
        bundle.putBoolean("return_summary", returnToSummary);
        bundle.putString("expanded", expandedStepId);
        return bundle;
    }

    static TaskEditorNavigation fromBundle(Bundle bundle, EditorUiState.Page fallback) {
        if (bundle == null) return new TaskEditorNavigation(fallback, false, null);
        return new TaskEditorNavigation(BundleValues.enumValue(EditorUiState.Page.class,
                bundle.getString("page"), fallback), bundle.getBoolean("return_summary"),
                bundle.getString("expanded"));
    }
}
