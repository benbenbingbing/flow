package com.workflow.contracts.process.open;

public class OpenProcessStateConflictException extends RuntimeException {

    public OpenProcessStateConflictException(String message) {
        super(message);
    }
}
