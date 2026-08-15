package de.thonktank.autosecretary.update.infrastructure;

import de.thonktank.autosecretary.update.application.UpdateRepository;
import de.thonktank.autosecretary.update.application.VerifiedUpdate;
import de.thonktank.autosecretary.update.domain.UpdateCheckResult;
import de.thonktank.autosecretary.update.domain.UpdateFailure;
import de.thonktank.autosecretary.update.domain.UpdateInfo;

import java.util.function.IntConsumer;

/** Network-free repository for development and test application variants. */
public final class DisabledUpdateRepository implements UpdateRepository {
    @Override public UpdateCheckResult check() {
        return UpdateCheckResult.current();
    }

    @Override public VerifiedUpdate download(UpdateInfo update, IntConsumer progress)
            throws UpdateFailure {
        throw new UpdateFailure(UpdateFailure.Kind.CANCELLED,
                "Remote updates are disabled in this application environment");
    }
}
