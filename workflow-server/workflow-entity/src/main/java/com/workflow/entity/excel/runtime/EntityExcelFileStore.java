package com.workflow.entity.excel.runtime;

import com.workflow.contracts.storage.model.FileUpload;
import com.workflow.contracts.storage.model.StoredFile;
import com.workflow.storage.application.FileStorageFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import javax.crypto.spec.SecretKeySpec;

/** 使用既有存储后端保存任务文件；对外只暴露任务下载地址，不返回存储URL。 */
@Service
public class EntityExcelFileStore {
    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final FileStorageFactory storage;
    private final JdbcTemplate jdbc;
    private final ExcelPrivateFileCipher cipher;

    public EntityExcelFileStore(FileStorageFactory storage, JdbcTemplate jdbc,
            @Value("${entity.excel.file-secret:${entity.excel.action-secret:${ui.release-resolution.secret:${jwt.secret}}}}") String secret) {
        this.storage = storage;
        this.jdbc = jdbc;
        if (secret == null || secret.isBlank()) throw new IllegalArgumentException("Excel任务文件密钥不能为空");
        try {
            cipher = new ExcelPrivateFileCipher(new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(("entity-excel-private-file:v1:" + secret).getBytes(StandardCharsets.UTF_8)), "AES"));
        } catch (GeneralSecurityException failure) { throw new IllegalStateException("无法初始化Excel任务文件加密", failure); }
    }

    /** 注册所有者后返回不透明文件ID；任何登记失败都删除孤立对象。 */
    public String put(Path path, String name, String contentType, String owner) throws IOException {
        var provider = storage.getStrategy();
        // 现有存储后端可能提供静态URL。任务内容进入后端前加密，只有通过任务授权下载才解密。
        // 先生成有界内存密文临时文件，兼容要求精确长度或会重试读取的存储后端。
        Path encrypted = Files.createTempFile("workflow-excel-private-", ".bin");
        Map<String, String> uploaded;
        try {
            try (var input = Files.newInputStream(path); var output = Files.newOutputStream(encrypted)) {
                cipher.encrypt(input, output);
            }
            uploaded = provider.upload(new FileUpload("excel-private.bin", "application/octet-stream", Files.size(encrypted),
                    () -> Files.newInputStream(encrypted)));
        } finally { Files.deleteIfExists(encrypted); }
        String url = uploaded.get("url");
        if (url == null || uploaded.get("filename") == null) {
            if (url != null) provider.delete(url);
            throw new IllegalStateException("存储后端未返回文件身份");
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        try {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            jdbc.update("""
                    INSERT INTO storage_file_object(id,storage_url,storage_key,owner_user_id,original_name,
                      content_type,content_length,deleted,create_time,update_time) VALUES (?,?,?,?,?,?,?,0,?,?)
                    """, id, url, uploaded.get("filename"), owner, name, contentType, Files.size(path), now, now);
            return id;
        } catch (RuntimeException ex) { provider.delete(url); throw ex; }
    }

    /** 只能在服务端已经校验任务所有权和当前权限之后调用。 */
    public StoredFile open(String id) throws IOException {
        Map<String, Object> file = metadata(id);
        StoredFile opened = storage.getStrategy().open(String.valueOf(file.get("storage_url")));
        try {
            return new StoredFile(cipher.decryptStream(opened.inputStream()),
                    String.valueOf(file.get("original_name")), String.valueOf(file.get("content_type")), ((Number) file.get("content_length")).longValue());
        } catch (IOException | RuntimeException failure) { opened.close(); throw failure; }
    }

    public Map<String, Object> metadata(String id) {
        var files = jdbc.queryForList("SELECT * FROM storage_file_object WHERE id=? AND deleted=0", id);
        if (files.size() != 1) throw new IllegalArgumentException("任务文件不存在或已过期");
        return files.get(0);
    }

    /** 内部清理入口，供任务失败及到期回收使用；不接受浏览器直接指定的存储URL。 */
    public void delete(String id) {
        if (id == null) return;
        var files = jdbc.queryForList("SELECT storage_url FROM storage_file_object WHERE id=? AND deleted=0", id);
        if (files.isEmpty()) return;
        var provider = storage.getStrategy();
        String url = String.valueOf(files.get(0).get("storage_url"));
        if (!provider.delete(url)) {
            // 文件删除无法随数据库回滚；重试时对象可能已经不存在，确认不存在后视为幂等成功。
            try (var input = provider.open(url).inputStream()) {
                throw new IllegalStateException("Excel任务文件清理失败");
            } catch (java.io.FileNotFoundException | java.nio.file.NoSuchFileException alreadyDeleted) {
                // 只接受明确的对象不存在，网络错误等不能被误报为清理成功。
            } catch (IOException failure) {
                throw new IllegalStateException("Excel任务文件清理失败", failure);
            }
        }
        jdbc.update("UPDATE storage_file_object SET deleted=1,update_time=? WHERE id=?", LocalDateTime.now(ZoneOffset.UTC), id);
    }
}
