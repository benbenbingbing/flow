package com.workflow.service.migration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.UserContext;
import com.workflow.dto.migration.ConfigEnvironmentMappingRequest;
import com.workflow.dto.migration.ConfigExportRequest;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityForm;
import com.workflow.entity.migration.ConfigAssetBaseline;
import com.workflow.entity.migration.ConfigEnvironmentMapping;
import com.workflow.entity.migration.ConfigExportPackage;
import com.workflow.entity.migration.ConfigExportPackageItem;
import com.workflow.entity.migration.ConfigImportItem;
import com.workflow.entity.migration.ConfigImportPackage;
import com.workflow.entity.migration.ConfigMigrationAsset;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.mapper.SysDictMapper;
import com.workflow.mapper.SysGroupMapper;
import com.workflow.mapper.SysOrganizationMapper;
import com.workflow.mapper.SysRoleMapper;
import com.workflow.mapper.SysUserMapper;
import com.workflow.mapper.migration.ConfigAssetBaselineMapper;
import com.workflow.mapper.migration.ConfigEnvironmentMappingMapper;
import com.workflow.mapper.migration.ConfigExportPackageItemMapper;
import com.workflow.mapper.migration.ConfigExportPackageMapper;
import com.workflow.mapper.migration.ConfigImportItemMapper;
import com.workflow.mapper.migration.ConfigImportPackageMapper;
import com.workflow.mapper.migration.ConfigMigrationAssetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConfigMigrationPackageService {

    private static final DateTimeFormatter PACKAGE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ConfigMigrationAssetService assetService;
    private final ConfigMigrationPackageCodec packageCodec;
    private final ConfigMigrationAssetMapper assetMapper;
    private final ConfigExportPackageMapper exportPackageMapper;
    private final ConfigExportPackageItemMapper exportItemMapper;
    private final ConfigImportPackageMapper importPackageMapper;
    private final ConfigImportItemMapper importItemMapper;
    private final ConfigAssetBaselineMapper baselineMapper;
    private final ConfigEnvironmentMappingMapper environmentMappingMapper;
    private final EntityDefinitionMapper entityMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityFormMapper formMapper;
    private final ProcessDefinitionConfigMapper processMapper;
    private final SysDictMapper dictMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysGroupMapper groupMapper;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> exportPackage(ConfigExportRequest request) {
        if (request == null || request.getAssetIds() == null || request.getAssetIds().isEmpty()) {
            throw new IllegalArgumentException("请选择至少一个迁移资产");
        }
        List<ConfigMigrationAsset> assets = expandDependencies(request);
        String migrationTag = resolvePackageTag(request.getMigrationTag(), assets);
        String packageNo = "WFP-" + migrationTag + "-" + LocalDateTime.now().format(PACKAGE_TIME)
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);

        ConfigMigrationPackageCodec.EncodedPackage encoded = packageCodec.encode(
                packageNo, migrationTag, assets, request.getSelections());

        ConfigExportPackage exportPackage = new ConfigExportPackage();
        exportPackage.setPackageNo(packageNo);
        exportPackage.setMigrationTag(migrationTag);
        exportPackage.setFileName(encoded.fileName());
        exportPackage.setChecksum(encoded.checksum());
        exportPackage.setSignatureValue(encoded.signature());
        exportPackage.setStatus("READY");
        exportPackage.setAssetCount(assets.size());
        exportPackage.setPackageData(encoded.data());
        exportPackage.setCreatedBy(UserContext.getUsername());
        exportPackage.setCreatedAt(LocalDateTime.now());
        exportPackage.setDownloadCount(0);
        exportPackage.setDeleted(0);
        exportPackageMapper.insert(exportPackage);

        for (ConfigMigrationAsset asset : assets) {
            ConfigExportPackageItem item = new ConfigExportPackageItem();
            item.setPackageId(exportPackage.getId());
            item.setAssetId(asset.getId());
            item.setAssetType(asset.getAssetType());
            item.setBusinessKey(asset.getBusinessKey());
            item.setSourceVersion(asset.getSourceVersion());
            item.setContentHash(asset.getContentHash());
            item.setSelectionJson(writeJson(request.getSelections().get(asset.getId())));
            item.setCreatedAt(LocalDateTime.now());
            exportItemMapper.insert(item);

            asset.setExportStatus("EXPORTED");
            asset.setLastExportAt(LocalDateTime.now());
            asset.setExportCount(Optional.ofNullable(asset.getExportCount()).orElse(0) + 1);
            asset.setUpdatedAt(LocalDateTime.now());
            assetMapper.updateById(asset);
        }
        return exportSummary(exportPackage);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listExports() {
        return exportPackageMapper.selectList(new LambdaQueryWrapper<ConfigExportPackage>()
                        .orderByDesc(ConfigExportPackage::getCreatedAt))
                .stream().map(this::exportSummary).toList();
    }

    @Transactional
    public DownloadFile downloadExport(String id) {
        ConfigExportPackage exportPackage = exportPackageMapper.selectById(id);
        if (exportPackage == null || exportPackage.getPackageData() == null) {
            throw new IllegalArgumentException("导出包不存在: " + id);
        }
        exportPackage.setDownloadCount(Optional.ofNullable(exportPackage.getDownloadCount()).orElse(0) + 1);
        exportPackage.setLastDownloadAt(LocalDateTime.now());
        exportPackageMapper.updateById(exportPackage);
        return new DownloadFile(exportPackage.getFileName(), "application/octet-stream",
                exportPackage.getPackageData());
    }

    @Transactional
    public Map<String, Object> importPackage(MultipartFile file, String sourceEnvironment) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择 wfpack 文件");
        }
        try {
            ConfigMigrationPackageCodec.DecodedPackage decoded = packageCodec.decode(file.getBytes());
            ConfigImportPackage existing = importPackageMapper.selectOne(
                    new LambdaQueryWrapper<ConfigImportPackage>()
                            .eq(ConfigImportPackage::getChecksum, decoded.checksum())
                            .last("LIMIT 1"));
            if (existing != null) {
                return importSummary(existing);
            }

            ConfigImportPackage importPackage = new ConfigImportPackage();
            importPackage.setPackageNo(decoded.packageNo());
            importPackage.setSourceEnvironment(StringUtils.hasText(sourceEnvironment)
                    ? sourceEnvironment.trim() : decoded.sourceEnvironment());
            importPackage.setMigrationTag(decoded.migrationTag());
            importPackage.setFileName(file.getOriginalFilename());
            importPackage.setChecksum(decoded.checksum());
            importPackage.setStatus("UPLOADED");
            importPackage.setPackageData(file.getBytes());
            importPackage.setImportedBy(UserContext.getUsername());
            importPackage.setImportedAt(LocalDateTime.now());
            importPackage.setDeleted(0);
            importPackageMapper.insert(importPackage);

            Set<String> packageAssets = decoded.assets().stream()
                    .map(asset -> asset.assetType() + ":" + asset.businessKey())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            for (ConfigMigrationPackageCodec.DecodedAsset asset : decoded.assets()) {
                ConfigImportItem item = new ConfigImportItem();
                item.setImportPackageId(importPackage.getId());
                item.setAssetType(asset.assetType());
                item.setBusinessKey(asset.businessKey());
                item.setAssetName(asset.assetName());
                item.setSourceVersion(asset.sourceVersion());
                item.setSourceHash(asset.sourceHash());
                item.setSnapshotJson(writeJson(asset.snapshot()));
                item.setDependenciesJson(writeJson(asset.dependencies()));
                item.setComparisonStatus(compare(item));
                item.setMappingStatus(resolveDependencies(asset.dependencies(), packageAssets).resolved()
                        ? "RESOLVED" : "UNRESOLVED");
                item.setPublishStatus("PENDING");
                item.setCreatedAt(LocalDateTime.now());
                item.setUpdatedAt(LocalDateTime.now());
                importItemMapper.insert(item);
            }
            return importSummary(importPackage);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("发布包导入失败: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listImports() {
        return importPackageMapper.selectList(new LambdaQueryWrapper<ConfigImportPackage>()
                        .orderByDesc(ConfigImportPackage::getImportedAt))
                .stream().map(this::importSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<ConfigImportItem> listImportItems(String importId) {
        return importItemMapper.selectList(new LambdaQueryWrapper<ConfigImportItem>()
                .eq(ConfigImportItem::getImportPackageId, importId)
                .orderByAsc(ConfigImportItem::getAssetType)
                .orderByAsc(ConfigImportItem::getBusinessKey));
    }

    @Transactional
    public Map<String, Object> analyze(String importId) {
        ConfigImportPackage importPackage = requiredImport(importId);
        List<ConfigImportItem> items = listImportItems(importId);
        Set<String> packageAssets = items.stream()
                .map(item -> item.getAssetType() + ":" + item.getBusinessKey())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<Map<String, Object>> reports = new ArrayList<>();
        boolean blocked = false;

        for (ConfigImportItem item : items) {
            item.setComparisonStatus(compare(item));
            List<Map<String, Object>> dependencies = readMapList(item.getDependenciesJson());
            DependencyResolution dependencyResolution = resolveDependencies(dependencies, packageAssets);
            item.setMappingStatus(dependencyResolution.resolved() ? "RESOLVED" : "UNRESOLVED");
            List<Map<String, Object>> risks = analyzeRisks(item);
            boolean itemBlocked = !dependencyResolution.resolved()
                    || "CONFLICT".equals(item.getComparisonStatus())
                    || "LOCAL_CHANGED".equals(item.getComparisonStatus())
                    || risks.stream().anyMatch(risk -> "BLOCKING".equals(risk.get("level")));
            blocked = blocked || itemBlocked;

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("itemId", item.getId());
            report.put("assetType", item.getAssetType());
            report.put("businessKey", item.getBusinessKey());
            report.put("comparisonStatus", item.getComparisonStatus());
            report.put("mappingStatus", item.getMappingStatus());
            report.put("missingDependencies", dependencyResolution.missing());
            report.put("risks", risks);
            report.put("blocked", itemBlocked);
            reports.add(report);

            item.setErrorMessage(itemBlocked ? summarizeFailure(dependencyResolution.missing(), risks,
                    item.getComparisonStatus()) : null);
            item.setUpdatedAt(LocalDateTime.now());
            importItemMapper.updateById(item);
        }

        Map<String, Object> validationReport = new LinkedHashMap<>();
        validationReport.put("analyzedAt", LocalDateTime.now());
        validationReport.put("blocked", blocked);
        validationReport.put("items", reports);
        importPackage.setValidationReportJson(writeJson(validationReport));
        importPackage.setStatus(blocked ? "BLOCKED" : "ANALYZED");
        importPackage.setErrorMessage(blocked ? "存在冲突、缺失依赖或危险数据库变更" : null);
        importPackageMapper.updateById(importPackage);
        return validationReport;
    }

    @Transactional
    public void saveMappings(String importId, ConfigEnvironmentMappingRequest request) {
        requiredImport(importId);
        if (request == null || request.getMappings() == null) {
            return;
        }
        for (ConfigEnvironmentMappingRequest.MappingItem value : request.getMappings()) {
            if (!StringUtils.hasText(value.getSourceType())
                    || !StringUtils.hasText(value.getSourceKey())
                    || !StringUtils.hasText(value.getTargetKey())) {
                throw new IllegalArgumentException("映射类型、来源编码和目标编码不能为空");
            }
            ConfigEnvironmentMapping mapping = environmentMappingMapper.selectOne(
                    new LambdaQueryWrapper<ConfigEnvironmentMapping>()
                            .eq(ConfigEnvironmentMapping::getSourceType, value.getSourceType())
                            .eq(ConfigEnvironmentMapping::getSourceKey, value.getSourceKey())
                            .last("LIMIT 1"));
            if (mapping == null) {
                mapping = new ConfigEnvironmentMapping();
                mapping.setSourceType(value.getSourceType());
                mapping.setSourceKey(value.getSourceKey());
                mapping.setCreatedAt(LocalDateTime.now());
            }
            mapping.setTargetKey(value.getTargetKey());
            mapping.setDescription(value.getDescription());
            mapping.setEnabled(value.getEnabled() == null || value.getEnabled());
            mapping.setUpdatedAt(LocalDateTime.now());
            if (mapping.getId() == null) {
                environmentMappingMapper.insert(mapping);
            } else {
                environmentMappingMapper.updateById(mapping);
            }
        }
        analyze(importId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> compare(String importId) {
        ConfigImportPackage importPackage = requiredImport(importId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("package", importSummary(importPackage));
        result.put("items", listImportItems(importId));
        result.put("validationReport", parseJson(importPackage.getValidationReportJson(), Map.of()));
        return result;
    }

    private List<ConfigMigrationAsset> expandDependencies(ConfigExportRequest request) {
        Map<String, ConfigMigrationAsset> selected = new LinkedHashMap<>();
        Deque<ConfigMigrationAsset> queue = new ArrayDeque<>();
        for (String id : request.getAssetIds()) {
            ConfigMigrationAsset asset = assetService.getRequired(id);
            validateExportable(asset);
            if (selected.put(asset.getAssetType() + ":" + asset.getBusinessKey(), asset) == null) {
                queue.add(asset);
            }
        }

        while (!queue.isEmpty()) {
            ConfigMigrationAsset asset = queue.removeFirst();
            for (Map<String, Object> dependency : readMapList(asset.getDependenciesJson())) {
                if (!Boolean.parseBoolean(String.valueOf(dependency.getOrDefault("required", false)))) {
                    continue;
                }
                String type = String.valueOf(dependency.get("type"));
                String key = String.valueOf(dependency.get("key"));
                if (request.getValidateOnlyDependencies().contains(type + ":" + key)) {
                    ensureDependencyExists(type, key);
                    continue;
                }
                ConfigMigrationAsset dependencyAsset = findDependencyAsset(type, key);
                if (dependencyAsset == null) {
                    throw new IllegalArgumentException("缺少可导出的硬依赖: " + type + ":" + key);
                }
                validateExportable(dependencyAsset);
                String assetKey = dependencyAsset.getAssetType() + ":" + dependencyAsset.getBusinessKey();
                if (selected.putIfAbsent(assetKey, dependencyAsset) == null) {
                    queue.add(dependencyAsset);
                }
            }
        }
        return selected.values().stream()
                .sorted(Comparator.comparing(ConfigMigrationAsset::getAssetType)
                        .thenComparing(ConfigMigrationAsset::getBusinessKey))
                .toList();
    }

    private ConfigMigrationAsset findDependencyAsset(String type, String key) {
        if (ConfigMigrationAssetService.ENTITY.equals(type)) {
            return assetService.findLatest(ConfigMigrationAssetService.ENTITY, key);
        }
        if (ConfigMigrationAssetService.PROCESS.equals(type)) {
            return assetService.findLatest(ConfigMigrationAssetService.PROCESS, key);
        }
        if ("FORM".equals(type) && key.startsWith("wf-form://")) {
            String[] segments = key.substring("wf-form://".length()).split("/", 2);
            return segments.length > 0
                    ? assetService.findLatest(ConfigMigrationAssetService.ENTITY, segments[0]) : null;
        }
        return null;
    }

    private void validateExportable(ConfigMigrationAsset asset) {
        if (!ConfigMigrationAssetService.COMPLETE.equals(asset.getSnapshotCompleteness())) {
            throw new IllegalArgumentException("历史资产 " + asset.getBusinessKey() + " 不是完整发布快照，请重新发布");
        }
    }

    private void ensureDependencyExists(String type, String key) {
        if (!isDependencyResolved(type, key, Set.of())) {
            throw new IllegalArgumentException("依赖仅校验失败: " + type + ":" + key);
        }
    }

    private String resolvePackageTag(String requested, List<ConfigMigrationAsset> assets) {
        if (StringUtils.hasText(requested)) {
            return normalizeTag(requested);
        }
        Set<String> tags = assets.stream().map(ConfigMigrationAsset::getMigrationTag)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return tags.size() == 1 ? tags.iterator().next() : assetService.generateMigrationTag();
    }

    private String normalizeTag(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9._-]", "-");
    }

    private String compare(ConfigImportItem item) {
        ConfigMigrationAsset target = assetService.findLatest(item.getAssetType(), item.getBusinessKey());
        item.setTargetBeforeVersion(target == null ? null : target.getSourceVersion());
        item.setTargetBeforeHash(target == null ? null : target.getContentHash());
        if (target == null) {
            return "NEW";
        }
        if (Objects.equals(item.getSourceHash(), target.getContentHash())) {
            return "CONSISTENT";
        }
        ConfigAssetBaseline baseline = baselineMapper.selectOne(new LambdaQueryWrapper<ConfigAssetBaseline>()
                .eq(ConfigAssetBaseline::getAssetType, item.getAssetType())
                .eq(ConfigAssetBaseline::getBusinessKey, item.getBusinessKey())
                .last("LIMIT 1"));
        if (baseline == null) {
            return item.getSourceVersion() != null && target.getSourceVersion() != null
                    && item.getSourceVersion() > target.getSourceVersion() ? "SOURCE_NEWER" : "CONFLICT";
        }
        boolean localChanged = !Objects.equals(target.getContentHash(), baseline.getTargetHash());
        boolean sourceChanged = !Objects.equals(item.getSourceHash(), baseline.getSourceHash());
        if (localChanged && sourceChanged) {
            return "CONFLICT";
        }
        if (localChanged) {
            return "LOCAL_CHANGED";
        }
        return sourceChanged ? "SOURCE_NEWER" : "CONSISTENT";
    }

    private DependencyResolution resolveDependencies(List<Map<String, Object>> dependencies,
                                                     Set<String> packageAssets) {
        List<Map<String, Object>> missing = new ArrayList<>();
        for (Map<String, Object> dependency : dependencies) {
            if (!Boolean.parseBoolean(String.valueOf(dependency.getOrDefault("required", false)))) {
                continue;
            }
            String type = String.valueOf(dependency.get("type"));
            String sourceKey = String.valueOf(dependency.get("key"));
            String targetKey = mappedKey(type, sourceKey);
            if (!isDependencyResolved(type, targetKey, packageAssets)) {
                Map<String, Object> value = new LinkedHashMap<>(dependency);
                value.put("targetKey", targetKey);
                missing.add(value);
            }
        }
        return new DependencyResolution(missing.isEmpty(), missing);
    }

    private boolean isDependencyResolved(String type, String key, Set<String> packageAssets) {
        if (ConfigMigrationAssetService.ENTITY.equals(type)) {
            return packageAssets.contains(type + ":" + key) || entityMapper.findByEntityCode(key).isPresent();
        }
        if (ConfigMigrationAssetService.PROCESS.equals(type)) {
            return packageAssets.contains(type + ":" + key) || processMapper.findByProcessKey(key).isPresent();
        }
        if ("FORM".equals(type) && key.startsWith("wf-form://")) {
            String[] segments = key.substring("wf-form://".length()).split("/", 2);
            if (segments.length != 2) {
                return false;
            }
            if (packageAssets.contains(ConfigMigrationAssetService.ENTITY + ":" + segments[0])) {
                return true;
            }
            EntityDefinition entity = entityMapper.findByEntityCode(segments[0]).orElse(null);
            return entity != null && formMapper.selectByEntityIdAndFormKey(entity.getId(), segments[1]) != null;
        }
        if ("DICTIONARY".equals(type)) {
            return dictMapper.existsDictCode(key, "");
        }
        if ("USER".equals(type)) {
            return userMapper.selectByUsername(key) != null || hasMapping(type, key);
        }
        if ("ROLE".equals(type)) {
            return roleMapper.existsRoleCode(key, "") || hasMapping(type, key);
        }
        if ("DEPT".equals(type)) {
            return organizationMapper.selectByCode(key) != null || hasMapping(type, key);
        }
        if ("GROUP".equals(type)) {
            return groupMapper.selectByGroupCode(key) != null || hasMapping(type, key);
        }
        if ("FLOW_ACTION_HANDLER".equals(type)) {
            return applicationContext.containsBean(key) || classExists(key) || hasMapping(type, key);
        }
        if ("CUSTOM_COMPONENT".equals(type) || "DATA_PROVIDER".equals(type)) {
            return hasMapping(type, key);
        }
        return true;
    }

    private String mappedKey(String type, String sourceKey) {
        ConfigEnvironmentMapping mapping = environmentMappingMapper.selectOne(
                new LambdaQueryWrapper<ConfigEnvironmentMapping>()
                        .eq(ConfigEnvironmentMapping::getSourceType, type)
                        .eq(ConfigEnvironmentMapping::getSourceKey, sourceKey)
                        .eq(ConfigEnvironmentMapping::getEnabled, true)
                        .last("LIMIT 1"));
        return mapping == null ? sourceKey : mapping.getTargetKey();
    }

    private boolean hasMapping(String type, String key) {
        return environmentMappingMapper.selectCount(new LambdaQueryWrapper<ConfigEnvironmentMapping>()
                .eq(ConfigEnvironmentMapping::getSourceType, type)
                .eq(ConfigEnvironmentMapping::getSourceKey, key)
                .eq(ConfigEnvironmentMapping::getEnabled, true)) > 0;
    }

    private boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<Map<String, Object>> analyzeRisks(ConfigImportItem item) {
        List<Map<String, Object>> risks = new ArrayList<>();
        if (!ConfigMigrationAssetService.ENTITY.equals(item.getAssetType())) {
            return risks;
        }
        Map<String, Object> snapshot = readMap(item.getSnapshotJson());
        if (!snapshot.containsKey("fields")) {
            return risks;
        }
        EntityDefinition existing = entityMapper.findByEntityCode(item.getBusinessKey()).orElse(null);
        if (existing == null) {
            return risks;
        }
        Map<String, EntityField> currentFields = fieldMapper.findByEntityId(existing.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        EntityField::getFieldCode, value -> value, (left, right) -> left, LinkedHashMap::new));
        Map<String, Map<String, Object>> incomingFields = readMapList(snapshot.get("fields")).stream()
                .collect(java.util.stream.Collectors.toMap(
                        value -> String.valueOf(value.get("fieldCode")), value -> value,
                        (left, right) -> left, LinkedHashMap::new));

        for (EntityField current : currentFields.values()) {
            if (Boolean.TRUE.equals(current.getIsSystem())) {
                continue;
            }
            Map<String, Object> incoming = incomingFields.get(current.getFieldCode());
            if (incoming == null) {
                risks.add(risk("BLOCKING", "FIELD_REMOVED", current.getFieldCode(),
                        "生产环境不允许通过迁移自动删除字段"));
                continue;
            }
            String incomingType = String.valueOf(incoming.get("fieldType"));
            if (current.getFieldType() != null && !current.getFieldType().name().equals(incomingType)) {
                risks.add(risk("BLOCKING", "FIELD_TYPE_CHANGED", current.getFieldCode(),
                        "字段类型从 " + current.getFieldType() + " 变更为 " + incomingType));
            }
            Integer incomingLength = integerValue(incoming.get("fieldLength"));
            if (current.getFieldLength() != null && incomingLength != null
                    && incomingLength < current.getFieldLength()) {
                risks.add(risk("BLOCKING", "FIELD_LENGTH_NARROWED", current.getFieldCode(),
                        "字段长度从 " + current.getFieldLength() + " 收窄为 " + incomingLength));
            }
            if (!Boolean.TRUE.equals(current.getIsRequired()) && booleanValue(incoming.get("isRequired"))) {
                risks.add(risk("BLOCKING", "FIELD_REQUIRED", current.getFieldCode(),
                        "已有字段改为必填，需要先完成数据治理"));
            }
            if (!Boolean.TRUE.equals(current.getIsUnique()) && booleanValue(incoming.get("isUnique"))) {
                risks.add(risk("BLOCKING", "FIELD_UNIQUE", current.getFieldCode(),
                        "已有字段增加唯一约束，需要先检查重复数据"));
            }
        }

        for (Map<String, Object> incoming : incomingFields.values()) {
            String fieldCode = String.valueOf(incoming.get("fieldCode"));
            if (!currentFields.containsKey(fieldCode) && booleanValue(incoming.get("isRequired"))
                    && !StringUtils.hasText(String.valueOf(incoming.getOrDefault("defaultValue", "")))) {
                risks.add(risk("BLOCKING", "NEW_REQUIRED_WITHOUT_DEFAULT", fieldCode,
                        "新增必填字段没有默认值，历史数据无法安全回填"));
            }
        }
        return risks;
    }

    private Map<String, Object> risk(String level, String code, String fieldCode, String message) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("level", level);
        value.put("code", code);
        value.put("fieldCode", fieldCode);
        value.put("message", message);
        return value;
    }

    private String summarizeFailure(List<Map<String, Object>> missing,
                                    List<Map<String, Object>> risks,
                                    String comparisonStatus) {
        List<String> reasons = new ArrayList<>();
        if (!missing.isEmpty()) {
            reasons.add("缺少 " + missing.size() + " 个依赖映射");
        }
        long blockingRisks = risks.stream().filter(risk -> "BLOCKING".equals(risk.get("level"))).count();
        if (blockingRisks > 0) {
            reasons.add("存在 " + blockingRisks + " 项危险变更");
        }
        if ("CONFLICT".equals(comparisonStatus) || "LOCAL_CHANGED".equals(comparisonStatus)) {
            reasons.add("生产配置存在本地修改或双向冲突");
        }
        return String.join("；", reasons);
    }

    private ConfigImportPackage requiredImport(String id) {
        ConfigImportPackage importPackage = importPackageMapper.selectById(id);
        if (importPackage == null) {
            throw new IllegalArgumentException("导入批次不存在: " + id);
        }
        return importPackage;
    }

    private Map<String, Object> exportSummary(ConfigExportPackage value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.getId());
        result.put("packageNo", value.getPackageNo());
        result.put("migrationTag", value.getMigrationTag());
        result.put("fileName", value.getFileName());
        result.put("checksum", value.getChecksum());
        result.put("status", value.getStatus());
        result.put("assetCount", value.getAssetCount());
        result.put("createdBy", value.getCreatedBy());
        result.put("createdAt", value.getCreatedAt());
        result.put("downloadCount", value.getDownloadCount());
        result.put("lastDownloadAt", value.getLastDownloadAt());
        return result;
    }

    private Map<String, Object> importSummary(ConfigImportPackage value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.getId());
        result.put("packageNo", value.getPackageNo());
        result.put("sourceEnvironment", value.getSourceEnvironment());
        result.put("migrationTag", value.getMigrationTag());
        result.put("fileName", value.getFileName());
        result.put("checksum", value.getChecksum());
        result.put("status", value.getStatus());
        result.put("importedBy", value.getImportedBy());
        result.put("importedAt", value.getImportedAt());
        result.put("publishedBy", value.getPublishedBy());
        result.put("publishedAt", value.getPublishedAt());
        result.put("errorMessage", value.getErrorMessage());
        return result;
    }

    private Integer integerValue(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value))
                || "1".equals(String.valueOf(value));
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("配置迁移 JSON 序列化失败", e);
        }
    }

    private Object parseJson(String value, Object fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception e) {
            return fallback;
        }
    }

    private Map<String, Object> readMap(String value) {
        if (!StringUtils.hasText(value)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("迁移快照 JSON 格式错误", e);
        }
    }

    private List<Map<String, Object>> readMapList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("迁移依赖 JSON 格式错误", e);
        }
    }

    private List<Map<String, Object>> readMapList(Object value) {
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

    private record DependencyResolution(boolean resolved, List<Map<String, Object>> missing) {
    }
}
