package com.workflow.config;

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
     * 存储类型：local / minio
     */
    private String type = "local";

    /**
     * 本地存储配置
     */
    private LocalConfig local = new LocalConfig();

    /**
     * MinIO 存储配置
     */
    private MinioConfig minio = new MinioConfig();

    @Data
    public static class LocalConfig {
        /**
         * 上传文件保存路径
         */
        private String path = "/Users/dawei/Documents/ddup/ai/flow/uploads";

        /**
         * 文件访问URL前缀
         */
        private String accessUrl = "/Users/dawei/Documents/ddup/ai/flow/uploads";
    }

    @Data
    public static class MinioConfig {
        /**
         * MinIO 服务端点
         */
        private String endpoint;

        /**
         * 访问密钥
         */
        private String accessKey;

        /**
         * 秘密密钥
         */
        private String secretKey;

        /**
         * 存储桶名称
         */
        private String bucketName;
    }
}
