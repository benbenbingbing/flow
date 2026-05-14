package com.workflow.service.storage;

import com.workflow.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * MinIO 对象存储策略
 * 框架预留，具体实现可后续补充
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioFileStorageStrategy implements FileStorageStrategy {

    private final FileStorageProperties properties;

    @Override
    public Map<String, String> upload(MultipartFile file) {
        // TODO: 接入 MinIO SDK 实现文件上传
        log.warn("MinIO 存储策略尚未实现，请补充相关代码");
        throw new UnsupportedOperationException("MinIO 存储策略尚未实现");
    }

    @Override
    public boolean delete(String fileUrl) {
        // TODO: 接入 MinIO SDK 实现文件删除
        log.warn("MinIO 存储策略尚未实现，请补充相关代码");
        throw new UnsupportedOperationException("MinIO 存储策略尚未实现");
    }

    @Override
    public String getAccessUrl(String filename) {
        FileStorageProperties.MinioConfig minio = properties.getMinio();
        return minio.getEndpoint() + "/" + minio.getBucketName() + "/" + filename;
    }

    @Override
    public String getStorageType() {
        return "minio";
    }
}
