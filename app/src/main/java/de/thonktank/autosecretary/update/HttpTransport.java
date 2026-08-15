package de.thonktank.autosecretary.update;

import java.io.File;
import java.util.function.IntConsumer;

interface HttpTransport {
    byte[] get(String url, int maxBytes) throws Exception;
    void download(String url, File destination, long expectedBytes, long maxBytes,
                  IntConsumer progress) throws Exception;
}
