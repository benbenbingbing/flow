package com.workflow.port.platform;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** 保存后台生成的文件，并通过平台文件 ID 查询或读取。 */
public interface StorageFileObjectPort {
    /**
     * 以当前操作者为归属账号登记文件。相同账号用同一个幂等键重试时必须提供相同内容。
     * 操作成功返回可写入实体附件字段的文件 ID。
     */
    FileMetadata upload(Upload upload, String idempotencyKey);

    /**
     * 为调用方已授权的归属账号上传，后台任务无需建立登录会话。
     * @param ownerUserId 文件归属账号，用于后续读取授权和幂等隔离
     */
    FileMetadata upload(Upload upload, String idempotencyKey, String ownerUserId);

    /** 读取已登记文件的元数据。调用方负责文件访问授权。 */
    FileMetadata getMetadata(String fileId);

    /** 按调用方已授权的文件 ID 打开内容，调用方须关闭返回的流。 */
    OpenFile open(String fileId) throws IOException;

    /** 保存期间可能多次读取同一内容；每次打开须返回从起点开始的独立流。 */
    record Upload(String originalName, String contentType, long size, Content content) {
        public Upload {
            Objects.requireNonNull(content, "上传内容不能为空");
            if (size <= 0) throw new IllegalArgumentException("文件大小必须大于零");
        }
    }

    @FunctionalInterface
    interface Content {
        InputStream open() throws IOException;
    }

    record FileMetadata(String fileId, String originalName, String contentType, long size) { }

    record OpenFile(FileMetadata metadata, InputStream stream) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            stream.close();
        }
    }
}
