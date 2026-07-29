package com.workflow.openapi.web;

import java.io.IOException;

public class OpenPayloadTooLargeException extends IOException {

    public OpenPayloadTooLargeException() {
        super("Open API request body exceeds 1 MiB");
    }
}
