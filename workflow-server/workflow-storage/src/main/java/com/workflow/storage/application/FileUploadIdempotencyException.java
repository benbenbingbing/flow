package com.workflow.storage.application;

/**
 * Client-visible validation or conflict raised by upload idempotency.
 */
public class FileUploadIdempotencyException extends RuntimeException {

    private final int resultCode;

    public FileUploadIdempotencyException(
            int resultCode,
            String message) {
        super(message);
        this.resultCode = resultCode;
    }

    public int getResultCode() {
        return resultCode;
    }
}
