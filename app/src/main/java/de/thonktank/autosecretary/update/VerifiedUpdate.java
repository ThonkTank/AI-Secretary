package de.thonktank.autosecretary.update;

import java.io.File;

public final class VerifiedUpdate {
    public final UpdateInfo info;
    public final File apk;

    public VerifiedUpdate(UpdateInfo info, File apk) {
        this.info = info;
        this.apk = apk;
    }
}
