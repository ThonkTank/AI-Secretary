package de.thonktank.autosecretary.presentation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.domain.model.StepAmountKind;

/** Produces localized, display-ready step metadata at the presentation boundary. */
public final class StepTextFormatter {
    private final UiTextProvider texts;

    public StepTextFormatter(UiTextProvider texts) {
        this.texts = texts;
    }

    public String format(@NonNull StepAmountKind kind, @Nullable Integer plannedSets,
                         @Nullable Integer plannedRepetitions,
                         @Nullable Integer plannedDurationSeconds,
                         @NonNull String note) {
        String amount = "";
        if (kind == StepAmountKind.SETS_REPS
                && plannedSets != null && plannedRepetitions != null) {
            amount = texts.text(R.string.step_amount_sets_reps_summary,
                    plannedSets, plannedRepetitions);
        } else if (kind == StepAmountKind.REPS && plannedRepetitions != null) {
            amount = texts.text(R.string.step_amount_reps_summary, plannedRepetitions);
        } else if (kind == StepAmountKind.DURATION && plannedDurationSeconds != null) {
            amount = plannedDurationSeconds >= 60 && plannedDurationSeconds % 60 == 0
                    ? texts.text(R.string.step_amount_minutes_summary,
                            plannedDurationSeconds / 60)
                    : texts.text(R.string.step_amount_seconds_summary,
                            plannedDurationSeconds);
        }
        String cleanNote = note.trim();
        if (amount.isEmpty()) return cleanNote;
        if (cleanNote.isEmpty()) return amount;
        return texts.text(R.string.step_amount_note_summary, amount, cleanNote);
    }
}
