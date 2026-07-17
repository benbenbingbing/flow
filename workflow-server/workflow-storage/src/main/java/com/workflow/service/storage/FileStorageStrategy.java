package com.workflow.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件存储策略接口
 * 当前内置本地存储实现，其他存储后端需提供完整策略后再启用。
 */
public interface FileStorageStrategy {

    /**
     * 上传文件
     *
     * @param file 文件
     * @return 文件信息（包含 url、filename、originalName、size 等）
     */
    Map<String, String> upload(MultipartFile file);

    /**
     * 删除文件
     *
     * @param fileUrl 文件访问URL
     * @return 是否删除成功
     */
    boolean delete(String fileUrl);

    /**
     * 获取文件的访问URL
     *
     * @param filename 存储的文件名
     * @return 完整的访问URL
     */
    String getAccessUrl(String filename);

    /**
     * 获取存储类型标识
     */
    String getStorageType();
}
