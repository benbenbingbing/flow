package com.workflow.storage.infrastructure.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.workflow.contracts.storage.model.StoredFile;
import com.workflow.storage.infrastructure.config.FileStorageProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

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
                strategy.upload(com.workflow.storage.infrastructure.web.MultipartFileUploadAdapter.from(upload));
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

    @ParameterizedTest
    @ValueSource(strings = {"flow/prod", "/flow/prod/"})
    void writesAndDeletesOnlyInsideTheConfiguredDirectory(String prefix) {
        FileStorageProperties.S3Config config = config();
        config.setKeyPrefix(prefix);
        S3Client client = mock(S3Client.class);
        try (S3FileStorageStrategy strategy = new S3FileStorageStrategy(config, client)) {
            Map<String, String> uploaded = strategy.upload(
                    com.workflow.storage.infrastructure.web.MultipartFileUploadAdapter.from(
                            new MockMultipartFile("file", "report.txt", "text/plain", new byte[] {1})));
            String key = uploaded.get("filename");
            assertTrue(key.matches("flow/prod/\\d{4}/\\d{2}/\\d{2}/[a-f0-9-]+\\.txt"));
            assertEquals("https://files.example.test/" + key, uploaded.get("url"));
            ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(client).putObject(put.capture(), any(RequestBody.class));
            assertEquals("files", put.getValue().bucket());
            assertEquals(key, put.getValue().key());

            assertTrue(strategy.delete(uploaded.get("url")));
            verify(client).deleteObject(DeleteObjectRequest.builder().bucket("files").key(key).build());
            assertFalse(strategy.delete("s3://files/legacy/report.txt"));
            assertFalse(strategy.delete("s3://files/flow/production/report.txt"));
            assertFalse(strategy.delete("s3://files/flow/prod"));
            assertFalse(strategy.delete("https://files.example.test/flow/production/report.txt"));
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"/", "///", " "})
    void emptyDirectoryPreservesBucketRootBehavior(String prefix) {
        FileStorageProperties.S3Config config = config();
        config.setKeyPrefix(prefix);
        S3Client client = mock(S3Client.class);
        try (S3FileStorageStrategy strategy = new S3FileStorageStrategy(config, client)) {
            Map<String, String> uploaded = strategy.upload(
                    com.workflow.storage.infrastructure.web.MultipartFileUploadAdapter.from(
                            new MockMultipartFile("file", "report.txt", "text/plain", new byte[] {1})));
            assertTrue(uploaded.get("filename").matches("\\d{4}/\\d{2}/\\d{2}/[a-f0-9-]+\\.txt"));
            assertTrue(strategy.delete("s3://files/legacy/report.txt"));
        }
    }

    @ParameterizedTest
    @CsvSource({"s3://files/legacy/report.txt, files, legacy/report.txt",
            "https://files.example.test/legacy/report.txt, files, legacy/report.txt",
            "s3://archive-files/other-project/report.txt, archive-files, other-project/report.txt",
            "s3://archive-files/flow/prod/report.txt, archive-files, flow/prod/report.txt"})
    void readsFullKeysOutsideUploadDirectoryAndFromAllowedBuckets(String url, String bucket, String key)
            throws Exception {
        FileStorageProperties.S3Config config = config();
        config.setKeyPrefix("flow/prod");
        config.setReadableBuckets(List.of("archive-files"));
        S3Client client = mock(S3Client.class);
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength(4L).build(),
                AbortableInputStream.create(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)))));
        try (S3FileStorageStrategy strategy = new S3FileStorageStrategy(config, client);
                StoredFile opened = strategy.open(url)) {
            assertEquals("report.txt", opened.filename());
            assertEquals("application/octet-stream", opened.contentType());
            assertEquals("data", new String(opened.inputStream().readAllBytes(), StandardCharsets.UTF_8));
            verify(client).getObject(GetObjectRequest.builder()
                    .bucket(bucket).key(key).build());
            assertFalse(strategy.delete(url));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"s3://unknown-bucket/report.txt", "s3://archive-files", "s3://archive-files/",
            "s3://archive-files//report.txt", "s3://archive-files/../report.txt",
            "s3://archive-files/folder\\report.txt", "s3:///report.txt",
            "https://archive-files.example.test/report.txt"})
    void rejectsInvalidOrUnconfiguredDownloadReferencesWithoutCallingStorage(String url) {
        FileStorageProperties.S3Config config = config();
        config.setReadableBuckets(List.of("archive-files"));
        S3Client client = mock(S3Client.class);
        S3FileStorageStrategy strategy = new S3FileStorageStrategy(config, client);
        assertThrows(IOException.class, () -> strategy.open(url));
        assertFalse(strategy.delete(url));
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../flow", "flow/../prod", "flow\\prod", "flow//prod", ".", "flow/./prod", "flow\nprod"})
    void rejectsInvalidUploadDirectoryAtStartup(String prefix) {
        FileStorageProperties.S3Config config = config();
        config.setKeyPrefix(prefix);
        S3Client client = mock(S3Client.class);
        assertThrows(IllegalStateException.class, () -> new S3FileStorageStrategy(config, client));
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "s3://archive-files", "archive-files/folder", "archive-files:9000"})
    void readableBucketsMustBeExactBucketNames(String bucket) {
        FileStorageProperties.S3Config config = config();
        config.setReadableBuckets(List.of(bucket));
        assertThrows(IllegalStateException.class,
                () -> new S3FileStorageStrategy(config, mock(S3Client.class)));
    }

    private FileStorageProperties.S3Config config() {
        FileStorageProperties.S3Config config =
                new FileStorageProperties.S3Config();
        config.setBucket("files");
        config.setAccessUrl("https://files.example.test");
        return config;
    }
}
