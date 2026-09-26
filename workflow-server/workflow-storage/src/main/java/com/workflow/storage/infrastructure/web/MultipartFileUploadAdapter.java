package com.workflow.storage.infrastructure.web;

import com.workflow.contracts.storage.model.FileUpload;
import org.springframework.web.multipart.MultipartFile;

/** HTTP 上传到存储契约的适配器；延迟打开内容，保留幂等重放时不读取文件的行为。 */
public final class MultipartFileUploadAdapter {
    private MultipartFileUploadAdapter() { }

    /** 输入必须在当前请求内消费；内容流由实际上传的 Provider 打开并关闭。 */
    public static FileUpload from(MultipartFile file) {
        return new FileUpload(file.getOriginalFilename(), file.getContentType(),
                file.getSize(), file::getInputStream);
    }
}
