package com.workflow.storage.application;

import java.io.IOException;
import java.io.InputStream;

/**
 * Stream and metadata returned by a storage backend.
 */
public record StoredFile(
        InputStream inputStream,
        String filename,
        String contentType,
        long contentLength) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}
