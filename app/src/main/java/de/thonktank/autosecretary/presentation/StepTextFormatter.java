package de.thonktank.autosecretary.presentation;

import androidx.annotation.NonNull;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.domain.model.StepAmount;

/** Produces localized, display-ready step metadata at the presentation boundary. */
public final class StepTextFormatter {
    private final UiTextProvider texts;

    public StepTextFormatter(UiTextProvider texts) {
        this.texts = texts;
    }

    public String format(@NonNull StepAmount stepAmount, @NonNull String note) {
        String amount = fullAmount(stepAmount);
        String cleanNote = note.trim();
        if (amount.isEmpty()) return cleanNote;
        if (cleanNote.isEmpty()) return amount;
        return texts.text(R.string.step_amount_note_summary, amount, cleanNote);
    }

    public String compactAmount(@NonNull StepAmount stepAmount) {
        if (stepAmount instanceof StepAmount.SetsReps) {
            StepAmount.SetsReps value = (StepAmount.SetsReps) stepAmount;
            return texts.text(R.string.step_amount_sets_reps_compact,
                    value.sets, value.repetitions);
        }
        return fullAmount(stepAmount);
    }

    private String fullAmount(@NonNull StepAmount stepAmount) {
        if (stepAmount instanceof StepAmount.SetsReps) {
            StepAmount.SetsReps value = (StepAmount.SetsReps) stepAmount;
            return texts.text(R.string.step_amount_sets_reps_summary,
                    value.sets, value.repetitions);
        }
        if (stepAmount instanceof StepAmount.Repetitions) {
            return texts.text(R.string.step_amount_reps_summary,
                    ((StepAmount.Repetitions) stepAmount).repetitions);
        }
        if (stepAmount instanceof StepAmount.Duration) {
            int seconds = ((StepAmount.Duration) stepAmount).seconds;
            return seconds >= 60 && seconds % 60 == 0
                    ? texts.text(R.string.step_amount_minutes_summary,
                            seconds / 60)
                    : texts.text(R.string.step_amount_seconds_summary, seconds);
        }
        return "";
    }
}
