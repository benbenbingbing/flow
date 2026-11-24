package com.workflow.storage.application;

import com.workflow.contracts.storage.model.FileUpload;
import com.workflow.port.platform.StorageFileObjectPort;
import com.workflow.storage.infrastructure.persistence.mapper.StoredFileMapper.StoredFileDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/** 文件读取由调用方授权，上传沿用现有归属登记、幂等和回滚规则。 */
@Component
@RequiredArgsConstructor
public class StorageFileObjectAdapter implements StorageFileObjectPort {
    private final FileStorageFactory factory;
    private final StoredFileAccessService access;
    private final StoredFileUploadService uploads;

    @Override
    public FileMetadata upload(Upload upload, String idempotencyKey) {
        return uploadedMetadata(upload, uploads.store(toFile(upload), idempotencyKey));
    }

    @Override
    public FileMetadata upload(Upload upload, String idempotencyKey, String ownerUserId) {
        return uploadedMetadata(upload, uploads.storeForOwner(toFile(upload), idempotencyKey, ownerUserId));
    }

    private FileUpload toFile(Upload upload) {
        return new FileUpload(upload.originalName(), upload.contentType(), upload.size(), upload.content()::open);
    }

    private FileMetadata uploadedMetadata(Upload upload, Map<String, String> result) {
        return new FileMetadata(result.get("fileId"), result.get("originalName"), upload.contentType(), upload.size());
    }

    @Override
    public FileMetadata getMetadata(String fileId) {
        return metadata(fileId, access.requireRegisteredDetails(fileId));
    }

    @Override
    public OpenFile open(String fileId) throws IOException {
        var details = access.requireRegisteredDetails(fileId);
        var stored = factory.getStrategy().open(details.storageUrl());
        return new OpenFile(metadata(fileId, details), stored.inputStream());
    }

    private FileMetadata metadata(String fileId, StoredFileDetails details) {
        return new FileMetadata(fileId, details.originalName(), details.contentType(), details.contentLength());
    }
}
