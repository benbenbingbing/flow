package com.workflow.storage.infrastructure.minio;

import com.workflow.storage.infrastructure.config.FileStorageProperties;
import com.workflow.storage.infrastructure.s3.S3FileStorageStrategy;
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * MinIO 文件存储策略，使用现有 S3 客户端完成上传、流式读取和删除。
 *
 * <p>保留 s3://bucket/key 文件标识格式，使指向同一 MinIO 桶的旧 s3 配置可以切换
 * 为 minio 配置而无需改写文件记录。文件访问仍由统一文件接口校验归属。</p>
 */
@Component
@ConditionalOnProperty(name = "file.storage.type", havingValue = "minio")
public class MinioFileStorageStrategy extends S3FileStorageStrategy {

    /**
     * 从独立的 MinIO 配置创建客户端；配置缺失或 endpoint 非对象 API 根地址时启动失败。
     */
    @Autowired
    public MinioFileStorageStrategy(FileStorageProperties properties) {
        this(properties.getMinio(), null);
    }

    MinioFileStorageStrategy(FileStorageProperties.MinioConfig config, S3Client client) {
        super(toS3Config(config), client);
    }

    @Override
    public String getStorageType() {
        return "minio";
    }

    /**
     * 校验后复制配置，避免误用默认 AWS endpoint、默认凭证链或修改原始配置对象。
     */
    private static FileStorageProperties.S3Config toS3Config(
            FileStorageProperties.MinioConfig config) {
        requireText(config.getEndpoint(), "endpoint");
        requireText(config.getBucket(), "bucket");
        requireText(config.getRegion(), "region");
        requireText(config.getAccessKey(), "access-key");
        requireText(config.getSecretKey(), "secret-key");
        validateEndpoint(config.getEndpoint());

        FileStorageProperties.S3Config s3 = new FileStorageProperties.S3Config();
        s3.setEndpoint(config.getEndpoint());
        s3.setRegion(config.getRegion());
        s3.setBucket(config.getBucket());
        s3.setAccessKey(config.getAccessKey());
        s3.setSecretKey(config.getSecretKey());
        s3.setAccessUrl(config.getAccessUrl());
        // 桶名放在 URL 路径中，支持 IP、容器服务名和未配置桶子域名的 MinIO 部署。
        s3.setPathStyleAccess(true);
        return s3;
    }

    private static void requireText(String value, String property) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("file.storage.minio." + property + " must be configured");
        }
    }

    /** endpoint 只接受 HTTP(S) 根地址；桶名由配置单独提供，凭证不允许放入 URL。 */
    private static void validateEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            if (("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    && StringUtils.hasText(uri.getHost())
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && (!StringUtils.hasText(uri.getPath()) || "/".equals(uri.getPath()))) {
                return;
            }
        } catch (IllegalArgumentException ignored) {
            // 不回显非法 URL，防止其中误填的凭证进入启动日志。
        }
        throw new IllegalStateException(
                "file.storage.minio.endpoint must be an HTTP(S) object API root URL");
    }
}
