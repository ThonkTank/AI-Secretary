package com.autosecretary.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class UpdateInstallFlowTest {
    @Test
    public void explicitTapOpensInstallerAsSoonAsDownloadIsVerified() {
        UpdateInstallFlow flow = new UpdateInstallFlow(0, 0, false);

        flow.request(2001001);

        assertEquals(UpdateInstallFlow.Action.OPEN_INSTALLER,
                flow.ready(2001001, true));
        assertEquals(UpdateInstallFlow.Action.NONE, flow.ready(2001001, true));
    }

    @Test
    public void unknownSourcePermissionOpensSettingsOnceThenContinues() {
        UpdateInstallFlow flow = new UpdateInstallFlow(0, 0, false);
        flow.request(2001001);

        assertEquals(UpdateInstallFlow.Action.OPEN_SETTINGS,
                flow.ready(2001001, false));
        assertEquals(UpdateInstallFlow.Action.NONE,
                flow.ready(2001001, false));
        assertEquals(UpdateInstallFlow.Action.OPEN_INSTALLER,
                flow.ready(2001001, true));
    }

    @Test
    public void restoredStateDoesNotOpenTheSameInstallerTwice() {
        UpdateInstallFlow original = new UpdateInstallFlow(0, 0, false);
        original.request(2001001);
        assertEquals(UpdateInstallFlow.Action.OPEN_INSTALLER,
                original.ready(2001001, true));

        UpdateInstallFlow restored = new UpdateInstallFlow(original.pendingVersion(),
                original.openedVersion(), original.settingsOpened());

        assertEquals(UpdateInstallFlow.Action.NONE,
                restored.ready(2001001, true));
    }

    @Test
    public void userCanExplicitlyReopenAPreviouslyCancelledInstaller() {
        UpdateInstallFlow flow = new UpdateInstallFlow(0, 0, false);
        flow.request(2001001);
        flow.ready(2001001, true);

        flow.request(2001001);

        assertEquals(UpdateInstallFlow.Action.OPEN_INSTALLER,
                flow.ready(2001001, true));
    }
}
