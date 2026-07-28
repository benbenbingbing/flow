package com.workflow.storage.infrastructure.s3;

import com.workflow.storage.application.FileStorageStrategy;
import com.workflow.storage.application.StoredFile;
import com.workflow.storage.infrastructure.config.FileStorageProperties;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3-compatible storage shared by every application replica.
 */
@Component
@ConditionalOnProperty(
        name = "file.storage.type",
        havingValue = "s3")
public class S3FileStorageStrategy implements FileStorageStrategy {

    private final FileStorageProperties.S3Config config;
    private final S3Client client;

    @Autowired
    public S3FileStorageStrategy(FileStorageProperties properties) {
        this(properties.getS3(), null);
    }

    S3FileStorageStrategy(
            FileStorageProperties.S3Config config,
            S3Client client) {
        this.config = config;
        requireText(config.getBucket(), "file.storage.s3.bucket");
        requireText(config.getRegion(), "file.storage.s3.region");
        validateCredentials(config);
        this.client = client == null
                ? buildClient(config)
                : client;
    }

    private S3Client buildClient(
            FileStorageProperties.S3Config config) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(config.getRegion()))
                .forcePathStyle(config.isPathStyleAccess())
                .httpClientBuilder(Apache5HttpClient.builder());
        if (StringUtils.hasText(config.getEndpoint())) {
            builder.endpointOverride(URI.create(config.getEndpoint()));
        }
        if (StringUtils.hasText(config.getAccessKey())) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(
                                    config.getAccessKey(),
                                    config.getSecretKey())));
        }
        return builder.build();
    }

    @Override
    public Map<String, String> upload(MultipartFile file) {
        String key = objectKey(file.getOriginalFilename());
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : "application/octet-stream";
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(config.getBucket())
                            .key(key)
                            .contentType(contentType)
                            .metadata(Map.of(
                                    "original-name-b64",
                                    encodeOriginalName(
                                            file.getOriginalFilename())))
                            .build(),
                    RequestBody.fromInputStream(
                            file.getInputStream(),
                            file.getSize()));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "读取上传文件失败",
                    exception);
        }
        Map<String, String> result = new HashMap<>();
        result.put("url", getAccessUrl(key));
        result.put("filename", key);
        result.put("originalName", file.getOriginalFilename());
        result.put("size", String.valueOf(file.getSize()));
        return result;
    }

    @Override
    public boolean delete(String fileUrl) {
        String key = extractKey(fileUrl);
        if (key == null) {
            return false;
        }
        client.deleteObject(DeleteObjectRequest.builder()
                .bucket(config.getBucket())
                .key(key)
                .build());
        return true;
    }

    @Override
    public StoredFile open(String fileUrl) throws IOException {
        String key = extractKey(fileUrl);
        if (key == null) {
            throw new IOException("文件路径无效");
        }
        ResponseInputStream<GetObjectResponse> stream;
        try {
            stream = client.getObject(GetObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(key)
                    .build());
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new FileNotFoundException("文件不存在");
            }
            throw exception;
        }
        GetObjectResponse response = stream.response();
        String originalName = decodeOriginalName(
                response.metadata().get("original-name-b64"));
        return new StoredFile(
                stream,
                StringUtils.hasText(originalName)
                        ? originalName
                        : key.substring(key.lastIndexOf('/') + 1),
                StringUtils.hasText(response.contentType())
                        ? response.contentType()
                        : "application/octet-stream",
                response.contentLength() == null
                        ? -1
                        : response.contentLength());
    }

    @Override
    public String getAccessUrl(String key) {
        if (StringUtils.hasText(config.getAccessUrl())) {
            String prefix = config.getAccessUrl();
            return (prefix.endsWith("/")
                    ? prefix
                    : prefix + "/") + key;
        }
        return "s3://" + config.getBucket() + "/" + key;
    }

    @Override
    public String getStorageType() {
        return "s3";
    }

    private String objectKey(String originalName) {
        String extension = "";
        if (StringUtils.hasText(originalName)) {
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0 && dot < originalName.length() - 1) {
                extension = originalName.substring(dot)
                        .replaceAll("[^A-Za-z0-9.]", "");
                if (extension.length() > 16) {
                    extension = "";
                }
            }
        }
        return LocalDate.now().format(
                DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                + "/"
                + UUID.randomUUID()
                + extension;
    }

    private String extractKey(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }
        String key = fileUrl;
        String s3Prefix = "s3://" + config.getBucket() + "/";
        if (key.startsWith(s3Prefix)) {
            key = key.substring(s3Prefix.length());
        } else if (StringUtils.hasText(config.getAccessUrl())) {
            String accessPrefix = config.getAccessUrl();
            accessPrefix = accessPrefix.endsWith("/")
                    ? accessPrefix
                    : accessPrefix + "/";
            if (!key.startsWith(accessPrefix)) {
                return null;
            }
            key = key.substring(accessPrefix.length());
        } else {
            return null;
        }
        return key.isBlank()
                || key.startsWith("/")
                || key.contains("..")
                || key.contains("\\")
                ? null
                : key;
    }

    private String encodeOriginalName(String originalName) {
        if (!StringUtils.hasText(originalName)) {
            originalName = "file";
        }
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        originalName.replaceAll("[\\r\\n]", "")
                                .getBytes(StandardCharsets.UTF_8));
    }

    private String decodeOriginalName(String encoded) {
        if (!StringUtils.hasText(encoded)) {
            return null;
        }
        try {
            return new String(
                    Base64.getUrlDecoder().decode(encoded),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void requireText(String value, String property) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    property + " must be configured");
        }
    }

    private void validateCredentials(
            FileStorageProperties.S3Config value) {
        boolean accessKey = StringUtils.hasText(value.getAccessKey());
        boolean secretKey = StringUtils.hasText(value.getSecretKey());
        if (accessKey != secretKey) {
            throw new IllegalStateException(
                    "S3 access key and secret key must be configured together");
        }
    }
}
