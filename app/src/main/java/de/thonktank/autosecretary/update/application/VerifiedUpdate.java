package de.thonktank.autosecretary.update.application;

import de.thonktank.autosecretary.update.domain.UpdateFailure;
import de.thonktank.autosecretary.update.domain.UpdateInfo;

import java.io.File;

/** An APK that passed the complete update contract and may reach the system installer. */
public final class VerifiedUpdate {
    public final UpdateInfo info;
    public final File apk;

    private VerifiedUpdate(UpdateInfo info, File apk) {
        this.info = info;
        this.apk = apk;
    }

    public static VerifiedUpdate fromVerifiedFile(UpdateInfo info, File apk)
            throws UpdateFailure {
        if (info == null || apk == null || !apk.isFile())
            throw new UpdateFailure(UpdateFailure.Kind.STORAGE,
                    "Verified update file is unavailable");
        return new VerifiedUpdate(info, apk);
    }
}
