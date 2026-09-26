package com.workflow.storage.infrastructure.minio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import com.workflow.contracts.storage.model.StoredFile;
import com.workflow.storage.infrastructure.config.FileStorageProperties;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

class MinioFileStorageStrategyTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "https://files.example.test/flow-files", "https://files.example.test/flow-files/"})
    void uploadsReadsAndDeletesUsingStableObjectReference(String accessUrl) throws Exception {
        FileStorageProperties.MinioConfig config = config();
        config.setAccessUrl(accessUrl);
        S3Client client = mock(S3Client.class);
        byte[] content = "图片内容".getBytes(StandardCharsets.UTF_8);
        String originalName = "需求图片.png";
        String encodedName = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(originalName.getBytes(StandardCharsets.UTF_8));
        ByteArrayInputStream uploadStream = spy(new ByteArrayInputStream(content));
        MockMultipartFile file = new MockMultipartFile("file", originalName, "image/png", content) {
            @Override
            public java.io.InputStream getInputStream() {
                return uploadStream;
            }
        };
        // 请求体由同步 SDK 在 upload 返回前消费，验证文件二进制未被当作文本转换。
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenAnswer(invocation -> {
                    RequestBody body = invocation.getArgument(1);
                    assertArrayEquals(content, body.contentStreamProvider().newStream().readAllBytes());
                    return null;
                });
        ResponseInputStream<GetObjectResponse> downloadStream = new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength((long) content.length)
                        .contentType("image/png").metadata(Map.of("original-name-b64", encodedName)).build(),
                AbortableInputStream.create(new ByteArrayInputStream(content)));
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(downloadStream);

        try (MinioFileStorageStrategy strategy = new MinioFileStorageStrategy(config, client)) {
            Map<String, String> result = strategy.upload(com.workflow.storage.infrastructure.web.MultipartFileUploadAdapter.from(file));
            String key = result.get("filename");
            assertTrue(key.matches("\\d{4}/\\d{2}/\\d{2}/[a-f0-9-]+\\.png"));
            String prefix = accessUrl.isEmpty() ? "s3://flow-files/" : accessUrl.replaceAll("/+$", "") + "/";
            assertEquals(prefix + key, result.get("url"));
            assertEquals(originalName, result.get("originalName"));
            assertEquals(String.valueOf(content.length), result.get("size"));
            verify(uploadStream).close();

            ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(client).putObject(put.capture(), any(RequestBody.class));
            assertEquals("flow-files", put.getValue().bucket());
            assertEquals(key, put.getValue().key());
            assertEquals("image/png", put.getValue().contentType());
            assertEquals(encodedName, put.getValue().metadata().get("original-name-b64"));

            try (StoredFile stored = strategy.open(result.get("url"))) {
                assertEquals(originalName, stored.filename());
                assertEquals("image/png", stored.contentType());
                assertEquals(content.length, stored.contentLength());
                assertArrayEquals(content, stored.inputStream().readAllBytes());
            }
            verify(client).getObject(GetObjectRequest.builder().bucket("flow-files").key(key).build());
            assertTrue(strategy.delete(result.get("url")));
            verify(client).deleteObject(DeleteObjectRequest.builder().bucket("flow-files").key(key).build());
        }
        verify(client).close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"s3://other-bucket/file.png", "https://other.test/file.png",
            "s3://flow-files/../file.png", "s3://flow-files/", "s3://flow-files//file.png"})
    void rejectsReferencesOutsideTheConfiguredBucket(String url) {
        S3Client client = mock(S3Client.class);
        MinioFileStorageStrategy strategy = new MinioFileStorageStrategy(config(), client);
        assertFalse(strategy.delete(url));
        assertThrows(IOException.class, () -> strategy.open(url));
        verifyNoInteractions(client);
    }

    @Test
    void missingObjectKeepsTheFileControllerNotFoundContract() {
        S3Client client = mock(S3Client.class);
        when(client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("NoSuchKey").build());
        MinioFileStorageStrategy strategy = new MinioFileStorageStrategy(config(), client);
        assertThrows(FileNotFoundException.class, () -> strategy.open("s3://flow-files/missing.png"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"endpoint", "bucket", "region", "access-key", "secret-key"})
    void requiresCompleteMinioConfiguration(String property) {
        FileStorageProperties.MinioConfig config = config();
        switch (property) {
            case "endpoint" -> config.setEndpoint("");
            case "bucket" -> config.setBucket("");
            case "region" -> config.setRegion("");
            case "access-key" -> config.setAccessKey("");
            case "secret-key" -> config.setSecretKey("");
            default -> fail("Unknown property");
        }
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new MinioFileStorageStrategy(config, mock(S3Client.class)));
        assertTrue(error.getMessage().contains("file.storage.minio." + property));
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost:9000", "ftp://localhost:9000", "https://localhost/bucket",
            "https://user:secret@localhost", "https://localhost?secret=value", "https://localhost/#console", "bad url"})
    void rejectsInvalidEndpointWithoutEchoingItsValue(String endpoint) {
        FileStorageProperties.MinioConfig config = config();
        config.setEndpoint(endpoint);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new MinioFileStorageStrategy(config, mock(S3Client.class)));
        assertEquals("file.storage.minio.endpoint must be an HTTP(S) object API root URL", error.getMessage());
        assertNull(error.getCause());
    }

    @Test
    void realClientUsesConfiguredEndpointCredentialsRegionAndPathStyle() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> requestedPath = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/", exchange -> {
            requestedPath.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] content = "minio-object".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, content.length);
            try (var output = exchange.getResponseBody()) {
                output.write(content);
            }
        });
        server.start();
        try {
            FileStorageProperties properties = new FileStorageProperties();
            properties.setMinio(config());
            // localhost 是合法 DNS 名，若未强制路径式寻址，SDK 会尝试 flow-files.localhost。
            properties.getMinio().setEndpoint("http://localhost:" + server.getAddress().getPort());
            properties.getMinio().setRegion("test-region-1");
            try (MinioFileStorageStrategy strategy = new MinioFileStorageStrategy(properties);
                    StoredFile stored = strategy.open("s3://flow-files/folder/file.txt")) {
                assertEquals("minio-object", new String(stored.inputStream().readAllBytes(), StandardCharsets.UTF_8));
            }
            assertEquals("/flow-files/folder/file.txt", requestedPath.get());
            assertTrue(authorization.get().contains("Credential=minio-test-access/"));
            assertTrue(authorization.get().contains("/test-region-1/s3/aws4_request"));
        } finally {
            server.stop(0);
        }
    }

    private FileStorageProperties.MinioConfig config() {
        FileStorageProperties.MinioConfig config = new FileStorageProperties.MinioConfig();
        config.setEndpoint("http://localhost:9000");
        config.setBucket("flow-files");
        config.setAccessKey("minio-test-access");
        config.setSecretKey("minio-test-secret");
        return config;
    }
}
