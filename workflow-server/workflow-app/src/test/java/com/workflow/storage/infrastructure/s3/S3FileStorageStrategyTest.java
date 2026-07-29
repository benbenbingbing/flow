package com.workflow.storage.infrastructure.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.storage.application.StoredFile;
import com.workflow.storage.infrastructure.config.FileStorageProperties;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

class S3FileStorageStrategyTest {

    @Test
    void uploadsAndStreamsObjectsUsingStablePublicUrl()
            throws Exception {
        S3Client client = mock(S3Client.class);
        when(client.getObject(any(GetObjectRequest.class)))
                .thenReturn(new ResponseInputStream<>(
                        GetObjectResponse.builder()
                                .contentLength(4L)
                                .contentType("text/plain")
                                .metadata(java.util.Map.of(
                                        "original-name-b64",
                                        "5Lit5paHLnR4dA"))
                                .build(),
                        AbortableInputStream.create(
                                new ByteArrayInputStream(
                                        "data".getBytes(
                                                StandardCharsets.UTF_8)))));
        S3FileStorageStrategy strategy =
                new S3FileStorageStrategy(config(), client);
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "note.txt",
                "text/plain",
                "data".getBytes(StandardCharsets.UTF_8));

        java.util.Map<String, String> uploaded =
                strategy.upload(upload);
        try (StoredFile opened =
                strategy.open(uploaded.get("url"))) {
            assertEquals("中文.txt", opened.filename());
            assertEquals(
                    "data",
                    new String(
                            opened.inputStream().readAllBytes(),
                            StandardCharsets.UTF_8));
        }

        verify(client).putObject(
                any(software.amazon.awssdk.services.s3.model
                        .PutObjectRequest.class),
                any(software.amazon.awssdk.core.sync
                        .RequestBody.class));
    }

    @Test
    void rejectsUrlsOutsideConfiguredStoragePrefix() {
        S3FileStorageStrategy strategy =
                new S3FileStorageStrategy(config(), mock(S3Client.class));

        assertFalse(strategy.delete(
                "https://attacker.invalid/file.txt"));
    }

    private FileStorageProperties.S3Config config() {
        FileStorageProperties.S3Config config =
                new FileStorageProperties.S3Config();
        config.setBucket("files");
        config.setAccessUrl("https://files.example.test");
        return config;
    }
}
