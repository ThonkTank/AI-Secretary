package com.autosecretary.application.update;

/** Application-facing update port. Android and GitHub details stay behind this boundary. */
public interface UpdateRepository {
    UpdateInfo check();
    VerifiedUpdate downloadAndVerify(UpdateInfo update);
    void cleanup(long installedVersionCode);
}
