package com.workflow.storage.infrastructure.s3;

import com.workflow.contracts.storage.model.FileUpload;
import com.workflow.contracts.storage.model.StoredFile;
import com.workflow.contracts.storage.spi.FileStorageProvider;
import com.workflow.storage.infrastructure.config.FileStorageProperties;
import jakarta.annotation.PreDestroy;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
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
 * S3 兼容存储：上传和删除使用默认桶的指定目录，下载可读取默认桶及显式允许的其他桶。
 * 对象引用仅决定桶和 key，所有请求仍复用本策略的 endpoint、区域和凭证。
 */
@Component
@ConditionalOnProperty(
        name = "file.storage.type",
        havingValue = "s3")
public class S3FileStorageStrategy implements FileStorageProvider, AutoCloseable {

    private final FileStorageProperties.S3Config config;
    private final S3Client client;
    /** 带结尾斜杠的写入前缀，保证目录边界匹配，避免 flow/prod 误匹配 flow/production。 */
    private final String keyPrefix;
    private final Set<String> readableBuckets;

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
        this.keyPrefix = normalizeKeyPrefix(config.getKeyPrefix());
        this.readableBuckets = readableBuckets(config.getReadableBuckets());
        this.client = client == null
                ? buildClient(config)
                : client;
    }

    /**
     * 为当前存储策略构建专用客户端，由 S3Client 管理 HTTP 连接池生命周期。
     *
     * @param config 区域、端点、凭证及 TLS 配置，信任全部证书须显式开启
     * @return 用于上传、下载及删除的客户端，随策略关闭
     */
    private S3Client buildClient(
            FileStorageProperties.S3Config config) {
        // 在 SDK 提供的默认配置上覆盖本客户端的 TLS 开关，避免影响其他存储策略或 JVM 全局 HTTPS。
        // 保留 httpClientBuilder 入口，使 S3Client 继续负责关闭 HTTP 客户端。
        SdkHttpClient.Builder<Apache5HttpClient.Builder> httpClientBuilder = serviceDefaults ->
                Apache5HttpClient.builder().buildWithDefaults(serviceDefaults.toBuilder()
                        .put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, config.isTrustAllCertificates())
                        .build());
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(config.getRegion()))
                .forcePathStyle(config.isPathStyleAccess())
                .httpClientBuilder(httpClientBuilder);
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
     * 将文件上传到默认桶的 key-prefix/年/月/日/UUID 路径，并返回包含完整对象 key 的文件标识。
     *
     * @param file 当前同步调用内可重复打开的上传内容，供 SDK 签名、校验及重试时从头读取
     * @return 文件标识、完整对象 key、原始名称和大小，供调用方保存文件记录
     * @throws IllegalStateException 打开、读取或关闭上传内容失败时抛出
     */
    @Override
    public Map<String, String> upload(FileUpload file) {
        String key = objectKey(file.originalFilename());
        String contentType = StringUtils.hasText(file.contentType())
                ? file.contentType()
                : "application/octet-stream";
        // 磁盘上的 multipart 流不一定支持 mark/reset；每次重读重新打开，避免签名或重试失败。
        // 显式提供已知长度，保持流式上传，不为支持回退而把整份文件缓存在内存中。
        try (UploadContentStreamProvider content = new UploadContentStreamProvider(file)) {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(config.getBucket())
                            .key(key)
                            .contentType(contentType)
                            .metadata(Map.of(
                                    "original-name-b64",
                                    encodeOriginalName(
                                            file.originalFilename())))
                            .build(),
                    RequestBody.fromContentProvider(content, file.size(), contentType));
        } catch (IOException | UncheckedIOException exception) {
            throw new IllegalStateException(
                    "读取上传文件失败",
                    exception);
        }
        Map<String, String> result = new HashMap<>();
        result.put("url", getAccessUrl(key));
        result.put("filename", key);
        result.put("originalName", file.originalFilename());
        result.put("size", String.valueOf(file.size()));
        return result;
    }

    /**
     * 在单次同步上传内管理可重放的内容流；重读前关闭上一条流，结束时关闭最后一条流。
     * 不依赖 SDK 是否主动关闭流，因此签名失败或存储服务异常时也不会遗留文件句柄。
     */
    private static final class UploadContentStreamProvider implements ContentStreamProvider, AutoCloseable {
        private final FileUpload file;
        private InputStream stream;

        private UploadContentStreamProvider(FileUpload file) {
            this.file = file;
        }

        /** 每次均返回内容起点的新流；SDK 的回调不允许 checked exception，IO 失败需转为运行时异常。 */
        @Override
        public InputStream newStream() {
            try {
                close();
                stream = file.openStream();
                return stream;
            } catch (IOException exception) {
                throw new UncheckedIOException("读取上传文件失败", exception);
            }
        }

        /** 先清除引用再关闭，避免关闭失败时在退出上传的清理阶段再次关闭同一条流。 */
        @Override
        public void close() throws IOException {
            InputStream previous = stream;
            stream = null;
            if (previous != null) {
                previous.close();
            }
        }
    }

    /**
     * 删除默认桶写入目录内的对象；额外可读桶和其他目录不会因此获得删除权限。
     *
     * @param fileUrl s3://bucket/key 或默认桶的自定义访问地址
     * @return 地址属于写入范围且删除请求成功时为 true，超出范围时为 false
     */
    @Override
    public boolean delete(String fileUrl) {
        ObjectReference reference = extractReference(fileUrl);
        if (reference == null
                || !config.getBucket().equals(reference.bucket())
                || !reference.key().startsWith(keyPrefix)) {
            return false;
        }
        client.deleteObject(DeleteObjectRequest.builder()
                .bucket(config.getBucket())
                .key(reference.key())
                .build());
        return true;
    }

    /**
     * 流式下载默认桶任意目录或额外可读桶中的对象，不将上传目录再次拼入已有 key。
     * 调用方须先完成文件归属鉴权；可读桶配置只限定存储范围，不代替用户授权。
     *
     * @param fileUrl s3://bucket/key；自定义访问地址仅映射到默认桶
     * @return 需由调用方关闭的文件流，缺少名称元数据时使用 key 的最后一段
     * @throws IOException 地址无效、桶未开放读取或对象不存在时抛出
     */
    @Override
    public StoredFile open(String fileUrl) throws IOException {
        ObjectReference reference = extractReference(fileUrl);
        if (reference == null || !readableBuckets.contains(reference.bucket())) {
            throw new IOException("文件路径无效");
        }
        ResponseInputStream<GetObjectResponse> stream;
        try {
            stream = client.getObject(GetObjectRequest.builder()
                    .bucket(reference.bucket())
                    .key(reference.key())
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
                        : reference.key().substring(reference.key().lastIndexOf('/') + 1),
                StringUtils.hasText(response.contentType())
                        ? response.contentType()
                        : "application/octet-stream",
                response.contentLength() == null
                        ? -1
                        : response.contentLength());
    }

    /**
     * 为默认桶的完整对象 key 生成稳定标识；此处不再添加 key-prefix，避免目录被重复拼接。
     *
     * @param key 已包含桶内目录的完整对象 key，通常取自上传结果的 filename
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
     * 生成带写入目录的唯一对象 key；日期与 UUID 保留原有布局，空前缀兼容历史行为。
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
        return keyPrefix + LocalDate.now().format(
                DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                + "/"
                + UUID.randomUUID()
                + extension;
    }

    /**
     * 从存储标识中分离桶与完整 key，读取与删除随后各自校验允许的操作范围。
     * 不把任意 HTTP 地址解析为新 endpoint，也不解码 key，避免改变含百分号等字符的对象名称。
     *
     * @param fileUrl s3://bucket/key 或默认桶配置的访问地址
     * @return 对象位置；地址或 key 无效时返回 null，不发出存储请求
     */
    private ObjectReference extractReference(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }
        String key = fileUrl;
        String bucket = config.getBucket();
        if (key.startsWith("s3://")) {
            int separator = key.indexOf('/', "s3://".length());
            if (separator < 0) {
                return null;
            }
            bucket = key.substring("s3://".length(), separator);
            key = key.substring(separator + 1);
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
        return !StringUtils.hasText(bucket)
                || key.isBlank()
                || key.startsWith("/")
                || key.contains("..")
                || key.contains("\\")
                ? null
                : new ObjectReference(bucket, key);
    }

    /**
     * 统一配置目录的首尾斜杠，拒绝无法通过既有对象 key 校验的目录，避免上传后无法读取或删除。
     *
     * @param prefix 可为空的桶内目录，允许带首尾斜杠
     * @return 空字符串或带一个结尾斜杠的目录前缀，用于上传及删除边界匹配
     * @throws IllegalStateException 目录含相对路径、反斜杠、空路径段或控制字符时抛出
     */
    private static String normalizeKeyPrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "";
        }
        String normalized = prefix.trim().replaceAll("^/+|/+$", "");
        if (normalized.contains("..") || normalized.contains("\\")
                || normalized.contains("//") || normalized.equals(".")
                || normalized.startsWith("./") || normalized.contains("/./")
                || normalized.endsWith("/.")
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException("Object storage key-prefix must be a bucket-relative directory");
        }
        return normalized.isEmpty() ? "" : normalized + "/";
    }

    /**
     * 默认桶始终可读，额外桶采用精确名称匹配；空环境变量视为未配置，不支持通配符或 URL。
     *
     * @param buckets 额外允许下载的桶名；这些桶不会获得写入或删除权限
     * @return 初始化时固定的读取范围，避免运行时配置集合变更造成权限漂移
     * @throws IllegalStateException 配置值不是桶名时抛出
     */
    private Set<String> readableBuckets(List<String> buckets) {
        Set<String> allowed = new HashSet<>();
        allowed.add(config.getBucket());
        if (buckets != null) {
            for (String bucket : buckets) {
                if (!StringUtils.hasText(bucket)) {
                    continue;
                }
                String name = bucket.trim();
                if (!name.matches("[a-z0-9][a-z0-9.-]*")) {
                    throw new IllegalStateException("Object storage readable-buckets must contain bucket names only");
                }
                allowed.add(name);
            }
        }
        return Set.copyOf(allowed);
    }

    /** 桶与 key 分开保留，使跨桶下载无需改变客户端或重写对象路径。 */
    private record ObjectReference(String bucket, String key) {
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
