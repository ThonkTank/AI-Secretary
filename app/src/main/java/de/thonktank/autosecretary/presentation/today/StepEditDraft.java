package de.thonktank.autosecretary.presentation.today;

import android.os.Bundle;
import de.thonktank.autosecretary.EditorStepState;
import de.thonktank.autosecretary.domain.model.*;

/** Retained quick-edit draft. Saving is transient; restoration never retries a write. */
public final class StepEditDraft {
    public final String id, error;
    public final EditorStepState original, step;
    public final boolean once, prescriptionProtected, saving, validated;

    public StepEditDraft(String id, EditorStepState original, EditorStepState step,
                         boolean once, boolean prescriptionProtected,
                         String error, boolean saving, boolean validated) {
        this.id = id; this.original = original; this.step = step; this.once = once;
        this.prescriptionProtected = prescriptionProtected; this.error = error;
        this.saving = saving; this.validated = validated;
    }
    public static StepEditDraft from(EditableStep value) {
        EditorStepState step = EditorStepState.from(value.source)
                .withText(value.text).withNote(value.note);
        return new StepEditDraft(value.id, step, step, value.once,
                value.prescriptionProtected, "", false, false);
    }
    public boolean showCadence() {
        return !once && step.activationKind != StepActivationKind.FOLLOW_UP;
    }
    public StepEditDraft change(EditorStepState value) {
        return new StepEditDraft(id, original, value, once, prescriptionProtected, "", false, validated);
    }
    public StepEditDraft status(String error, boolean saving) {
        return new StepEditDraft(id, original, step, once, prescriptionProtected, error, saving, true);
    }
    public Bundle saved() {
        Bundle bundle = new Bundle();
        bundle.putString("id", id); bundle.putBundle("original", original.toBundle());
        bundle.putBundle("step", step.toBundle()); bundle.putBoolean("once", once);
        bundle.putBoolean("protected", prescriptionProtected);
        bundle.putBoolean("validated", validated); bundle.putString("error", error);
        return bundle;
    }
    public static StepEditDraft restore(Bundle bundle) {
        if (bundle == null || bundle.getString("id") == null) return null;
        Bundle original = bundle.getBundle("original"), step = bundle.getBundle("step");
        if (original == null || step == null) return null;
        return new StepEditDraft(bundle.getString("id"), EditorStepState.fromBundle(original),
                EditorStepState.fromBundle(step), bundle.getBoolean("once"),
                bundle.getBoolean("protected"), bundle.getString("error", ""), false,
                bundle.getBoolean("validated"));
    }
}
