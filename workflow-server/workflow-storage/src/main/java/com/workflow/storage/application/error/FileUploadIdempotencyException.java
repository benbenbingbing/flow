package com.workflow.storage.application.error;

/**
 * Client-visible validation or conflict raised by upload idempotency.
 */
public class FileUploadIdempotencyException extends RuntimeException {

    private final int resultCode;

    /**
     * 初始化文件上传幂等异常，保存构造参数供后续方法使用。
     *
     * @param resultCode 结果编码依赖，保存到当前对象供后续业务方法调用
     * @param message 消息，保存在对象中供后续校验、查询或展示
     */
    public FileUploadIdempotencyException(
            int resultCode,
            String message) {
        super(message);
        this.resultCode = resultCode;
    }

    /**
     * 读取结果编码；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的文件上传幂等异常结果，供调用方继续处理
     */
    public int getResultCode() {
        return resultCode;
    }
}
