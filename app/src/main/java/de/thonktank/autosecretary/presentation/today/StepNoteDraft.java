package de.thonktank.autosecretary.presentation.today;

import android.os.Bundle;

/** Immutable note dialog state; only the saved-state boundary uses a Bundle. */
public final class StepNoteDraft extends StepDialogDraft {
    public final String text;

    public StepNoteDraft(String id, String text, String error, boolean saving) {
        super(id, error, saving);
        this.text = text;
    }
    @Override public Bundle saved() {
        Bundle value = new Bundle();
        value.putString("id", id); value.putString("text", text);
        value.putString("error", error);
        return value;
    }
    public static StepNoteDraft restore(Bundle value) {
        return value == null || value.getString("id") == null ? null
                : new StepNoteDraft(value.getString("id"), value.getString("text", ""),
                value.getString("error", ""), false);
    }
}
