package com.workflow.contracts.storage.model;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * 存储扩展的上传输入，隔离 Servlet/Spring MultipartFile。
 * 内容按需打开，不预读整份文件；仅在当前同步上传调用中有效，Provider 必须关闭其打开的流。
 * 同一次调用内须允许多次打开相同内容，供对象存储签名、校验和失败重试使用。
 * @param originalFilename 原始文件名，供存储后端生成对象名和返回元数据
 * @param contentType 媒体类型，可为空，由存储后端采用默认值
 * @param size 内容长度，用于流式上传和返回文件元数据
 * @param content 宿主提供的延迟读取入口，不能在请求结束后保存或异步调用
 */
public record FileUpload(String originalFilename, String contentType, long size, Content content) {
    public FileUpload {
        Objects.requireNonNull(content, "上传内容读取入口不能为空");
    }

    /** 从内容起点打开独立的新流；调用方负责关闭，读取失败以 IOException 传播。 */
    public InputStream openStream() throws IOException {
        return content.open();
    }

    /** 单次上传内可重复调用的 IO 回调；每次返回相同内容的新流，不属于可注册的业务扩展点。 */
    @FunctionalInterface
    public interface Content {
        InputStream open() throws IOException;
    }
}
