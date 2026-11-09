package com.workflow.storage.infrastructure.config;

import java.util.ArrayList;
import java.util.List;
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
     * 所有 profile（包括 production）均按此配置选择策略，由部署者决定存储方式。
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
        /** 上传及删除的桶内根目录；为空时使用桶根，读取其他目录不受此值限制。 */
        private String keyPrefix = "";
        /** 额外允许下载的桶名，复用当前 endpoint 和凭证；不授予上传或删除权限。 */
        private List<String> readableBuckets = new ArrayList<>();
        private String accessKey;
        private String secretKey;
        /** 可选的对象访问前缀；为空时返回稳定的 s3://bucket/key 文件标识。 */
        private String accessUrl;
        /** 显式启用后跳过当前 MinIO 客户端的证书及主机名校验，存在中间人风险，默认关闭。 */
        private boolean trustAllCertificates;
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
        /** 上传及删除的桶内根目录；为空时使用桶根，读取其他目录不受此值限制。 */
        private String keyPrefix = "";
        /** 额外允许下载的桶名，默认桶始终可读；存储端凭证仍须具备目标对象读取权限。 */
        private List<String> readableBuckets = new ArrayList<>();
        private String accessKey;
        private String secretKey;
        private String accessUrl;
        private boolean pathStyleAccess;
        /** 显式启用后跳过当前 S3 客户端的证书及主机名校验，存在中间人风险，默认关闭。 */
        private boolean trustAllCertificates;
    }
}
