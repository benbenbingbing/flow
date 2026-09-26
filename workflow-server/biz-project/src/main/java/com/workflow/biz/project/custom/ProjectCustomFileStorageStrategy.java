package com.workflow.biz.project.custom;

import com.workflow.core.logging.LogValue;
import com.workflow.contracts.storage.spi.FileStorageProvider;
import com.workflow.contracts.storage.model.StoredFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.workflow.contracts.storage.model.FileUpload;

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
        implements FileStorageProvider {

    public static final String STORAGE_TYPE =
            "PROJECT_LOG_ONLY";

    /**
     * 记录上传调用并拒绝操作；该示例策略不保存文件，也不伪造上传成功。
     *
     * @param file 待上传文件，仅提取安全日志字段用于验证扩展调用
     * @return 此实现不会返回上传结果
     * @throws UnsupportedOperationException 始终抛出，因为未配置真实文件存储
     */
    @Override
    public Map<String, String> upload(
            FileUpload file) {
        log.info(
                "项目日志型存储收到上传请求: storageType={}, originalFilename={}, size={}, contentType={}",
                STORAGE_TYPE,
                LogValue.safe(file == null
                        ? null
                        : file.originalFilename()),
                file == null ? null : file.size(),
                LogValue.safe(file == null
                        ? null : file.contentType()));
        throw unsupported();
    }

    /**
     * 记录删除调用并拒绝操作，避免误报文件已删除。
     *
     * @param fileUrl 待删除文件地址，仅用于安全日志
     * @return 此实现不会返回删除结果
     * @throws UnsupportedOperationException 始终抛出，因为未配置真实文件存储
     */
    @Override
    public boolean delete(String fileUrl) {
        log.info(
                "项目日志型存储收到删除请求: storageType={}, fileUrl={}",
                STORAGE_TYPE,
                LogValue.safe(fileUrl));
        throw unsupported();
    }

    /**
     * 记录读取调用并拒绝操作；该示例策略没有实际文件内容。
     *
     * @param fileUrl 待读取文件地址，仅用于安全日志
     * @return 此实现不会返回文件内容
     * @throws IOException 接口契约保留的 I/O 异常声明；当前实现不访问文件
     * @throws UnsupportedOperationException 始终抛出，因为未配置真实文件存储
     */
    @Override
    public StoredFile open(String fileUrl)
            throws IOException {
        log.info(
                "项目日志型存储收到读取请求: storageType={}, fileUrl={}",
                STORAGE_TYPE,
                LogValue.safe(fileUrl));
        throw unsupported();
    }

    /**
     * 记录访问地址请求并拒绝操作，避免生成无法使用的文件链接。
     *
     * @param filename 文件名，仅用于安全日志
     * @return 此实现不会返回访问地址
     * @throws UnsupportedOperationException 始终抛出，因为未配置真实文件存储
     */
    @Override
    public String getAccessUrl(String filename) {
        log.info(
                "项目日志型存储收到访问地址请求: storageType={}, filename={}",
                STORAGE_TYPE,
                LogValue.safe(filename));
        throw unsupported();
    }

    /**
     * 读取存储类型；查询结果供调用方展示或继续处理。
     *
     * @return {@value #STORAGE_TYPE}，供存储策略工厂选择该示例实现
     */
    @Override
    public String getStorageType() {
        return STORAGE_TYPE;
    }

    /**
     * 构造明确的未实现异常，阻止调用方把日志记录误当作成功操作。
     *
     * @return 描述当前策略没有实际存储能力的异常
     */
    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException(
                "PROJECT_LOG_ONLY 仅用于验证存储扩展调用，未配置真实文件存储");
    }
}
