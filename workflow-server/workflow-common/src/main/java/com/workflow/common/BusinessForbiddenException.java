package com.workflow.common;

import lombok.Getter;

@Getter
public class BusinessForbiddenException extends ForbiddenException {

    private final String errorCode;

    public BusinessForbiddenException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
