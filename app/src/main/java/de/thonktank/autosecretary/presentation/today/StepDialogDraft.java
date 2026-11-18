package de.thonktank.autosecretary.presentation.today;

import android.os.Bundle;

/** The one editable step dialog shown by Today. */
public abstract class StepDialogDraft {
    public final String id;
    public final String error;
    public final boolean saving;

    StepDialogDraft(String id, String error, boolean saving) {
        this.id = id;
        this.error = error;
        this.saving = saving;
    }

    public abstract Bundle saved();
}
