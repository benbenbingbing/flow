package com.workflow.openapi.api.error;

import java.io.IOException;

/**
 * 表示打开载荷{@code too}{@code large}处理失败；调用方可据此区分错误并终止后续操作。
 */
public class OpenPayloadTooLargeException extends IOException {

    /**
     * 初始化打开载荷{@code too}{@code large}异常，保存构造参数供后续方法使用。
     */
    public OpenPayloadTooLargeException() {
        super("Open API request body exceeds 1 MiB");
    }
}
