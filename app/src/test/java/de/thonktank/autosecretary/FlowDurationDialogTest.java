package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class FlowDurationDialogTest {
    @Test public void durationParsingUsesHumanUnitsAndEnforcesThirtyDayLimit() {
        assertEquals(Long.valueOf(7_200_000L),
                FlowDurationDialog.parse("2", FlowDurationDialog.Unit.HOURS));
        assertEquals(FlowDurationDialog.Unit.DAYS,
                FlowDurationDialog.Unit.bestFor(86_400_000L));
        assertNull(FlowDurationDialog.parse("31", FlowDurationDialog.Unit.DAYS));
        assertNull(FlowDurationDialog.parse("nope", FlowDurationDialog.Unit.MINUTES));
    }
}
