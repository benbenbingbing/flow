package com.workflow.service.migration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.migration.ConfigMigrationAsset;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 配置迁移发布包编解码器。
 *
 * <p>负责 wfpack 发布包的打包与解包：编码时将迁移资产快照、依赖清单、清单文件、
 * 校验文件与 HMAC 签名打包成 zip；解码时校验大小/路径/签名/校验和并解析为资产结构。</p>
 */
@Component
@RequiredArgsConstructor
public class ConfigMigrationPackageCodec {

    private static final int FORMAT_VERSION = 1;              // 发布包格式版本
    private static final int MAX_ENTRY_COUNT = 500;           // 单包最大条目数
    private static final int MAX_ENTRY_SIZE = 20 * 1024 * 1024;   // 单个条目最大字节数(20MB)
    private static final int MAX_TOTAL_SIZE = 100 * 1024 * 1024;  // 解压后最大总字节数(100MB)

    private final ObjectMapper objectMapper;

    @Value("${config.migration.signing-key:workflow-config-migration-development-key}")
    private String signingKey;            // 发布包 HMAC 签名密钥

    @Value("${config.migration.environment-name:local}")
    private String environmentName;       // 当前环境名称(写入清单)

    /**
     * 将迁移资产列表编码为 wfpack 发布包。
     *
     * <p>对每个资产按选择配置裁剪快照并写入条目，流程资产额外写入 BPMN，
     * 实体资产额外写入表单/列表明细；随后生成依赖清单、清单文件、校验文件与签名。</p>
     *
     * @param packageNo    发布包编号
     * @param migrationTag 迁移标签
     * @param assets       待打包的迁移资产列表
     * @param selections   资产ID -> 快照选择配置(可为空)
     * @return 编码后的发布包数据
     */
    public EncodedPackage encode(String packageNo,
                                 String migrationTag,
                                 List<ConfigMigrationAsset> assets,
                                 Map<String, Object> selections) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        List<Map<String, Object>> manifestAssets = new ArrayList<>();
        List<Map<String, Object>> packageDependencies = new ArrayList<>();

        for (ConfigMigrationAsset asset : assets) {
            Map<String, Object> snapshot = readMap(asset.getSnapshotJson());
            Map<String, Object> selectedSnapshot = selectSnapshot(snapshot,
                    selections == null ? null : selections.get(asset.getId()));
            String path = assetPath(asset);
            byte[] selectedSnapshotBytes = writeBytes(selectedSnapshot);
            entries.put(path, selectedSnapshotBytes);

            if (ConfigMigrationAssetService.PROCESS.equals(asset.getAssetType())) {
                Object bpmnXml = selectedSnapshot.get("bpmnXml");
                if (bpmnXml instanceof String xml && StringUtils.hasText(xml)) {
                    entries.put("assets/processes/" + safe(asset.getBusinessKey()) + ".bpmn",
                            xml.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                addEntityDetailEntries(asset, selectedSnapshot, entries);
            }

            Map<String, Object> manifestAsset = new LinkedHashMap<>();
            manifestAsset.put("assetType", asset.getAssetType());
            manifestAsset.put("businessKey", asset.getBusinessKey());
            manifestAsset.put("assetName", asset.getAssetName());
            manifestAsset.put("sourceVersion", asset.getSourceVersion());
            manifestAsset.put("sourceHash", sha256(selectedSnapshotBytes));
            manifestAsset.put("fullSourceHash", asset.getContentHash());
            manifestAsset.put("snapshotSchemaVersion", asset.getSnapshotSchemaVersion());
            manifestAsset.put("path", path);
            manifestAsset.put("selection", selections == null ? null : selections.get(asset.getId()));
            manifestAssets.add(manifestAsset);

            packageDependencies.addAll(readList(asset.getDependenciesJson()));
        }

        entries.put("dependencies.json", writeBytes(deduplicateDependencies(packageDependencies)));

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("formatVersion", FORMAT_VERSION);
        manifest.put("packageNo", packageNo);
        manifest.put("migrationTag", migrationTag);
        manifest.put("sourceEnvironment", environmentName);
        manifest.put("createdAt", LocalDateTime.now().toString());
        manifest.put("assets", manifestAssets);
        entries.put("manifest.json", writeBytes(manifest));

        Map<String, String> checksums = new LinkedHashMap<>();
        entries.forEach((path, value) -> checksums.put(path, sha256(value)));
        byte[] checksumsBytes = writeBytes(checksums);
        entries.put("checksums.json", checksumsBytes);
        String signature = hmac(checksumsBytes);
        entries.put("signature.sig", signature.getBytes(StandardCharsets.UTF_8));

        byte[] packageData = zip(entries);
        return new EncodedPackage(packageData, sha256(packageData), signature,
                packageNo + ".wfpack", manifest);
    }

    /**
     * 解码 wfpack 发布包二进制为结构化资产数据。
     *
     * <p>依次校验：非空与大小上限、zip 解压条目数/单条目大小/总大小/路径合法性、
     * 清单存在性、HMAC 签名、每个条目的校验和、清单格式版本，最终还原资产列表。</p>
     *
     * @param packageData 发布包二进制内容
     * @return 解码后的发布包数据
     * @throws IllegalArgumentException 包内容为空、超限、签名或校验失败、格式不支持等
     */
    public DecodedPackage decode(byte[] packageData) {
        if (packageData == null || packageData.length == 0) {
            throw new IllegalArgumentException("发布包内容为空");
        }
        if (packageData.length > MAX_TOTAL_SIZE) {
            throw new IllegalArgumentException("发布包超过最大限制 100MB");
        }

        Map<String, byte[]> entries = unzip(packageData);
        byte[] manifestBytes = requiredEntry(entries, "manifest.json");
        byte[] checksumBytes = requiredEntry(entries, "checksums.json");
        String signature = new String(requiredEntry(entries, "signature.sig"), StandardCharsets.UTF_8).trim();
        if (!MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8),
                hmac(checksumBytes).getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("发布包签名校验失败");
        }

        Map<String, String> checksums = readMap(checksumBytes, new TypeReference<>() {});
        checksums.forEach((path, expected) -> {
            byte[] value = requiredEntry(entries, path);
            String actual = sha256(value);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    actual.getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("发布包文件校验失败: " + path);
            }
        });

        Map<String, Object> manifest = readMap(manifestBytes, new TypeReference<>() {});
        int formatVersion = Integer.parseInt(String.valueOf(manifest.getOrDefault("formatVersion", 0)));
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("不支持的发布包格式版本: " + formatVersion);
        }

        List<Map<String, Object>> manifestAssets = castMapList(manifest.get("assets"));
        List<DecodedAsset> assets = new ArrayList<>();
        for (Map<String, Object> asset : manifestAssets) {
            String path = String.valueOf(asset.get("path"));
            Map<String, Object> snapshot = readMap(requiredEntry(entries, path), new TypeReference<>() {});
            assets.add(new DecodedAsset(
                    String.valueOf(asset.get("assetType")),
                    String.valueOf(asset.get("businessKey")),
                    String.valueOf(asset.get("assetName")),
                    Integer.parseInt(String.valueOf(asset.get("sourceVersion"))),
                    String.valueOf(asset.get("sourceHash")),
                    snapshot,
                    castMapList(snapshot.get("dependencies"))));
        }
        return new DecodedPackage(
                String.valueOf(manifest.get("packageNo")),
                String.valueOf(manifest.get("migrationTag")),
                String.valueOf(manifest.getOrDefault("sourceEnvironment", "")),
                sha256(packageData),
                signature,
                manifest,
                assets);
    }

    private Map<String, Object> selectSnapshot(Map<String, Object> snapshot, Object rawSelection) {
        if (!(rawSelection instanceof Map<?, ?> selectionMap)) {
            return snapshot;
        }
        Object full = selectionMap.get("full");
        if (Boolean.TRUE.equals(full) || "true".equalsIgnoreCase(String.valueOf(full))) {
            return snapshot;
        }
        Set<String> sections = stringSet(selectionMap.get("sections"));
        if (sections.isEmpty()) {
            return snapshot;
        }

        Map<String, Object> selected = new LinkedHashMap<>();
        copyIfPresent(snapshot, selected, "schemaVersion", "assetType", "businessKey", "assetName",
                "definition", "dependencies");
        for (String section : sections) {
            if (snapshot.containsKey(section)) {
                selected.put(section, snapshot.get(section));
            }
        }
        filterByKey(selected, "forms", "formKey", stringSet(selectionMap.get("formKeys")));
        filterByKey(selected, "lists", "listKey", stringSet(selectionMap.get("listKeys")));
        return selected;
    }

    private void addEntityDetailEntries(ConfigMigrationAsset asset,
                                        Map<String, Object> snapshot,
                                        Map<String, byte[]> entries) {
        for (Map<String, Object> form : castMapList(snapshot.get("forms"))) {
            String formKey = String.valueOf(form.get("formKey"));
            entries.put("assets/forms/" + safe(asset.getBusinessKey()) + "/" + safe(formKey) + ".json",
                    writeBytes(form));
        }
        for (Map<String, Object> list : castMapList(snapshot.get("lists"))) {
            String listKey = String.valueOf(list.get("listKey"));
            entries.put("assets/lists/" + safe(asset.getBusinessKey()) + "/" + safe(listKey) + ".json",
                    writeBytes(list));
        }
    }

    private void filterByKey(Map<String, Object> snapshot, String section, String keyName, Set<String> keys) {
        if (keys.isEmpty() || !snapshot.containsKey(section)) {
            return;
        }
        List<Map<String, Object>> filtered = castMapList(snapshot.get(section)).stream()
                .filter(value -> keys.contains(String.valueOf(value.get(keyName))))
                .toList();
        snapshot.put(section, filtered);
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) {
                target.put(key, source.get(key));
            }
        }
    }

    private List<Map<String, Object>> deduplicateDependencies(List<Map<String, Object>> dependencies) {
        Map<String, Map<String, Object>> values = new LinkedHashMap<>();
        for (Map<String, Object> dependency : dependencies) {
            values.put(dependency.get("type") + ":" + dependency.get("key"), dependency);
        }
        return new ArrayList<>(values.values());
    }

    private String assetPath(ConfigMigrationAsset asset) {
        String directory = ConfigMigrationAssetService.ENTITY.equals(asset.getAssetType())
                ? "assets/entities/" : "assets/processes/";
        return directory + safe(asset.getBusinessKey()) + "-v" + asset.getSourceVersion() + ".json";
    }

    private String safe(String value) {
        String normalized = value == null ? "unnamed" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "-");
        return normalized.isBlank() ? "unnamed" : normalized;
    }

    private byte[] zip(Map<String, byte[]> entries) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    ZipEntry zipEntry = new ZipEntry(entry.getKey());
                    zipEntry.setTime(0);
                    zip.putNextEntry(zipEntry);
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("发布包生成失败", e);
        }
    }

    private Map<String, byte[]> unzip(byte[] data) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        int totalSize = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String path = entry.getName();
                validateEntryPath(path);
                if (entries.size() >= MAX_ENTRY_COUNT) {
                    throw new IllegalArgumentException("发布包文件数量超过限制");
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    if (output.size() > MAX_ENTRY_SIZE) {
                        throw new IllegalArgumentException("发布包文件超过 20MB: " + path);
                    }
                }
                totalSize += output.size();
                if (totalSize > MAX_TOTAL_SIZE) {
                    throw new IllegalArgumentException("发布包解压后超过 100MB");
                }
                if (entries.put(path, output.toByteArray()) != null) {
                    throw new IllegalArgumentException("发布包包含重复路径: " + path);
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("发布包不是有效的 wfpack 文件", e);
        }
        return entries;
    }

    private void validateEntryPath(String path) {
        if (!StringUtils.hasText(path) || path.startsWith("/") || path.contains("../")
                || path.contains("..\\") || path.contains(":")) {
            throw new IllegalArgumentException("发布包包含非法路径: " + path);
        }
    }

    private byte[] requiredEntry(Map<String, byte[]> entries, String path) {
        byte[] value = entries.get(path);
        if (value == null) {
            throw new IllegalArgumentException("发布包缺少文件: " + path);
        }
        return value;
    }

    private String hmac(byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value));
        } catch (Exception e) {
            throw new IllegalStateException("发布包签名失败", e);
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception e) {
            throw new IllegalStateException("发布包哈希计算失败", e);
        }
    }

    private byte[] writeBytes(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new IllegalStateException("发布包 JSON 序列化失败", e);
        }
    }

    private Map<String, Object> readMap(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("迁移资产快照格式错误", e);
        }
    }

    private List<Map<String, Object>> readList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("迁移资产依赖格式错误", e);
        }
    }

    private <T> T readMap(byte[] value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("发布包 JSON 格式错误", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castMapList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> converted = new LinkedHashMap<>();
                map.forEach((key, child) -> converted.put(String.valueOf(key), child));
                result.add(converted);
            }
        }
        return result;
    }

    private Set<String> stringSet(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return Set.of();
        }
        return collection.stream()
                .map(String::valueOf)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /** 编码后的发布包数据(二进制内容、校验和、签名、文件名、清单)。 */
    public record EncodedPackage(byte[] data,
                                 String checksum,
                                 String signature,
                                 String fileName,
                                 Map<String, Object> manifest) {
    }

    /** 解码后的发布包数据(编号、标签、源环境、校验和、签名、清单、资产列表)。 */
    public record DecodedPackage(String packageNo,
                                 String migrationTag,
                                 String sourceEnvironment,
                                 String checksum,
                                 String signature,
                                 Map<String, Object> manifest,
                                 List<DecodedAsset> assets) {
    }

    /** 解码后的单个迁移资产(类型、键、名称、源版本、源哈希、快照、依赖列表)。 */
    public record DecodedAsset(String assetType,
                               String businessKey,
                               String assetName,
                               Integer sourceVersion,
                               String sourceHash,
                               Map<String, Object> snapshot,
                               List<Map<String, Object>> dependencies) {
    }
}
