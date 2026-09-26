package com.workflow.storage.application.model;

import java.io.IOException;
import java.io.InputStream;

/**
 * Stream and metadata returned by a storage backend.
 *
 * @param inputStream 输入流，保存在对象中供后续校验、查询或展示
 * @param filename {@code filename}，后续用于处理已存储文件时匹配或展示
 * @param contentType 内容类型标识，决定后续已存储文件采用的处理分支
 * @param contentLength 内容长度，保存在对象中供后续校验、查询或展示
 */
public record StoredFile(
        InputStream inputStream,
        String filename,
        String contentType,
        long contentLength) implements AutoCloseable {

    /**
     * 处理关闭，并将结果传给后续步骤。
     *
     * @throws IOException 读取或写入外部资源失败时抛出
     */
    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}
