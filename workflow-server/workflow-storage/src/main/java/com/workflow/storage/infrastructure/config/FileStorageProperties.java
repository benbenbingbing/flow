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
     * 存储类型：local、s3、minio，也可使用已注册的业务扩展策略标识。
     */
    private String type = "local";

    /**
     * 本地存储配置
     */
    private LocalConfig local = new LocalConfig();

    private S3Config s3 = new S3Config();

    private MinioConfig minio = new MinioConfig();

    /**
     * MinIO 独立配置；通过 S3 协议访问，固定使用路径式桶寻址。
     */
    @Data
    public static class MinioConfig {
        /** MinIO 对象 API 地址（通常为 9000 端口），不是控制台地址。 */
        private String endpoint;
        /** 用于请求签名，需与服务端区域一致。 */
        private String region = "us-east-1";
        /** 已创建的存储桶；应用不会自动建桶或修改桶的访问策略。 */
        private String bucket;
        private String accessKey;
        private String secretKey;
        /** 可选的对象访问前缀；为空时返回稳定的 s3://bucket/key 文件标识。 */
        private String accessUrl;
    }

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
