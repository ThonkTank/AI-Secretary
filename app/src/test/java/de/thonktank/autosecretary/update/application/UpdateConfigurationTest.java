package de.thonktank.autosecretary.update.application;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class UpdateConfigurationTest {
    @Test public void productionAloneEnablesRemoteAndAutomaticChecks() {
        UpdateConfiguration production = UpdateConfiguration.production(
                "owner", "repository", "metadata.json", "app.apk", "tag-");
        UpdateConfiguration development = UpdateConfiguration.development(
                "owner", "repository", "metadata.json", "app.apk", "tag-");
        UpdateConfiguration test = UpdateConfiguration.test(
                "owner", "repository", "metadata.json", "app.apk", "tag-");

        assertTrue(production.remoteChecksEnabled);
        assertTrue(production.automaticChecksEnabled);
        assertFalse(development.remoteChecksEnabled);
        assertFalse(development.automaticChecksEnabled);
        assertFalse(test.remoteChecksEnabled);
        assertFalse(test.automaticChecksEnabled);
    }
}
