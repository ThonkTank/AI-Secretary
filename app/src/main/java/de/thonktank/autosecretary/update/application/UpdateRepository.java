package de.thonktank.autosecretary.update.application;

import de.thonktank.autosecretary.update.domain.UpdateCheckResult;
import de.thonktank.autosecretary.update.domain.UpdateFailure;
import de.thonktank.autosecretary.update.domain.UpdateInfo;

import java.util.function.IntConsumer;

/** Application port for checking and downloading updates. */
public interface UpdateRepository {
    UpdateCheckResult check() throws UpdateFailure;
    VerifiedUpdate download(UpdateInfo update, IntConsumer progress) throws UpdateFailure;
}
