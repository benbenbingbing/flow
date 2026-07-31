package com.workflow.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.storage.api.web.FileController;
import com.workflow.storage.application.StoredFileAccessService;
import com.workflow.storage.application.FileStorageFactory;
import com.workflow.storage.application.FileStorageStrategy;
import com.workflow.storage.application.StoredFile;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletResponse;

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

        var result = new FileController(factory, accessService)
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

        var result = new FileController(factory, accessService)
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
        new FileController(factory, accessService).previewFile(
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
        FileController controller = new FileController(factory, accessService);
        MockHttpServletResponse missing =
                new MockHttpServletResponse();
        MockHttpServletResponse outage =
                new MockHttpServletResponse();

        controller.previewFile("missing", missing);
        controller.previewFile("outage", outage);

        assertEquals(404, missing.getStatus());
        assertEquals(503, outage.getStatus());
    }
}
