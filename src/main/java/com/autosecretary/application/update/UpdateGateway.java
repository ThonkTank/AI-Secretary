package com.autosecretary.application.update;

public interface UpdateGateway {
    /** Returns null when the installed build is current. */
    UpdateInfo check();

    VerifiedUpdate downloadAndVerify(UpdateInfo update);
}
