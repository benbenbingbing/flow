package com.workflow.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.workflow.contracts.entity.port.EntityFileUploadAuthorizationPort;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;
import com.workflow.storage.api.web.FileController;
import com.workflow.storage.application.FileStorageFactory;
import com.workflow.storage.application.FileStorageStrategy;
import com.workflow.storage.application.StoredFile;
import com.workflow.storage.application.StoredFileAccessService;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class FileControllerTest {

    @Test
    void replaysIdempotentUploadWithoutWritingStorageAgain() {
        FileStorageFactory factory = mock(FileStorageFactory.class);
        StoredFileAccessService accessService =
                mock(StoredFileAccessService.class);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.txt",
                "text/plain",
                "content".getBytes(StandardCharsets.UTF_8));
        Map<String, String> response = Map.of(
                "url", "s3://files/original.txt",
                "filename", "original.txt",
                "originalName", "report.txt",
                "size", "7");
        when(accessService.prepareUpload("upload-01", file))
                .thenReturn(new StoredFileAccessService.UploadClaim(
                        "user-1",
                        "upload-01",
                        "hash",
                        response));

        var result = new FileController(factory, accessService, List.of())
                .uploadFile(file, "upload-01");

        assertEquals(200, result.getCode());
        assertEquals(response, result.getData());
        verify(factory, never()).getStrategy();
    }

    @Test
    void removesObjectThatLosesConcurrentRegistration() {
        FileStorageFactory factory = mock(FileStorageFactory.class);
        FileStorageStrategy strategy = mock(FileStorageStrategy.class);
        StoredFileAccessService accessService =
                mock(StoredFileAccessService.class);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.txt",
                "text/plain",
                "content".getBytes(StandardCharsets.UTF_8));
        Map<String, String> current = Map.of(
                "url", "s3://files/current.txt",
                "filename", "current.txt");
        Map<String, String> winner = Map.of(
                "url", "s3://files/winner.txt",
                "filename", "winner.txt");
        StoredFileAccessService.UploadClaim claim =
                new StoredFileAccessService.UploadClaim(
                        "user-1",
                        "upload-02",
                        "hash",
                        null);
        when(accessService.prepareUpload("upload-02", file))
                .thenReturn(claim);
        when(factory.getStrategy()).thenReturn(strategy);
        when(strategy.upload(file)).thenReturn(current);
        when(strategy.delete("s3://files/current.txt"))
                .thenReturn(true);
        when(accessService.register(current, file, claim))
                .thenReturn(new StoredFileAccessService.UploadRegistration(
                        winner,
                        false));

        var result = new FileController(factory, accessService, List.of())
                .uploadFile(file, "upload-02");

        assertEquals(winner, result.getData());
        verify(strategy).delete("s3://files/current.txt");
    }

    @Test
    void previewStreamsThroughConfiguredStorageBackend()
            throws Exception {
        FileStorageFactory factory = mock(FileStorageFactory.class);
        FileStorageStrategy strategy =
                mock(FileStorageStrategy.class);
        when(factory.getStrategy()).thenReturn(strategy);
        when(strategy.open("s3://files/key"))
                .thenReturn(new StoredFile(
                        new ByteArrayInputStream(
                                "content".getBytes()),
                        "report.txt",
                        "text/plain",
                        7));
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        StoredFileAccessService accessService = mock(StoredFileAccessService.class);
        new FileController(factory, accessService, List.of()).previewFile(
                "s3://files/key",
                response);

        assertEquals("text/plain", response.getContentType());
        assertEquals(
                "nosniff",
                response.getHeader("X-Content-Type-Options"));
        assertEquals(7, response.getContentLengthLong());
        assertArrayEquals(
                "content".getBytes(),
                response.getContentAsByteArray());
    }

    @Test
    void previewDistinguishesMissingObjectFromBackendOutage()
            throws Exception {
        FileStorageFactory factory = mock(FileStorageFactory.class);
        FileStorageStrategy strategy =
                mock(FileStorageStrategy.class);
        when(factory.getStrategy()).thenReturn(strategy);
        when(strategy.open("missing"))
                .thenThrow(new FileNotFoundException());
        when(strategy.open("outage"))
                .thenThrow(new IllegalStateException("S3 unavailable"));
        StoredFileAccessService accessService = mock(StoredFileAccessService.class);
        FileController controller = new FileController(
                factory, accessService, List.of());
        MockHttpServletResponse missing =
                new MockHttpServletResponse();
        MockHttpServletResponse outage =
                new MockHttpServletResponse();

        controller.previewFile("missing", missing);
        controller.previewFile("outage", outage);

        assertEquals(404, missing.getStatus());
        assertEquals(503, outage.getStatus());
    }

    @Test
    void entityUploadAuthorizesBeforePreparingStorage() {
        FileStorageFactory factory = mock(FileStorageFactory.class);
        StoredFileAccessService accessService =
                mock(StoredFileAccessService.class);
        EntityFileUploadAuthorizationPort authorizer =
                mock(EntityFileUploadAuthorizationPort.class);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.txt",
                "text/plain",
                "content".getBytes(StandardCharsets.UTF_8));
        Map<String, String> replay = Map.of(
                "url", "s3://files/original.txt",
                "filename", "original.txt");
        when(accessService.prepareUpload("upload-entity-01", file))
                .thenReturn(new StoredFileAccessService.UploadClaim(
                        "user-1",
                        "upload-entity-01",
                        "hash",
                        replay));

        var result = new FileController(
                factory, accessService, List.of(authorizer))
                .uploadEntityFile(
                        "ZDWREQ", "create", "attachment",
                        file, "upload-entity-01");

        assertEquals(200, result.getCode());
        assertEquals(replay, result.getData());
        var ordered = inOrder(authorizer, accessService);
        ordered.verify(authorizer).requireUpload(
                "ZDWREQ", "create", "attachment");
        ordered.verify(accessService).prepareUpload(
                "upload-entity-01", file);
        verify(factory, never()).getStrategy();
    }

    @Test
    void rejectedEntityUploadDoesNotTouchStorage() {
        FileStorageFactory factory = mock(FileStorageFactory.class);
        StoredFileAccessService accessService =
                mock(StoredFileAccessService.class);
        EntityFileUploadAuthorizationPort authorizer =
                mock(EntityFileUploadAuthorizationPort.class);
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.txt", "text/plain", new byte[] {1});
        doThrow(new ForbiddenException("没有权限"))
                .when(authorizer)
                .requireUpload("ZDWREQ", "create", "attachment");
        FileController controller = new FileController(
                factory, accessService, List.of(authorizer));

        assertThrows(
                ForbiddenException.class,
                () -> controller.uploadEntityFile(
                        "ZDWREQ", "create", "attachment",
                        file, "upload-entity-02"));

        verifyNoInteractions(factory, accessService);
    }

    @Test
    void entityUploadFailsClosedWhenAuthorizerIsMissingOrAmbiguous() {
        FileStorageFactory factory = mock(FileStorageFactory.class);
        StoredFileAccessService accessService =
                mock(StoredFileAccessService.class);
        EntityFileUploadAuthorizationPort first =
                mock(EntityFileUploadAuthorizationPort.class);
        EntityFileUploadAuthorizationPort second =
                mock(EntityFileUploadAuthorizationPort.class);
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.txt", "text/plain", new byte[] {1});

        FileController missing = new FileController(
                factory, accessService, List.of());
        assertThrows(
                ForbiddenException.class,
                () -> missing.uploadEntityFile(
                        "ZDWREQ", "create", "attachment",
                        file, "upload-entity-03"));

        FileController ambiguous = new FileController(
                factory, accessService, List.of(first, second));
        assertThrows(
                ForbiddenException.class,
                () -> ambiguous.uploadEntityFile(
                        "ZDWREQ", "create", "attachment",
                        file, "upload-entity-04"));

        verifyNoInteractions(factory, accessService, first, second);
    }

    @Test
    void genericAndEntityUploadKeepDistinctAuthorizationContracts()
            throws Exception {
        RequiresPermission genericPermission = FileController.class
                .getDeclaredMethod(
                        "uploadFile", MultipartFile.class, String.class)
                .getAnnotation(RequiresPermission.class);
        AuthenticatedApi genericAuthentication = FileController.class
                .getDeclaredMethod(
                        "uploadFile", MultipartFile.class, String.class)
                .getAnnotation(AuthenticatedApi.class);

        assertNotNull(genericPermission);
        assertArrayEquals(
                new String[] {"storage:file:write"},
                genericPermission.value());
        assertNull(genericAuthentication);

        var entityUpload = FileController.class.getDeclaredMethod(
                "uploadEntityFile",
                String.class,
                String.class,
                String.class,
                MultipartFile.class,
                String.class);
        AuthenticatedApi entityAuthentication =
                entityUpload.getAnnotation(AuthenticatedApi.class);

        assertNotNull(entityAuthentication);
        assertTrue(entityAuthentication.objectAuthorization());
        assertNull(entityUpload.getAnnotation(RequiresPermission.class));
    }
}
