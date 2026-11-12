package com.workflow.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import com.workflow.contracts.storage.model.FileUpload;
import com.workflow.storage.infrastructure.config.FileStorageProperties;
import com.workflow.storage.infrastructure.minio.MinioFileStorageStrategy;
import com.workflow.storage.infrastructure.s3.S3FileStorageStrategy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectStorageUploadTest {

    @ParameterizedTest
    @ValueSource(strings = {"s3", "minio"})
    void realSdkSignsAndRetriesDiskUploadWithoutResettingTheStream(String type, @TempDir Path directory)
            throws Exception {
        String sample = "non-resettable-upload-content\n".repeat(100);
        byte[] content = sample.repeat(100).getBytes(StandardCharsets.UTF_8);
        Path path = Files.write(directory.resolve("upload.txt"), content);
        try (var stream = Files.newInputStream(path)) {
            assertFalse(stream.markSupported());
        }
        List<byte[]> requests = new CopyOnWriteArrayList<>();
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // 首次请求模拟对象存储短暂故障，让真实 SDK 执行签名、传输和自动重试。
        server.createContext("/", exchange -> {
            try {
                requestPath.set(exchange.getRequestURI().getPath());
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                requests.add(objectContent(exchange.getRequestBody().readAllBytes(),
                        exchange.getRequestHeaders().getFirst("Content-Encoding")));
                exchange.sendResponseHeaders(requests.size() == 1 ? 500 : 200, -1);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            FileStorageProperties properties = properties("http://127.0.0.1:" + server.getAddress().getPort());
            try (S3FileStorageStrategy strategy = "s3".equals(type)
                    ? new S3FileStorageStrategy(properties)
                    : new MinioFileStorageStrategy(properties)) {
                var uploaded = strategy.upload(new FileUpload("upload.txt", "text/plain", content.length,
                        () -> Files.newInputStream(path)));
                assertEquals("/files/" + uploaded.get("filename"), requestPath.get());
                assertEquals(String.valueOf(content.length), uploaded.get("size"));
            }
            assertEquals(2, requests.size());
            assertArrayEquals(content, requests.get(0));
            assertArrayEquals(content, requests.get(1));
            assertTrue(authorization.get().startsWith("AWS4-HMAC-SHA256 "));
        } finally {
            server.stop(0);
        }
    }

    /** 解码 aws-chunked 的内容帧；重试会重新签名，校验应比较文件内容而不是每帧的签名。 */
    private byte[] objectContent(byte[] body, String encoding) throws IOException {
        if (encoding == null || !encoding.contains("aws-chunked")) {
            return body;
        }
        ByteArrayInputStream input = new ByteArrayInputStream(body);
        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        while (true) {
            StringBuilder header = new StringBuilder();
            int next;
            while ((next = input.read()) != -1 && next != '\r') {
                header.append((char) next);
            }
            if (next == -1 || input.read() != '\n') {
                throw new IOException("Invalid aws-chunked header");
            }
            int length = Integer.parseInt(header.toString().split(";", 2)[0], 16);
            if (length == 0) {
                return decoded.toByteArray();
            }
            byte[] chunk = input.readNBytes(length);
            if (chunk.length != length || input.read() != '\r' || input.read() != '\n') {
                throw new IOException("Invalid aws-chunked content");
            }
            decoded.write(chunk);
        }
    }

    /** S3 和 MinIO 均连接同一个本地对象 API，只使用测试凭证，不访问外部存储。 */
    private FileStorageProperties properties(String endpoint) {
        FileStorageProperties properties = new FileStorageProperties();
        var s3 = properties.getS3();
        s3.setEndpoint(endpoint);
        s3.setBucket("files");
        s3.setAccessKey("test-access");
        s3.setSecretKey("test-secret");
        s3.setPathStyleAccess(true);

        var minio = properties.getMinio();
        minio.setEndpoint(endpoint);
        minio.setBucket("files");
        minio.setAccessKey("test-access");
        minio.setSecretKey("test-secret");
        return properties;
    }
}
