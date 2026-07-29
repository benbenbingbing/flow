package com.workflow.storage.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "file.storage")
public class FileStorageProperties {

    /**
     * 存储类型：local
     */
    private String type = "local";

    /**
     * 本地存储配置
     */
    private LocalConfig local = new LocalConfig();

    private S3Config s3 = new S3Config();

    /**
     * 本地存储相关配置。
     */
    @Data
    public static class LocalConfig {
        /**
         * 上传文件保存路径
         */
        private String path = "./uploads";

        /**
         * 文件访问URL前缀
         */
        private String accessUrl = "/uploads";
    }

    /**
     * S3-compatible shared object storage configuration.
     */
    @Data
    public static class S3Config {
        private String endpoint;
        private String region = "us-east-1";
        private String bucket;
        private String accessKey;
        private String secretKey;
        private String accessUrl;
        private boolean pathStyleAccess;
    }
}
