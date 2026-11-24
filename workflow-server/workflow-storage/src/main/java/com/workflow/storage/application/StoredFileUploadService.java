package com.workflow.storage.application;

import com.workflow.contracts.storage.model.FileUpload;
import com.workflow.contracts.storage.spi.FileStorageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/** 在各入口完成授权后统一上传、登记和清理后端对象。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoredFileUploadService {
    private final FileStorageFactory factory;
    private final StoredFileAccessService access;

    /** 登记与调用方事务一并提交，登记失败或外层回滚时清理本次写入的存储对象。 */
    public Map<String, String> store(FileUpload file, String idempotencyKey) {
        return store(file, access.prepareUpload(idempotencyKey, file));
    }

    /** Port 调用方已确认归属账号，用相同流程登记和清理存储对象。 */
    Map<String, String> storeForOwner(FileUpload file, String idempotencyKey, String ownerUserId) {
        return store(file, access.prepareUploadForOwner(idempotencyKey, file, ownerUserId));
    }

    private Map<String, String> store(FileUpload file, StoredFileAccessService.UploadClaim claim) {
        if (claim.replay() != null) return claim.replay();

        var strategy = factory.getStrategy();
        var stored = strategy.upload(file);
        try {
            var registration = access.register(stored, file, claim);
            if (!registration.currentObjectRegistered()) {
                deleteUnregistered(strategy, stored.get("url"));
            } else if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                // 存储后端不参与数据库事务，外层业务回滚后不能留下无归属的对象。
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) deleteUnregistered(strategy, stored.get("url"));
                    }
                });
            }
            return registration.response();
        } catch (RuntimeException failure) {
            deleteUnregistered(strategy, stored.get("url"));
            throw failure;
        }
    }

    private void deleteUnregistered(FileStorageProvider strategy, String url) {
        try {
            if (!strategy.delete(url)) log.error("未登记的文件对象清理未成功");
        } catch (RuntimeException failure) {
            log.error("未登记的文件对象清理失败", failure);
        }
    }
}
