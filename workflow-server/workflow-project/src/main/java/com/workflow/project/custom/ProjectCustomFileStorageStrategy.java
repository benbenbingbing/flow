package com.workflow.project.custom;

import com.workflow.core.logging.LogValue;
import com.workflow.storage.application.FileStorageStrategy;
import com.workflow.storage.application.StoredFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 文件存储策略扩展示例。
 *
 * <p>存储类型为 {@value #STORAGE_TYPE}。该实现只记录调用并抛出明确异常，
 * 不会伪造上传成功。只有在配置 {@code file.storage.type=PROJECT_LOG_ONLY}
 * 时才会被工厂选中。</p>
 */
@Slf4j
@Component
public class ProjectCustomFileStorageStrategy
        implements FileStorageStrategy {

    public static final String STORAGE_TYPE =
            "PROJECT_LOG_ONLY";

    @Override
    public Map<String, String> upload(
            MultipartFile file) {
        log.info(
                "项目日志型存储收到上传请求: storageType={}, originalFilename={}, size={}, contentType={}",
                STORAGE_TYPE,
                LogValue.safe(file == null
                        ? null
                        : file.getOriginalFilename()),
                file == null ? null : file.getSize(),
                LogValue.safe(file == null
                        ? null : file.getContentType()));
        throw unsupported();
    }

    @Override
    public boolean delete(String fileUrl) {
        log.info(
                "项目日志型存储收到删除请求: storageType={}, fileUrl={}",
                STORAGE_TYPE,
                LogValue.safe(fileUrl));
        throw unsupported();
    }

    @Override
    public StoredFile open(String fileUrl)
            throws IOException {
        log.info(
                "项目日志型存储收到读取请求: storageType={}, fileUrl={}",
                STORAGE_TYPE,
                LogValue.safe(fileUrl));
        throw unsupported();
    }

    @Override
    public String getAccessUrl(String filename) {
        log.info(
                "项目日志型存储收到访问地址请求: storageType={}, filename={}",
                STORAGE_TYPE,
                LogValue.safe(filename));
        throw unsupported();
    }

    @Override
    public String getStorageType() {
        return STORAGE_TYPE;
    }

    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException(
                "PROJECT_LOG_ONLY 仅用于验证存储扩展调用，未配置真实文件存储");
    }
}
