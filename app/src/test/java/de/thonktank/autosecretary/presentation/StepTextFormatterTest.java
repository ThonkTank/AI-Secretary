package de.thonktank.autosecretary.presentation;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.domain.model.StepAmount;

public final class StepTextFormatterTest {
    private final StepTextFormatter formatter = new StepTextFormatter((resource, arguments) -> {
        if (resource == R.string.step_amount_sets_reps_summary)
            return arguments[0] + " × " + arguments[1] + " Wdh.";
        if (resource == R.string.step_amount_reps_summary)
            return arguments[0] + " Wdh.";
        if (resource == R.string.step_amount_minutes_summary)
            return arguments[0] + " Min.";
        if (resource == R.string.step_amount_seconds_summary)
            return arguments[0] + " Sek.";
        if (resource == R.string.step_amount_note_summary)
            return arguments[0] + " · " + arguments[1];
        throw new AssertionError("Unexpected resource " + resource);
    });

    @Test public void formatsEveryAmountShapeAndTrimmedNotes() {
        assertEquals("3 × 12 Wdh. · 23 kg", formatter.format(
                StepAmount.setsReps(3, 12), " 23 kg "));
        assertEquals("20 Wdh.", formatter.format(
                StepAmount.repetitions(20), ""));
        assertEquals("2 Min. · Bauch fest", formatter.format(
                StepAmount.duration(120), "Bauch fest"));
        assertEquals("45 Sek.", formatter.format(
                StepAmount.duration(45), ""));
        assertEquals("ruhig atmen", formatter.format(
                StepAmount.none(), " ruhig atmen "));
    }
}
