package de.thonktank.autosecretary.update;

import java.util.function.IntConsumer;

public interface UpdateRepository {
    UpdateInfo check() throws Exception;
    VerifiedUpdate download(UpdateInfo update, IntConsumer progress) throws Exception;
}
