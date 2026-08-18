package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SetProgressEditorStateTest {
    @Test public void draftExpansionAndErrorAreImmutablePresentationState() {
        SetProgressEditorState closed = SetProgressEditorState.closed();
        SetProgressEditorState expanded = closed.toggle("step", "10");
        SetProgressEditorState drafted = expanded.withDraft("step", "10, 11");
        SetProgressEditorState invalid = drafted.withError("step", "Zu viele Sätze");

        assertFalse(closed.isExpanded("step"));
        assertTrue(expanded.isExpanded("step"));
        assertEquals("10", expanded.draft("step", ""));
        assertEquals("10, 11", invalid.draft("step", ""));
        assertEquals("Zu viele Sätze", invalid.error("step"));
        assertNull(drafted.error("step"));
        assertFalse(invalid.toggle("step", "ignored").isExpanded("step"));
    }
}
