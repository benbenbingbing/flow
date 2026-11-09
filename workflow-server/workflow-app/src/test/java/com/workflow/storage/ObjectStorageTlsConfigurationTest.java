package com.workflow.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import com.workflow.contracts.storage.model.StoredFile;
import com.workflow.storage.infrastructure.config.FileStorageProperties;
import com.workflow.storage.infrastructure.minio.MinioFileStorageStrategy;
import com.workflow.storage.infrastructure.s3.S3FileStorageStrategy;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awssdk.core.exception.SdkClientException;

class ObjectStorageTlsConfigurationTest {

    private static SSLContext serverTlsContext;
    private HttpsServer server;

    /**
     * 动态生成仅用于测试的证书，避免在仓库中保存私钥。
     * 证书属于 localhost，请求使用 127.0.0.1，同时覆盖证书信任与主机名校验的开关行为。
     */
    @BeforeAll
    static void createServerTlsContext(@TempDir Path directory) throws Exception {
        Path keyStorePath = directory.resolve("storage-test.p12");
        String password = "temporary-test-password";
        Process keytool = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "storage-test", "-keyalg", "RSA", "-keysize", "2048",
                "-storetype", "PKCS12", "-keystore", keyStorePath.toString(),
                "-storepass", password, "-keypass", password,
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-validity", "1", "-noprompt")
                .redirectErrorStream(true)
                .start();
        try {
            assertTrue(keytool.waitFor(30, TimeUnit.SECONDS), "生成本地测试证书超时");
            assertEquals(0, keytool.exitValue(),
                    new String(keytool.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } finally {
            keytool.destroyForcibly();
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(keyStorePath)) {
            keyStore.load(input, password.toCharArray());
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, password.toCharArray());
        serverTlsContext = SSLContext.getInstance("TLS");
        serverTlsContext.init(keyManagers.getKeyManagers(), null, null);
    }

    @BeforeEach
    void startObjectServer() throws Exception {
        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverTlsContext));
        server.createContext("/", exchange -> {
            byte[] content = "storage-data".getBytes(StandardCharsets.UTF_8);
            try {
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, content.length);
                exchange.getResponseBody().write(content);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopObjectServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "s3,false,false", "s3,false,true", "s3,true,false",
            "minio,false,false", "minio,true,false", "minio,false,true"
    })
    void onlySelectedStorageCanDisableTlsVerification(
            String type, boolean s3TrustAllCertificates, boolean minioTrustAllCertificates) throws Exception {
        FileStorageProperties properties = properties(s3TrustAllCertificates, minioTrustAllCertificates);
        boolean trustAllCertificates = "s3".equals(type) ? s3TrustAllCertificates : minioTrustAllCertificates;
        try (S3FileStorageStrategy strategy = "s3".equals(type)
                ? new S3FileStorageStrategy(properties)
                : new MinioFileStorageStrategy(properties)) {
            if (trustAllCertificates) {
                try (StoredFile file = strategy.open("s3://files/report.txt")) {
                    assertEquals("storage-data",
                            new String(file.inputStream().readAllBytes(), StandardCharsets.UTF_8));
                }
            } else {
                assertThrows(SdkClientException.class, () -> strategy.open("s3://files/report.txt"));
            }
        }
    }

    private FileStorageProperties properties(boolean s3TrustAllCertificates, boolean minioTrustAllCertificates) {
        FileStorageProperties properties = new FileStorageProperties();
        String endpoint = "https://127.0.0.1:" + server.getAddress().getPort();
        FileStorageProperties.S3Config s3 = properties.getS3();
        s3.setEndpoint(endpoint);
        s3.setBucket("files");
        s3.setAccessKey("test-access");
        s3.setSecretKey("test-secret");
        s3.setPathStyleAccess(true);
        s3.setTrustAllCertificates(s3TrustAllCertificates);

        FileStorageProperties.MinioConfig minio = properties.getMinio();
        minio.setEndpoint(endpoint);
        minio.setBucket("files");
        minio.setAccessKey("test-access");
        minio.setSecretKey("test-secret");
        minio.setTrustAllCertificates(minioTrustAllCertificates);
        return properties;
    }
}
