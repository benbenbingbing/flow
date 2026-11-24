package com.workflow.storage;

import com.workflow.port.system.model.identity.CurrentActor;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.core.database.jdbc.JdbcWriteAttempt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;

import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.storage.model.StoredFile;
import com.workflow.contracts.storage.spi.FileStorageProvider;
import com.workflow.port.platform.StorageFileObjectPort;
import com.workflow.port.system.CurrentActorPort;
import com.workflow.core.error.ForbiddenException;
import com.workflow.storage.application.FileStorageFactory;
import com.workflow.storage.application.StorageFileObjectAdapter;
import com.workflow.storage.application.StoredFileUploadService;
import com.workflow.storage.application.error.FileUploadIdempotencyException;
import com.workflow.storage.application.StoredFileAccessService;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mock.web.MockMultipartFile;

class StoredFileAccessServiceTest {

    private EmbeddedDatabase database;
    private StoredFileAccessService service;
    private CurrentActorPort authorization;

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
        // 本类保留原有 H2 业务契约夹具；MySQL 保存点和读守卫另有实库测试。
        var errors = mock(com.workflow.integration.database.api.write.DatabaseInsertDialect.class);
        org.mockito.Mockito.when(errors.isUniqueViolation("23505", 23505)).thenReturn(true);
        var queries = mock(com.workflow.integration.database.api.query.DatabaseQueryDialect.class);
        org.mockito.Mockito.when(queries.readGuardClause()).thenReturn(" FOR UPDATE");
        authorization = mock(CurrentActorPort.class);
        org.mockito.Mockito.when(authorization.current()).thenAnswer(call -> new CurrentActor(UserContext.getUserId(), UserContext.getUsername()));
        service = new StoredFileAccessService(
                com.workflow.support.BusinessMapperTestSupport.mapper(jdbcTemplate.getDataSource(), com.workflow.storage.infrastructure.persistence.mapper.StoredFileMapper.class),
                authorization,
                new JdbcWriteAttempt(jdbcTemplate, errors), queries);
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
        assertEquals(registration.response(), replay.replay());
        assertEquals(java.util.Set.of("fileId", "originalName", "size"), replay.replay().keySet());
        assertEquals("s3://files/first.txt", service.requireRead(replay.replay().get("fileId")));
    }

    @Test
    void filePortUsesCallerAuthorizationAndPreservesOwnershipForWebReads() throws Exception {
        var provider = mock(FileStorageProvider.class);
        var factory = mock(FileStorageFactory.class);
        when(factory.getStrategy()).thenReturn(provider);
        when(provider.upload(any())).thenReturn(stored("s3://files/report.txt"));
        byte[] content = "report".getBytes(StandardCharsets.UTF_8);
        var upload = new StorageFileObjectPort.Upload("report.txt", "text/plain", content.length,
                () -> new ByteArrayInputStream(content));
        var port = new StorageFileObjectAdapter(factory, service, new StoredFileUploadService(factory, service));

        UserContext.clear();
        var first = port.upload(upload, "report", "owner");
        assertEquals(first, port.upload(upload, "report", "owner"));
        assertEquals(first, port.getMetadata(first.fileId()));
        verify(provider, times(1)).upload(any());
        when(provider.open("s3://files/report.txt"))
                .thenReturn(new StoredFile(new ByteArrayInputStream(content), "internal", "text/plain", content.length));
        try (var file = port.open(first.fileId())) {
            assertEquals(first, file.metadata());
            assertEquals("report", new String(file.stream().readAllBytes(), StandardCharsets.UTF_8));
        }

        UserContext.setCurrentUser("other", "other");
        assertEquals(first, port.getMetadata(first.fileId()));
        assertThrows(ForbiddenException.class, () -> service.requireRead(first.fileId()));
    }

    @Test
    void removesRegisteredObjectIfEnclosingBusinessTransactionRollsBack() {
        var provider = mock(FileStorageProvider.class);
        var factory = mock(FileStorageFactory.class);
        when(factory.getStrategy()).thenReturn(provider);
        when(provider.upload(any())).thenReturn(stored("s3://files/uncommitted.txt"));
        when(provider.delete("s3://files/uncommitted.txt")).thenReturn(true);
        var uploads = new StoredFileUploadService(factory, service);
        var file = new com.workflow.contracts.storage.model.FileUpload("report.txt", "text/plain", 1L,
                () -> new ByteArrayInputStream(new byte[]{1}));
        var tx = new TransactionTemplate(new DataSourceTransactionManager(database));
        var id = tx.execute(status -> {
            var response = uploads.store(file, null);
            status.setRollbackOnly();
            return response.get("fileId");
        });
        verify(provider).delete("s3://files/uncommitted.txt");
        assertThrows(ForbiddenException.class, () -> service.requireDetails(id));
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
        assertEquals("s3://files/first.txt", service.requireRead(resolved.response().get("fileId")));
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

    @ParameterizedTest
    @ValueSource(strings = {"s3://files/private.txt", "s3://files/legacy/private.txt",
            "s3://archive-files/legacy/private.txt"})
    void onlyOwnerOrAuthenticatedAdministratorCanReadAndDelete(String url) {
        MockMultipartFile file = file("content");
        var registration = service.register(stored(url), file, service.prepareUpload("private-01", file));
        String fileId = registration.response().get("fileId");
        assertThrows(ForbiddenException.class, () -> service.requireRead(url));
        assertEquals(url, service.requireRead(fileId));
        service.requireDelete(fileId);
        UserContext.setCurrentUser("other-user", "other");
        assertThrows(ForbiddenException.class, () -> service.requireRead(fileId));
        assertThrows(ForbiddenException.class, () -> service.requireDelete(fileId));

        org.mockito.Mockito.when(authorization.isAdministrator()).thenReturn(true);
        service.requireRead(fileId);
        service.requireDelete(fileId);
        assertThrows(ForbiddenException.class, () -> service.requireRead("s3://files/missing.txt"));
        UserContext.clear();
        assertThrows(ForbiddenException.class, () -> service.requireRead(fileId));
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
