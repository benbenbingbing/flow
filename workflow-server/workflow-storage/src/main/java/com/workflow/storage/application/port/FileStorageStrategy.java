package com.workflow.storage.application.port;

import com.workflow.storage.application.model.StoredFile;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 文件存储策略接口
 * 内置 local、s3 和 minio 实现，由 file.storage.type 选择。
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
     * Open a stored object for streaming.
     *
     * @param fileUrl 文件URL，供本方法处理打开时使用
     * @return 处理后的打开结果，供调用方继续处理
     * @throws IOException 读取或写入外部资源失败时抛出
     */
    StoredFile open(String fileUrl) throws IOException;

    /**
     * 获取文件的访问URL
     *
     * @param filename 存储的文件名
     * @return 完整的访问URL
     */
    String getAccessUrl(String filename);

    /**
     * 获取存储类型标识
     *
     * @return 读取后的存储类型文本，供调用方比较或展示
     */
    String getStorageType();
}
