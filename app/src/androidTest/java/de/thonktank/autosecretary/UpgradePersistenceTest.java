package de.thonktank.autosecretary;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assume.assumeTrue;

/** Ordinary test-suite adapter for the release-safe cross-installation probe. */
@RunWith(AndroidJUnit4.class)
public final class UpgradePersistenceTest {
    @Test public void seedPreviousVersion() throws Exception {
        requirePhase("seed");
        UpgradePersistenceProbe.seed(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation());
    }

    @Test public void currentVersionStartsAndReadsPreviousData() throws Exception {
        requirePhase("verify");
        UpgradePersistenceProbe.verify(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation());
    }

    private static void requirePhase(String expected) {
        assumeTrue("Only the cross-installation CI probe runs this test",
                expected.equals(InstrumentationRegistry.getArguments().getString("upgradePhase")));
    }
}
