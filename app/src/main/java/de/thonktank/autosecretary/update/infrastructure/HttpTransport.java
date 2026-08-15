package de.thonktank.autosecretary.update.infrastructure;

import de.thonktank.autosecretary.update.domain.UpdateFailure;

import java.io.File;
import java.util.function.IntConsumer;

public interface HttpTransport {
    byte[] get(String url, int maxBytes) throws UpdateFailure;
    void download(String url, File destination, long expectedBytes, long maxBytes,
                  IntConsumer progress) throws UpdateFailure;
}
