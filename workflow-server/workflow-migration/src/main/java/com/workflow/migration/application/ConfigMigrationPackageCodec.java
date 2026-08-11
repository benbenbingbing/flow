package com.workflow.migration.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAsset;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.PostConstruct;
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

    private static final int FORMAT_VERSION = 2;              // 当前发布包格式版本
    private static final int MIN_SUPPORTED_FORMAT_VERSION = 1;
    private static final int MAX_ENTRY_COUNT = 500;           // 单包最大条目数
    private static final int MAX_ENTRY_SIZE = 20 * 1024 * 1024;   // 单个条目最大字节数(20MB)
    private static final int MAX_TOTAL_SIZE = 100 * 1024 * 1024;  // 解压后最大总字节数(100MB)
    static final String SELECTION_METADATA = "_selection";
    static final String TARGET_ONLY_DEPENDENCY = "targetOnly";

    private final ObjectMapper objectMapper;

    @Value("${config.migration.signing-key}")
    private String signingKey;            // 发布包 HMAC 签名密钥

    @Value("${config.migration.environment-name:local}")
    private String environmentName;       // 当前环境名称(写入清单)

    @PostConstruct
    void validateSigningKey() {
        if (!StringUtils.hasText(signingKey)
                || signingKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "CONFIG_MIGRATION_SIGNING_KEY must contain at least 32 bytes");
        }
        String normalized = signingKey.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("workflow-config-migration")
                || normalized.contains("replace-with")
                || normalized.contains("changeme")) {
            throw new IllegalStateException(
                    "CONFIG_MIGRATION_SIGNING_KEY cannot use a public example value");
        }
    }

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
            Map<String, Object> selection = normalizeSelection(
                    selections == null ? null : selections.get(asset.getId()));
            Map<String, Object> selectedSnapshot = selectSnapshot(snapshot, selection);
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
            manifestAsset.put("sourceHash", hashSnapshot(selectedSnapshot));
            manifestAsset.put("fullSourceHash", asset.getContentHash());
            manifestAsset.put("snapshotSchemaVersion", asset.getSnapshotSchemaVersion());
            manifestAsset.put("path", path);
            manifestAsset.put("selection", selection);
            manifestAssets.add(manifestAsset);

            packageDependencies.addAll(castMapList(selectedSnapshot.get("dependencies")));
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
        if (formatVersion < MIN_SUPPORTED_FORMAT_VERSION
                || formatVersion > FORMAT_VERSION) {
            throw new IllegalArgumentException("不支持的发布包格式版本: " + formatVersion);
        }

        List<Map<String, Object>> manifestAssets = castMapList(manifest.get("assets"));
        List<DecodedAsset> assets = new ArrayList<>();
        for (Map<String, Object> asset : manifestAssets) {
            String path = String.valueOf(asset.get("path"));
            byte[] snapshotBytes = requiredEntry(entries, path);
            Map<String, Object> snapshot = readMap(snapshotBytes, new TypeReference<>() {});
            Map<String, Object> selection = normalizeSelection(asset.get("selection"));
            String packagedSourceHash = String.valueOf(asset.get("sourceHash"));
            String verifiedHash = formatVersion == 1
                    ? sha256(snapshotBytes)
                    : hashSnapshot(snapshot);
            if (!MessageDigest.isEqual(
                    packagedSourceHash.getBytes(StandardCharsets.UTF_8),
                    verifiedHash.getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("发布包资产哈希校验失败: " + path);
            }
            snapshot = selectSnapshot(snapshot, selection);
            String normalizedSourceHash = hashSnapshot(snapshot);
            assets.add(new DecodedAsset(
                    String.valueOf(asset.get("assetType")),
                    String.valueOf(asset.get("businessKey")),
                    String.valueOf(asset.get("assetName")),
                    Integer.parseInt(String.valueOf(asset.get("sourceVersion"))),
                    normalizedSourceHash,
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

    Map<String, Object> selectSnapshot(Map<String, Object> snapshot, Object rawSelection) {
        return selectSnapshot(snapshot, rawSelection, true);
    }

    private Map<String, Object> selectSnapshot(Map<String, Object> snapshot,
                                               Object rawSelection,
                                               boolean requireSelectedKeys) {
        Map<String, Object> selection = normalizeSelection(rawSelection);
        if (Boolean.TRUE.equals(selection.get("full"))) {
            Map<String, Object> selected = new LinkedHashMap<>(snapshot);
            selected.put(SELECTION_METADATA, selection);
            return selected;
        }

        Set<String> sections = stringSet(selection.get("sections"));
        Map<String, Object> selected = new LinkedHashMap<>();
        copyIfPresent(snapshot, selected,
                "schemaVersion", "assetType", "businessKey", "assetName");
        selected.put("definition", selectedDefinition(snapshot, sections));

        for (String section : expandedSections(
                String.valueOf(snapshot.get("assetType")), sections)) {
            if (snapshot.containsKey(section)) {
                selected.put(section, snapshot.get(section));
            }
        }

        Set<String> formKeys = stringSet(selection.get("formKeys"));
        Set<String> listKeys = stringSet(selection.get("listKeys"));
        filterByKey(selected, "forms", "formKey", formKeys);
        filterByKey(selected, "lists", "listKey", listKeys);
        if (requireSelectedKeys) {
            validateSelectedKeys(snapshot, selected, sections, formKeys, listKeys);
        }
        addSelectedSupportSections(snapshot, selected, sections);
        selected.put("dependencies", selectDependencies(snapshot, selected, sections));
        selected.put(SELECTION_METADATA, selection);
        return selected;
    }

    Map<String, Object> selectSnapshot(String snapshotJson, Object rawSelection) {
        return selectSnapshot(readMap(snapshotJson), rawSelection);
    }

    Map<String, Object> selectSnapshotAllowingMissingKeys(
            String snapshotJson,
            Object rawSelection) {
        return selectSnapshot(readMap(snapshotJson), rawSelection, false);
    }

    Map<String, Object> normalizeSelection(Object rawSelection) {
        if (!(rawSelection instanceof Map<?, ?> rawMap)) {
            return Map.of("full", true);
        }
        Map<String, Object> selection = new LinkedHashMap<>();
        Object full = rawMap.get("full");
        if (Boolean.TRUE.equals(full)
                || "true".equalsIgnoreCase(String.valueOf(full))) {
            selection.put("full", true);
            return selection;
        }
        Set<String> sections = stringSet(rawMap.get("sections"));
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("细粒度导出至少需要选择一个配置部分");
        }
        selection.put("full", false);
        selection.put("sections", sections.stream().sorted().toList());
        Set<String> formKeys = stringSet(rawMap.get("formKeys"));
        if (!formKeys.isEmpty()) {
            if (!sections.contains("forms")) {
                throw new IllegalArgumentException("指定 formKeys 时必须选择 forms 配置部分");
            }
            selection.put("formKeys", formKeys.stream().sorted().toList());
        }
        Set<String> listKeys = stringSet(rawMap.get("listKeys"));
        if (!listKeys.isEmpty()) {
            if (!sections.contains("lists")) {
                throw new IllegalArgumentException("指定 listKeys 时必须选择 lists 配置部分");
            }
            selection.put("listKeys", listKeys.stream().sorted().toList());
        }
        return selection;
    }

    Map<String, Object> mergeSelections(Object leftValue, Object rightValue) {
        Map<String, Object> left = normalizeSelection(leftValue);
        Map<String, Object> right = normalizeSelection(rightValue);
        if (Boolean.TRUE.equals(left.get("full"))
                || Boolean.TRUE.equals(right.get("full"))) {
            return Map.of("full", true);
        }
        Set<String> sections = new LinkedHashSet<>(stringSet(left.get("sections")));
        sections.addAll(stringSet(right.get("sections")));
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("full", false);
        merged.put("sections", sections.stream().sorted().toList());
        mergeSelectedKeys(merged, "formKeys", "forms", left, right);
        mergeSelectedKeys(merged, "listKeys", "lists", left, right);
        return merged;
    }

    Map<String, Object> selectionOf(Map<String, Object> snapshot) {
        return normalizeSelection(snapshot.get(SELECTION_METADATA));
    }

    String selectionScopeKey(Map<String, Object> snapshot) {
        Map<String, Object> selection = selectionOf(snapshot);
        if (Boolean.TRUE.equals(selection.get("full"))) {
            return "FULL";
        }
        return "PARTIAL:" + sha256(writeBytes(selection)).substring(0, 32);
    }

    String hashSelectedSnapshot(String snapshotJson, Object selection) {
        return hashSnapshot(selectSnapshotAllowingMissingKeys(
                snapshotJson, selection));
    }

    String hashSnapshot(Map<String, Object> snapshot) {
        Map<String, Object> content = new LinkedHashMap<>(snapshot);
        content.remove(SELECTION_METADATA);
        return sha256(writeBytes(content));
    }

    private void mergeSelectedKeys(Map<String, Object> merged,
                                   String keyName,
                                   String section,
                                   Map<String, Object> left,
                                   Map<String, Object> right) {
        if (!stringSet(merged.get("sections")).contains(section)) {
            return;
        }
        Set<String> leftSections = stringSet(left.get("sections"));
        Set<String> rightSections = stringSet(right.get("sections"));
        Set<String> leftKeys = stringSet(left.get(keyName));
        Set<String> rightKeys = stringSet(right.get(keyName));
        boolean leftSelectsAll = leftSections.contains(section) && leftKeys.isEmpty();
        boolean rightSelectsAll = rightSections.contains(section) && rightKeys.isEmpty();
        if (leftSelectsAll || rightSelectsAll) {
            return;
        }
        Set<String> mergedKeys = new LinkedHashSet<>(leftKeys);
        mergedKeys.addAll(rightKeys);
        if (!mergedKeys.isEmpty()) {
            merged.put(keyName, mergedKeys.stream().sorted().toList());
        }
    }

    private Map<String, Object> selectedDefinition(Map<String, Object> snapshot,
                                                   Set<String> sections) {
        Map<String, Object> definition = mapValue(snapshot.get("definition"));
        if (sections.contains("definition")) {
            return definition;
        }
        Map<String, Object> identity = new LinkedHashMap<>();
        String assetType = String.valueOf(snapshot.get("assetType"));
        if (ConfigMigrationAssetService.PROCESS.equals(assetType)) {
            copyIfPresent(definition, identity, "processKey", "processName");
        } else {
            copyIfPresent(definition, identity, "entityCode", "entityName", "storageMode");
        }
        return identity;
    }

    private Set<String> expandedSections(String assetType, Set<String> sections) {
        Set<String> expanded = new LinkedHashSet<>();
        for (String section : sections) {
            switch (section) {
                case "fields" -> {
                    expanded.add("fields");
                    expanded.add("relations");
                }
                case "statuses" -> {
                    expanded.add("statuses");
                    expanded.add("codeRule");
                }
                case "dataPermissions" -> {
                    expanded.add("scopePolicies");
                    expanded.add("scopeBindings");
                }
                case "bpmnXml" -> {
                    expanded.add("bpmnXml");
                    expanded.add("nodes");
                    expanded.add("nodeForms");
                }
                case "definition" -> {
                    // Definition is handled separately so identity metadata is always available.
                }
                default -> expanded.add(section);
            }
        }
        if ((ConfigMigrationAssetService.ENTITY.equals(assetType)
                || ConfigMigrationAssetService.SYSTEM_ENTITY_UI.equals(assetType))
                && (sections.contains("forms") || sections.contains("lists"))) {
            expanded.add("referencedFields");
        }
        return expanded;
    }

    private void addSelectedSupportSections(Map<String, Object> source,
                                            Map<String, Object> selected,
                                            Set<String> sections) {
        boolean uiSectionSelected = selected.containsKey("forms")
                || selected.containsKey("lists");
        if (!uiSectionSelected
                && !sections.contains("dataSources")
                && !sections.contains("extensions")) {
            return;
        }
        Set<String> dataSourceCodes = new LinkedHashSet<>();
        collectValuesForKeys(selected, Set.of(
                "serviceCode", "dataSourceCode", "queryDataSourceCode"), dataSourceCodes);
        if (sections.contains("dataSources")) {
            copyIfPresent(source, selected, "dataSources");
        } else if (!dataSourceCodes.isEmpty()) {
            selected.put("dataSources", castMapList(source.get("dataSources")).stream()
                    .filter(value -> dataSourceCodes.contains(String.valueOf(value.get("sourceCode"))))
                    .toList());
        }

        Set<String> extensionKeys = new LinkedHashSet<>();
        collectValuesForKeys(selected, Set.of(
                "customComponent", "renderComponent", "componentName"), extensionKeys);
        if (sections.contains("extensions")) {
            copyIfPresent(source, selected, "extensions");
        } else if (!extensionKeys.isEmpty()) {
            selected.put("extensions", castMapList(source.get("extensions")).stream()
                    .filter(value -> extensionKeys.contains(String.valueOf(value.get("extensionKey"))))
                    .toList());
        }

        if (selected.containsKey("referencedFields")) {
            Set<String> referencedFieldCodes = new LinkedHashSet<>();
            collectValuesForKeys(selected, Set.of("fieldCode"), referencedFieldCodes);
            selected.put("referencedFields", castStringList(source.get("referencedFields")).stream()
                    .filter(referencedFieldCodes::contains)
                    .toList());
        }
    }

    private void collectValuesForKeys(Object value,
                                      Set<String> names,
                                      Set<String> result) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> {
                if (names.contains(String.valueOf(key))
                        && child instanceof String text
                        && StringUtils.hasText(text)) {
                    result.add(text);
                }
                collectValuesForKeys(child, names, result);
            });
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(child -> collectValuesForKeys(child, names, result));
        } else if (value instanceof String text
                && (text.trim().startsWith("{") || text.trim().startsWith("["))) {
            try {
                collectValuesForKeys(objectMapper.readValue(text, Object.class), names, result);
            } catch (Exception ignored) {
                // Plain text configuration is not a structured reference document.
            }
        }
    }

    private List<Map<String, Object>> selectDependencies(Map<String, Object> source,
                                                          Map<String, Object> selected,
                                                          Set<String> sections) {
        Map<String, Object> searchable = new LinkedHashMap<>(selected);
        searchable.remove("dependencies");
        searchable.remove(SELECTION_METADATA);
        List<Map<String, Object>> dependencies = castMapList(source.get("dependencies")).stream()
                .filter(dependency -> containsReference(
                        searchable, String.valueOf(dependency.get("key"))))
                .map(dependency -> (Map<String, Object>)
                        new LinkedHashMap<String, Object>(dependency))
                .toList();
        List<Map<String, Object>> result = new ArrayList<>(dependencies);
        String assetType = String.valueOf(source.get("assetType"));
        String businessKey = String.valueOf(source.get("businessKey"));
        if (ConfigMigrationAssetService.ENTITY.equals(assetType)
                && !(sections.contains("definition") && sections.contains("fields"))) {
            result.add(targetOnlyDependency(
                    ConfigMigrationAssetService.ENTITY,
                    businessKey,
                    "细粒度配置所属实体"));
        } else if (ConfigMigrationAssetService.PROCESS.equals(assetType)
                && !(sections.contains("definition") && sections.contains("bpmnXml"))) {
            result.add(targetOnlyDependency(
                    ConfigMigrationAssetService.PROCESS,
                    businessKey,
                    "细粒度配置所属流程"));
        } else if (ConfigMigrationAssetService.SYSTEM_ENTITY_UI.equals(assetType)) {
            result.add(targetOnlyDependency(
                    ConfigMigrationAssetService.ENTITY,
                    businessKey,
                    "系统UI配置所属实体"));
        }
        return deduplicateDependencies(result);
    }

    private Map<String, Object> targetOnlyDependency(String type,
                                                     String key,
                                                     String source) {
        Map<String, Object> dependency = new LinkedHashMap<>();
        dependency.put("type", type);
        dependency.put("key", key);
        dependency.put("required", true);
        dependency.put("source", source);
        dependency.put(TARGET_ONLY_DEPENDENCY, true);
        return dependency;
    }

    private boolean containsReference(Object value, String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().anyMatch(child -> containsReference(child, key));
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(child -> containsReference(child, key));
        }
        return value instanceof String text && text.contains(key);
    }

    private void validateSelectedKeys(Map<String, Object> source,
                                      Map<String, Object> selected,
                                      Set<String> sections,
                                      Set<String> formKeys,
                                      Set<String> listKeys) {
        validateSelectedKeys(source, selected, sections, "forms", "formKey", formKeys);
        validateSelectedKeys(source, selected, sections, "lists", "listKey", listKeys);
    }

    private void validateSelectedKeys(Map<String, Object> source,
                                      Map<String, Object> selected,
                                      Set<String> sections,
                                      String section,
                                      String keyName,
                                      Set<String> requestedKeys) {
        if (!sections.contains(section) || requestedKeys.isEmpty()) {
            return;
        }
        Set<String> available = castMapList(source.get(section)).stream()
                .map(value -> String.valueOf(value.get(keyName)))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> missing = new LinkedHashSet<>(requestedKeys);
        missing.removeAll(available);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "细粒度导出包含不存在的 " + keyName + ": " + String.join(", ", missing));
        }
        if (castMapList(selected.get(section)).isEmpty()) {
            throw new IllegalArgumentException("细粒度导出没有匹配到 " + section);
        }
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

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        map.forEach((key, child) -> converted.put(String.valueOf(key), child));
        return converted;
    }

    private List<String> castStringList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream().map(String::valueOf).toList();
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
        String directory = switch (asset.getAssetType()) {
            case ConfigMigrationAssetService.ENTITY ->
                    "assets/entities/";
            case ConfigMigrationAssetService.SYSTEM_ENTITY_UI ->
                    "assets/system-entity-ui/";
            case ConfigMigrationAssetService.WORK_CALENDAR ->
                    "assets/work-calendars/";
            case ConfigMigrationAssetService.TASK_SLA_POLICY ->
                    "assets/task-sla-policies/";
            case ConfigMigrationAssetService.PROCESS ->
                    "assets/processes/";
            default -> throw new IllegalArgumentException(
                    "不支持的迁移资产类型: "
                            + asset.getAssetType());
        };
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
