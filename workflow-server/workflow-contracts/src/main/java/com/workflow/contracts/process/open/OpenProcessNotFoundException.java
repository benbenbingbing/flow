package com.workflow.contracts.process.open;

public class OpenProcessNotFoundException extends RuntimeException {

    public OpenProcessNotFoundException() {
        super("Process instance was not found");
    }
}
