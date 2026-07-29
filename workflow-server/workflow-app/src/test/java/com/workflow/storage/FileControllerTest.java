package com.workflow.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.workflow.storage.api.web.FileController;
import com.workflow.storage.application.StoredFileAccessService;
import com.workflow.storage.application.FileStorageFactory;
import com.workflow.storage.application.FileStorageStrategy;
import com.workflow.storage.application.StoredFile;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class FileControllerTest {

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
