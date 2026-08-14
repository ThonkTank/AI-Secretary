package com.autosecretary.ui;

/** Retained one-tap hand-off from a verified download to Android's package installer. */
final class UpdateInstallFlow {
    enum Action { NONE, OPEN_SETTINGS, OPEN_INSTALLER }

    private int pendingVersion;
    private int openedVersion;
    private boolean settingsOpened;

    UpdateInstallFlow(int pendingVersion, int openedVersion, boolean settingsOpened) {
        this.pendingVersion = pendingVersion;
        this.openedVersion = openedVersion;
        this.settingsOpened = settingsOpened;
    }

    void request(int version) {
        if (version < 1) throw new IllegalArgumentException("Update-Version fehlt");
        pendingVersion = version;
        openedVersion = 0;
        settingsOpened = false;
    }

    Action ready(int version, boolean canInstallPackages) {
        if (version != pendingVersion || version == openedVersion) return Action.NONE;
        if (!canInstallPackages) {
            if (settingsOpened) return Action.NONE;
            settingsOpened = true;
            return Action.OPEN_SETTINGS;
        }
        pendingVersion = 0;
        openedVersion = version;
        settingsOpened = false;
        return Action.OPEN_INSTALLER;
    }

    int pendingVersion() { return pendingVersion; }
    int openedVersion() { return openedVersion; }
    boolean settingsOpened() { return settingsOpened; }
}
