package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import de.thonktank.autosecretary.domain.model.RestTimerPolicy;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;

public final class RestTimerPolicyTest {
    @Test public void setsInheritGlobalDefaultWhileCustomAndOffRemainExplicit() {
        assertEquals(60, RestTimerPolicy.inherit().effectiveSeconds(60));
        assertEquals(75, RestTimerPolicy.custom(75).effectiveSeconds(60));
        assertEquals(0, RestTimerPolicy.off().effectiveSeconds(60));
    }

    @Test public void newSetsInheritAndOtherAmountsRejectRestTimers() {
        TaskStepDefinition sets = de.thonktank.autosecretary.testing.StepTestFixtures.definition("sets", 0, "Liegestütze", 0, 0,
                StepAmount.setsReps(3, 12), "");
        TaskStepDefinition duration = de.thonktank.autosecretary.testing.StepTestFixtures.definition("run", 1, "Laufen", 0, 0,
                StepAmount.duration(600), "");

        assertEquals(RestTimerPolicy.Mode.INHERIT, sets.restTimerPolicy.mode);
        assertEquals(RestTimerPolicy.Mode.OFF, duration.restTimerPolicy.mode);
    }
}
