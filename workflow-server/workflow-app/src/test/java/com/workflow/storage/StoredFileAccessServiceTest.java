package com.workflow.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.storage.application.FileUploadIdempotencyException;
import com.workflow.storage.application.StoredFileAccessService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mock.web.MockMultipartFile;

class StoredFileAccessServiceTest {

    private EmbeddedDatabase database;
    private StoredFileAccessService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("storage-" + UUID.randomUUID() + ";MODE=MySQL")
                .build();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.execute("""
                CREATE TABLE storage_file_object (
                  id VARCHAR(32) NOT NULL PRIMARY KEY,
                  storage_url VARCHAR(1024) NOT NULL,
                  storage_key VARCHAR(512) NOT NULL,
                  owner_user_id VARCHAR(64) NOT NULL,
                  idempotency_key VARCHAR(128),
                  request_hash CHAR(64),
                  original_name VARCHAR(512),
                  content_type VARCHAR(255),
                  content_length BIGINT NOT NULL,
                  deleted TINYINT NOT NULL DEFAULT 0,
                  create_time TIMESTAMP NOT NULL,
                  update_time TIMESTAMP NOT NULL,
                  CONSTRAINT uk_storage_file_url UNIQUE (storage_url),
                  CONSTRAINT uk_storage_file_owner_idempotency
                    UNIQUE (owner_user_id, idempotency_key)
                )
                """);
        service = new StoredFileAccessService(
                jdbcTemplate,
                mock(CurrentUserRoleService.class));
        UserContext.setCurrentUser("user-1", "tester");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        database.shutdown();
    }

    @Test
    void replaysCompletedUploadWithoutCreatingAnotherObject() {
        MockMultipartFile file = file("content");
        StoredFileAccessService.UploadClaim first =
                service.prepareUpload("upload-01", file);

        StoredFileAccessService.UploadRegistration registration =
                service.register(
                        stored("s3://files/first.txt"),
                        file,
                        first);
        StoredFileAccessService.UploadClaim replay =
                service.prepareUpload("upload-01", file);

        assertTrue(registration.currentObjectRegistered());
        assertNull(first.replay());
        assertNotNull(replay.replay());
        assertEquals(
                "s3://files/first.txt",
                replay.replay().get("url"));
    }

    @Test
    void resolvesConcurrentDuplicateToTheFirstRegisteredObject() {
        MockMultipartFile file = file("content");
        StoredFileAccessService.UploadClaim first =
                service.prepareUpload("upload-02", file);
        StoredFileAccessService.UploadClaim concurrent =
                service.prepareUpload("upload-02", file);
        service.register(
                stored("s3://files/first.txt"),
                file,
                first);

        StoredFileAccessService.UploadRegistration resolved =
                service.register(
                        stored("s3://files/concurrent.txt"),
                        file,
                        concurrent);

        assertFalse(resolved.currentObjectRegistered());
        assertEquals(
                "s3://files/first.txt",
                resolved.response().get("url"));
    }

    @Test
    void rejectsKeyReuseForDifferentFileContent() {
        MockMultipartFile firstFile = file("first");
        StoredFileAccessService.UploadClaim first =
                service.prepareUpload("upload-03", firstFile);
        service.register(
                stored("s3://files/first.txt"),
                firstFile,
                first);

        FileUploadIdempotencyException exception = assertThrows(
                FileUploadIdempotencyException.class,
                () -> service.prepareUpload(
                        "upload-03",
                        file("different")));

        assertEquals(409, exception.getResultCode());
    }

    @Test
    void rejectsInvalidIdempotencyKeyBeforeReadingFile() {
        FileUploadIdempotencyException exception = assertThrows(
                FileUploadIdempotencyException.class,
                () -> service.prepareUpload("contains space", file("data")));

        assertEquals(400, exception.getResultCode());
    }

    private MockMultipartFile file(String content) {
        return new MockMultipartFile(
                "file",
                "report.txt",
                "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> stored(String url) {
        return Map.of(
                "url", url,
                "filename", url.substring(url.lastIndexOf('/') + 1),
                "originalName", "report.txt",
                "size", "7");
    }
}
