package com.workflow.storage.infrastructure.s3;

import com.workflow.storage.application.FileStorageStrategy;
import com.workflow.storage.application.StoredFile;
import com.workflow.storage.infrastructure.config.FileStorageProperties;
import jakarta.annotation.PreDestroy;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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
public class S3FileStorageStrategy implements FileStorageStrategy, AutoCloseable {

    private final FileStorageProperties.S3Config config;
    private final S3Client client;

    /**
     * 初始化{@code s3}文件存储{@code strategy}，保存构造参数供后续方法使用。
     *
     * @param properties 属性集合，保存在对象中供后续校验、查询或展示
     */
    @Autowired
    public S3FileStorageStrategy(FileStorageProperties properties) {
        this(properties.getS3(), null);
    }

    /**
     * 供 S3 兼容策略复用对象读写；未传客户端时按配置创建，策略销毁时关闭客户端。
     *
     * @param config 对象存储配置，桶和区域必填，显式凭证须成对提供
     * @param client 可选的专用客户端，其生命周期由该策略管理
     */
    protected S3FileStorageStrategy(
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

    /**
     * 构建客户端；结果供后续流程传递或持久化。
     *
     * @param config 配置内容，决定后续客户端的处理规则
     * @return 构建后的客户端结果，供调用方继续处理
     */
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

    /**
     * 整理上传数据，供调用方遍历或继续处理。
     *
     * @param file 文件，作为 {@code objectKey} 的输入影响后续处理
     * @return 上传键值结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    @Override
    public Map<String, String> upload(MultipartFile file) {
        String key = objectKey(file.getOriginalFilename());
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : "application/octet-stream";
        // SDK 同步消费请求体，上传结束后由调用方关闭 MultipartFile 打开的流。
        try (InputStream stream = file.getInputStream()) {
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
                            stream,
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

    /**
     * 删除{@code s3}文件存储{@code strategy}；后续读取或执行将使用更新后的状态。
     *
     * @param fileUrl 文件URL，作为 {@code extractKey} 的输入影响后续处理
     * @return {@code s3}文件存储{@code strategy}条件成立时为 true，否则为 false
     */
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

    /**
     * 处理打开，并将结果传给后续步骤。
     *
     * @param fileUrl 文件URL，作为 {@code extractKey} 的输入影响后续处理
     * @return 处理后的打开结果，供调用方继续处理
     * @throws IOException 读取或写入外部资源失败时抛出
     */
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

    /**
     * 读取访问URL；查询结果供调用方展示或继续处理。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 读取后的访问URL文本，供调用方比较或展示
     */
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

    /**
     * 读取存储类型；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的存储类型文本，供调用方比较或展示
     */
    @Override
    public String getStorageType() {
        return "s3";
    }

    /** 释放 SDK 的 HTTP 连接池，避免应用关闭或配置上下文重建时泄漏连接。 */
    @PreDestroy
    @Override
    public void close() {
        client.close();
    }

    /**
     * 生成对象键文本，供后续匹配或展示。
     *
     * @param originalName 原始名称，后续用于处理对象键时匹配或展示
     * @return 处理后的对象键文本，供调用方比较或展示
     */
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

    /**
     * 提取键；输出作为后续校验或处理的输入。
     *
     * @param fileUrl 文件URL，供本方法提取键时使用
     * @return 提取后的键文本，供调用方比较或展示
     */
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

    /**
     * 编码原始名称；输出作为后续校验或处理的输入。
     *
     * @param originalName 原始名称，后续用于编码原始名称时匹配或展示
     * @return 编码后的原始名称文本，供调用方比较或展示
     */
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

    /**
     * 解码原始名称；输出作为后续校验或处理的输入。
     *
     * @param encoded 已编码，供本方法解码原始名称时使用
     * @return 解码后的原始名称文本，供调用方比较或展示
     */
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

    /**
     * 校验并获取文本；不满足约束时阻止后续处理。
     *
     * @param value 待校验并获取文本的原始输入，结果供调用方继续使用
     * @param property 属性，作为 {@code IllegalStateException} 的输入影响后续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private void requireText(String value, String property) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    property + " must be configured");
        }
    }

    /**
     * 校验{@code credentials}；不满足约束时阻止后续处理。
     *
     * @param value 待校验{@code credentials}的原始输入，结果供调用方继续使用
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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
