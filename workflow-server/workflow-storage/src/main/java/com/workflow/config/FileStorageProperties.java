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
     * 存储类型：local
     */
    private String type = "local";

    /**
     * 本地存储配置
     */
    private LocalConfig local = new LocalConfig();

    /**
     * 本地存储相关配置。
     */
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
}
