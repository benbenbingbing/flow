package com.workflow.entity.excel.runtime;

import com.workflow.contracts.storage.model.FileUpload;
import com.workflow.contracts.storage.model.StoredFile;
import com.workflow.contracts.storage.spi.FileStorageProvider;
import com.workflow.storage.application.FileStorageFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实数据库登记和真实加密流验证私有任务文件，不依赖存储URL不可猜测这一假设。 */
class EntityExcelFileStoreTest {
    @TempDir Path directory;
    private final FileStorageFactory factory = mock(FileStorageFactory.class);
    private final FileStorageProvider provider = mock(FileStorageProvider.class);
    private EntityExcelFileStore store;
    private JdbcTemplate jdbc;
    private byte[] stored;

    @BeforeEach void setup() throws IOException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:files_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE storage_file_object(id VARCHAR(64) PRIMARY KEY,storage_url VARCHAR(1024),storage_key VARCHAR(512),owner_user_id VARCHAR(64),original_name VARCHAR(255),content_type VARCHAR(150),content_length BIGINT,deleted INT,create_time TIMESTAMP,update_time TIMESTAMP)");
        when(factory.getStrategy()).thenReturn(provider);
        when(provider.upload(any())).thenAnswer(call -> {
            FileUpload upload = call.getArgument(0);
            try (var input = upload.openStream()) { stored = input.readAllBytes(); }
            assertThat(stored.length).isEqualTo(upload.size());
            assertThat(upload.originalFilename()).isEqualTo("excel-private.bin");
            return Map.of("url", "/uploads/opaque.bin", "filename", "opaque.bin");
        });
        when(provider.open(anyString())).thenAnswer(call -> new StoredFile(new ByteArrayInputStream(stored), "opaque.bin", "application/octet-stream", stored.length));
        store = new EntityExcelFileStore(factory, jdbc, "test-file-secret");
    }

    @Test void backendObjectIsEncryptedAndAuthorizedDownloadRestoresBytesAndMetadata() throws Exception {
        byte[] original = "姓名,身份证号\n张三,001234567890123456".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path source = directory.resolve("input.xlsx"); Files.write(source, original);
        String id = store.put(source, "员工.xlsx", EntityExcelFileStore.XLSX, "alice");
        assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain(new String(original, java.nio.charset.StandardCharsets.ISO_8859_1));
        assertThat(new String(stored, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("WEX2");
        try (var result = store.open(id)) {
            assertThat(result.filename()).isEqualTo("员工.xlsx");
            assertThat(result.contentType()).isEqualTo(EntityExcelFileStore.XLSX);
            assertThat(result.contentLength()).isEqualTo(original.length);
            assertThat(result.inputStream().readAllBytes()).isEqualTo(original);
        }
    }

    @Test void tamperedObjectCannotBeDownloadedAsPlaintext() throws Exception {
        Path source = directory.resolve("input.xlsx"); Files.writeString(source, "private data");
        String id = store.put(source, "员工.xlsx", EntityExcelFileStore.XLSX, "alice");
        stored[stored.length - 1] ^= 1;
        assertThatThrownBy(() -> { try (var result = store.open(id)) { result.inputStream().readAllBytes(); } })
                .isInstanceOf(IOException.class);
    }

    @Test void cleanupIsIdempotentAfterPhysicalObjectWasDeletedBeforeDatabaseRollback() throws Exception {
        Path source = directory.resolve("input.xlsx"); Files.writeString(source, "private data");
        String id = store.put(source, "员工.xlsx", EntityExcelFileStore.XLSX, "alice");
        when(provider.delete(anyString())).thenReturn(false);
        when(provider.open(anyString())).thenThrow(new FileNotFoundException("deleted already"));
        store.delete(id);
        store.delete(id);
        assertThat(jdbc.queryForObject("SELECT deleted FROM storage_file_object WHERE id=?", Integer.class, id)).isEqualTo(1);
        verify(provider, times(1)).delete(anyString());
    }
}
