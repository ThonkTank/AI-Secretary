package de.thonktank.autosecretary;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GoldenAssertionsTest {
    @Test public void baselineUpdatePolicyIsExplicitlyDisabledOnCi() {
        assertTrue(GoldenAssertions.baselineUpdatesAllowed(true, false));
        assertFalse(GoldenAssertions.baselineUpdatesAllowed(true, true));
        assertFalse(GoldenAssertions.baselineUpdatesAllowed(false, false));
    }
}
