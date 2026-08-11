package com.workflow.migration.application;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.contracts.action.FlowActionCatalogPort;
import com.workflow.migration.api.request.ConfigEnvironmentMappingRequest;
import com.workflow.migration.api.request.ConfigExportRequest;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.migration.infrastructure.persistence.record.ConfigAssetBaseline;
import com.workflow.migration.infrastructure.persistence.record.ConfigEnvironmentMapping;
import com.workflow.migration.infrastructure.persistence.record.ConfigExportPackage;
import com.workflow.migration.infrastructure.persistence.record.ConfigExportPackageItem;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportItem;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportPackage;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAsset;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigAssetBaselineMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigEnvironmentMappingMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigExportPackageItemMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigExportPackageMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigImportItemMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigImportPackageMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigMigrationAssetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
/**
 * 配置迁移包服务。
 *
 * <p>负责配置导出包的生成、查询、下载，以及导入批次的上传、条目生成、
 * 分析(冲突比较/依赖解析/风险识别)、环境映射保存与比较结果查询。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
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
    private final SystemEntityFieldPolicy systemEntityFieldPolicy;
    private final EntityFormMapper formMapper;
    private final ProcessDefinitionConfigMapper processMapper;
    private final SysDictMapper dictMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysGroupMapper groupMapper;
    private final FlowActionCatalogPort flowActionCatalogPort;
    private final ConfigMigrationPackageDocumentSupport documents;
    /**
     * 生成配置导出包。
     *
     * <p>展开硬依赖并校验可导出性后，调用编解码器打包，持久化导出包及其条目，
     * 并将涉及的资产标记为 EXPORTED、更新导出统计。</p>
     *
     * @param request 导出请求
     * @return 导出包摘要
     * @throws IllegalArgumentException 未选择资产或缺少可导出依赖
     */
    @Transactional
    @SystemAudit(
            module = AuditModule.MIGRATION,
            action = AuditAction.EXPORT,
            operation = "导出配置迁移包",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "CONFIG_MIGRATION_PACKAGE",
            captureArguments = true,
            captureResult = true)
    public Map<String, Object> exportPackage(ConfigExportRequest request) {
        if (request == null || request.getAssetIds() == null || request.getAssetIds().isEmpty()) {
            throw new IllegalArgumentException("请选择至少一个迁移资产");
        }
        ExpandedExport expanded = expandDependencies(request);
        List<ConfigMigrationAsset> assets = expanded.assets();
        String migrationTag = resolvePackageTag(request.getMigrationTag(), assets);
        String packageNo = "WFP-" + migrationTag + "-" + LocalDateTime.now().format(PACKAGE_TIME)
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        ConfigMigrationPackageCodec.EncodedPackage encoded = packageCodec.encode(
                packageNo, migrationTag, assets, expanded.selections());
        log.info("生成配置迁移包，packageNo={}，assetCount={}，selections={}",
                packageNo, assets.size(), expanded.selections());
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
            item.setSelectionJson(documents.writeJson(expanded.selections().get(asset.getId())));
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
    /**
     * 查询所有导出包摘要列表(按创建时间倒序)。
     *
     * @return 导出包摘要列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listExports() {
        return exportPackageMapper.selectList(new LambdaQueryWrapper<ConfigExportPackage>()
                        .orderByDesc(ConfigExportPackage::getCreatedAt))
                .stream().map(this::exportSummary).toList();
    }
    /**
     * 下载指定导出包并累加下载次数。
     *
     * @param id 导出包ID
     * @return 下载文件数据
     * @throws IllegalArgumentException 导出包不存在
     */
    @Transactional
    @SystemAudit(
            module = AuditModule.MIGRATION,
            action = AuditAction.EXPORT,
            operation = "下载配置迁移包",
            risk = AuditRiskLevel.HIGH,
            required = true,
            targetType = "CONFIG_MIGRATION_PACKAGE",
            targetIdArg = 0)
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
    /**
     * 上传并导入 wfpack 发布包。
     *
     * <p>解码校验通过后，若同校验和批次已存在则直接返回；否则新建导入批次，
     * 为每个资产生成导入条目并初始化比较状态、依赖映射状态与发布状态。</p>
     *
     * @param file              发布包文件
     * @param sourceEnvironment 源环境名称(可选，覆盖包内信息)
     * @return 导入批次摘要
     * @throws IllegalArgumentException 文件为空或解码失败
     */
    @Transactional
    @SystemAudit(
            module = AuditModule.MIGRATION,
            action = AuditAction.IMPORT,
            operation = "导入配置迁移包",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "CONFIG_MIGRATION_PACKAGE")
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
            Map<String, PackageAsset> packageAssets = decoded.assets().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            asset -> asset.assetType() + ":" + asset.businessKey(),
                            asset -> new PackageAsset(
                                    asset.assetType(),
                                    asset.businessKey(),
                                    asset.snapshot()),
                            (left, right) -> left,
                            LinkedHashMap::new));
            for (ConfigMigrationPackageCodec.DecodedAsset asset : decoded.assets()) {
                ConfigImportItem item = new ConfigImportItem();
                item.setImportPackageId(importPackage.getId());
                item.setAssetType(asset.assetType());
                item.setBusinessKey(asset.businessKey());
                item.setAssetName(asset.assetName());
                item.setSourceVersion(asset.sourceVersion());
                item.setSourceHash(asset.sourceHash());
                item.setSnapshotJson(documents.writeJson(asset.snapshot()));
                item.setDependenciesJson(documents.writeJson(asset.dependencies()));
                item.setComparisonStatus(compare(item));
                item.setMappingStatus(resolveDependencies(asset.dependencies(), packageAssets).resolved()
                        ? "RESOLVED" : "UNRESOLVED");
                item.setPublishStatus("PENDING");
                item.setCreatedAt(LocalDateTime.now());
                item.setUpdatedAt(LocalDateTime.now());
                importItemMapper.insert(item);
            }
            log.info("导入配置迁移包，importId={}，packageNo={}，assetCount={}",
                    importPackage.getId(), importPackage.getPackageNo(), decoded.assets().size());
            return importSummary(importPackage);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("发布包导入失败: " + e.getMessage(), e);
        }
    }
    /**
     * 查询所有导入批次摘要列表(按导入时间倒序)。
     *
     * @return 导入批次摘要列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listImports() {
        return importPackageMapper.selectList(new LambdaQueryWrapper<ConfigImportPackage>()
                        .orderByDesc(ConfigImportPackage::getImportedAt))
                .stream().map(this::importSummary).toList();
    }
    /**
     * 查询指定导入批次的条目列表(按资产类型、业务编码排序)。
     *
     * @param importId 导入批次ID
     * @return 导入条目列表
     */
    @Transactional(readOnly = true)
    public List<ConfigImportItem> listImportItems(String importId) {
        return importItemMapper.selectList(new LambdaQueryWrapper<ConfigImportItem>()
                .eq(ConfigImportItem::getImportPackageId, importId)
                .orderByAsc(ConfigImportItem::getAssetType)
                .orderByAsc(ConfigImportItem::getBusinessKey));
    }
    /**
     * 对导入批次执行分析。
     *
     * <p>逐条目重新比较、解析依赖、识别风险，据此更新比较状态/映射状态/异常信息，
     * 汇总生成校验报告；任一条目存在阻断项则批次置为 BLOCKED，否则置为 ANALYZED。</p>
     *
     * @param importId 导入批次ID
     * @return 校验报告
     */
    @Transactional
    @SystemAudit(
            module = AuditModule.MIGRATION,
            action = AuditAction.CONFIGURE,
            operation = "分析配置迁移包",
            risk = AuditRiskLevel.HIGH,
            required = true,
            targetType = "CONFIG_MIGRATION_PACKAGE",
            targetIdArg = 0,
            captureResult = true)
    public Map<String, Object> analyze(String importId) {
        ConfigImportPackage importPackage = requiredImport(importId);
        List<ConfigImportItem> items = listImportItems(importId);
        Map<String, PackageAsset> packageAssets = items.stream()
                .collect(java.util.stream.Collectors.toMap(
                        item -> item.getAssetType() + ":" + item.getBusinessKey(),
                        item -> new PackageAsset(
                                item.getAssetType(),
                                item.getBusinessKey(),
                                documents.readMap(item.getSnapshotJson())),
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<Map<String, Object>> reports = new ArrayList<>();
        boolean blocked = false;
        for (ConfigImportItem item : items) {
            item.setComparisonStatus(compare(item));
            List<Map<String, Object>> dependencies = documents.readMapList(item.getDependenciesJson());
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
            importItemMapper.updateAnalysisResult(item);
        }
        Map<String, Object> validationReport = new LinkedHashMap<>();
        validationReport.put("analyzedAt", LocalDateTime.now());
        validationReport.put("blocked", blocked);
        validationReport.put("items", reports);
        importPackage.setValidationReportJson(documents.writeJson(validationReport));
        importPackage.setStatus(blocked ? "BLOCKED" : "ANALYZED");
        importPackage.setErrorMessage(blocked ? "存在冲突、缺失依赖或危险数据库变更" : null);
        importPackageMapper.updateAnalysisResult(
                importPackage.getId(),
                importPackage.getStatus(),
                importPackage.getValidationReportJson(),
                importPackage.getErrorMessage());
        log.info("分析配置迁移包完成，importId={}，itemCount={}，blocked={}",
                importId, items.size(), blocked);
        return validationReport;
    }
    /**
     * 保存环境映射并在保存后重新触发导入批次分析。
     *
     * @param importId 导入批次ID
     * @param request  环境映射保存请求
     * @throws IllegalArgumentException 映射缺少类型/来源键/目标键
     */
    @Transactional
    @SystemAudit(
            module = AuditModule.MIGRATION,
            action = AuditAction.CONFIGURE,
            operation = "保存配置迁移映射",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "CONFIG_MIGRATION_PACKAGE",
            targetIdArg = 0,
            captureArguments = true)
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
    /**
     * 查询导入批次的比较结果(批次摘要、条目列表、校验报告)。
     *
     * @param importId 导入批次ID
     * @return 比较结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> compare(String importId) {
        ConfigImportPackage importPackage = requiredImport(importId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("package", importSummary(importPackage));
        result.put("items", listImportItems(importId));
        result.put("validationReport", documents.parseJson(importPackage.getValidationReportJson(), Map.of()));
        return result;
    }
    /**
     * 展开所选资产的全部硬依赖(BFS)，返回去重后按类型+编码排序的资产列表。
     *
     * <p>硬依赖缺失会抛异常；validateOnlyDependencies 中的依赖仅校验存在性而不打包。</p>
     */
    private ExpandedExport expandDependencies(ConfigExportRequest request) {
        Map<String, ConfigMigrationAsset> selected = new LinkedHashMap<>();
        Map<String, Object> selections = new LinkedHashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        Map<String, Object> requestedSelections = request.getSelections() == null
                ? Map.of() : request.getSelections();
        for (String id : request.getAssetIds()) {
            ConfigMigrationAsset asset = assetService.getRequired(id);
            validateExportable(asset);
            addOrMergeExportAsset(
                    selected,
                    selections,
                    queue,
                    asset,
                    requestedSelections.get(asset.getId()));
        }
        while (!queue.isEmpty()) {
            String queuedKey = queue.removeFirst();
            ConfigMigrationAsset asset = selected.get(queuedKey);
            Map<String, Object> selectedSnapshot = packageCodec.selectSnapshot(
                    asset.getSnapshotJson(), selections.get(asset.getId()));
            for (Map<String, Object> dependency :
                    documents.readMapList(selectedSnapshot.get("dependencies"))) {
                if (!Boolean.parseBoolean(String.valueOf(dependency.getOrDefault("required", false)))) {
                    continue;
                }
                if (Boolean.parseBoolean(String.valueOf(dependency.getOrDefault(
                        ConfigMigrationPackageCodec.TARGET_ONLY_DEPENDENCY, false)))) {
                    continue;
                }
                String type = String.valueOf(dependency.get("type"));
                String key = String.valueOf(dependency.get("key"));
                Set<String> validateOnly = request.getValidateOnlyDependencies() == null
                        ? Set.of() : request.getValidateOnlyDependencies();
                if (validateOnly.contains(type + ":" + key)) {
                    ensureDependencyExists(type, key);
                    continue;
                }
                DependencyAsset dependencyAsset = findDependencyAsset(type, key);
                if (dependencyAsset == null) {
                    throw new IllegalArgumentException("缺少可导出的硬依赖: " + type + ":" + key);
                }
                validateExportable(dependencyAsset.asset());
                addOrMergeExportAsset(
                        selected,
                        selections,
                        queue,
                        dependencyAsset.asset(),
                        dependencyAsset.selection());
            }
        }
        List<ConfigMigrationAsset> assets = selected.values().stream()
                .sorted(Comparator.comparing(ConfigMigrationAsset::getAssetType)
                        .thenComparing(ConfigMigrationAsset::getBusinessKey))
                .toList();
        return new ExpandedExport(assets, selections);
    }

    private void addOrMergeExportAsset(
            Map<String, ConfigMigrationAsset> selected,
            Map<String, Object> selections,
            Deque<String> queue,
            ConfigMigrationAsset asset,
            Object selection) {
        String assetKey = asset.getAssetType() + ":" + asset.getBusinessKey();
        ConfigMigrationAsset existing = selected.putIfAbsent(assetKey, asset);
        Map<String, Object> normalized = packageCodec.normalizeSelection(selection);
        if (existing == null) {
            selections.put(asset.getId(), normalized);
            queue.add(assetKey);
            return;
        }
        Map<String, Object> current = packageCodec.normalizeSelection(
                selections.get(existing.getId()));
        Map<String, Object> merged = packageCodec.mergeSelections(current, normalized);
        if (!merged.equals(current)) {
            selections.put(existing.getId(), merged);
            queue.add(assetKey);
        }
    }
    /**
     * 根据依赖类型与编码查找对应的迁移资产(实体/流程/表单引用)。
     *
     * @param type 依赖类型
     * @param key  依赖编码
     * @return 匹配的迁移资产，不存在返回 null
     */
    private DependencyAsset findDependencyAsset(String type, String key) {
        if (ConfigMigrationAssetService.ENTITY.equals(type)) {
            ConfigMigrationAsset asset =
                    assetService.findLatest(ConfigMigrationAssetService.ENTITY, key);
            return asset == null ? null : new DependencyAsset(asset, Map.of("full", true));
        }
        if (ConfigMigrationAssetService.PROCESS.equals(type)) {
            ConfigMigrationAsset asset =
                    assetService.findLatest(ConfigMigrationAssetService.PROCESS, key);
            return asset == null ? null : new DependencyAsset(asset, Map.of("full", true));
        }
        if ("FORM".equals(type) && key.startsWith("wf-form://")) {
            String[] segments = key.substring("wf-form://".length()).split("/", 2);
            if (segments.length != 2) {
                return null;
            }
            ConfigMigrationAsset asset =
                    assetService.findLatest(ConfigMigrationAssetService.ENTITY, segments[0]);
            return asset == null ? null : new DependencyAsset(
                    asset,
                    Map.of(
                            "full", false,
                            "sections", List.of("forms"),
                            "formKeys", List.of(segments[1])));
        }
        return null;
    }
    private void validateExportable(ConfigMigrationAsset asset) {
        if (!ConfigMigrationAssetService.COMPLETE.equals(asset.getSnapshotCompleteness())) {
            throw new IllegalArgumentException("历史资产 " + asset.getBusinessKey() + " 不是完整发布快照，请重新发布");
        }
    }
    private void ensureDependencyExists(String type, String key) {
        if (!isDependencyResolved(
                Map.of("type", type, "key", key, "required", true),
                type,
                key,
                Map.of())) {
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
    /**
     * 比较导入条目与目标环境当前资产，返回比较状态并写入目标前置版本/哈希。
     *
     * <p>结合迁移基线区分：NEW/CONSISTENT/CONFLICT(双方改动)/LOCAL_CHANGED/SOURCE_NEWER。</p>
     *
     * @param item 导入条目(会被写入 targetBeforeVersion/targetBeforeHash)
     * @return 比较状态
     */
    private String compare(ConfigImportItem item) {
        ConfigMigrationAsset target = assetService.findLatest(item.getAssetType(), item.getBusinessKey());
        item.setTargetBeforeVersion(target == null ? null : target.getSourceVersion());
        item.setTargetBeforeHash(target == null ? null : target.getContentHash());
        Map<String, Object> incomingSnapshot = documents.readMap(item.getSnapshotJson());
        if (target == null) {
            if (isFineGrainedSnapshot(incomingSnapshot)
                    && liveAssetExists(item.getAssetType(), item.getBusinessKey())) {
                log.info("细粒度迁移缺少目标回滚快照，assetType={}，businessKey={}",
                        item.getAssetType(), item.getBusinessKey());
                return "LOCAL_CHANGED";
            }
            return "NEW";
        }
        Map<String, Object> selection = packageCodec.selectionOf(incomingSnapshot);
        String targetScopeHash = packageCodec.hashSelectedSnapshot(
                target.getSnapshotJson(), selection);
        if (Objects.equals(item.getSourceHash(), targetScopeHash)) {
            return "CONSISTENT";
        }
        String scopeKey = packageCodec.selectionScopeKey(incomingSnapshot);
        ConfigAssetBaseline baseline = baselineMapper.selectOne(new LambdaQueryWrapper<ConfigAssetBaseline>()
                .eq(ConfigAssetBaseline::getAssetType, item.getAssetType())
                .eq(ConfigAssetBaseline::getBusinessKey, item.getBusinessKey())
                .eq(ConfigAssetBaseline::getScopeKey, scopeKey)
                .last("LIMIT 1"));
        BaselineHashes hashes = baseline == null
                ? deriveBaselineHashes(item, selection, scopeKey)
                : new BaselineHashes(
                        baseline.getSourceHash(),
                        baseline.getTargetHash());
        if (hashes == null) {
            return item.getSourceVersion() != null && target.getSourceVersion() != null
                    && item.getSourceVersion() > target.getSourceVersion() ? "SOURCE_NEWER" : "CONFLICT";
        }
        boolean localChanged = !Objects.equals(
                targetScopeHash, hashes.targetHash());
        boolean sourceChanged = !Objects.equals(
                item.getSourceHash(), hashes.sourceHash());
        if (localChanged && sourceChanged) {
            return "CONFLICT";
        }
        if (localChanged) {
            return "LOCAL_CHANGED";
        }
        return sourceChanged ? "SOURCE_NEWER" : "CONSISTENT";
    }

    private boolean isFineGrainedSnapshot(Map<String, Object> snapshot) {
        if (!(snapshot.get(ConfigMigrationPackageCodec.SELECTION_METADATA)
                instanceof Map<?, ?> selection)) {
            return false;
        }
        return !Boolean.parseBoolean(String.valueOf(selection.get("full")));
    }

    private boolean liveAssetExists(String assetType, String businessKey) {
        if (ConfigMigrationAssetService.ENTITY.equals(assetType)) {
            return entityMapper.findByEntityCode(businessKey).isPresent();
        }
        if (ConfigMigrationAssetService.PROCESS.equals(assetType)) {
            return processMapper.findByProcessKey(businessKey).isPresent();
        }
        return false;
    }

    private BaselineHashes deriveBaselineHashes(
            ConfigImportItem item,
            Map<String, Object> selection,
            String scopeKey) {
        if ("FULL".equals(scopeKey)) {
            return null;
        }
        ConfigAssetBaseline fullBaseline = baselineMapper.selectOne(
                new LambdaQueryWrapper<ConfigAssetBaseline>()
                        .eq(ConfigAssetBaseline::getAssetType, item.getAssetType())
                        .eq(ConfigAssetBaseline::getBusinessKey, item.getBusinessKey())
                        .eq(ConfigAssetBaseline::getScopeKey, "FULL")
                        .last("LIMIT 1"));
        if (fullBaseline == null) {
            return null;
        }
        ConfigImportItem baselineSource = importItemMapper.selectList(
                        new LambdaQueryWrapper<ConfigImportItem>()
                                .eq(ConfigImportItem::getImportPackageId,
                                        fullBaseline.getImportPackageId())
                                .eq(ConfigImportItem::getAssetType,
                                        item.getAssetType())
                                .eq(ConfigImportItem::getBusinessKey,
                                        item.getBusinessKey()))
                .stream()
                .filter(value -> "FULL".equals(packageCodec.selectionScopeKey(
                        documents.readMap(value.getSnapshotJson()))))
                .findFirst()
                .orElse(null);
        ConfigMigrationAsset baselineTarget = findBaselineTarget(
                item.getAssetType(),
                item.getBusinessKey(),
                fullBaseline);
        if (baselineSource == null || baselineTarget == null) {
            return null;
        }
        return new BaselineHashes(
                packageCodec.hashSelectedSnapshot(
                        baselineSource.getSnapshotJson(), selection),
                packageCodec.hashSelectedSnapshot(
                        baselineTarget.getSnapshotJson(), selection));
    }

    private ConfigMigrationAsset findBaselineTarget(
            String assetType,
            String businessKey,
            ConfigAssetBaseline baseline) {
        ConfigMigrationAsset target = null;
        if (baseline.getTargetVersion() != null) {
            target = assetMapper.selectOne(
                    baselineTargetQuery(assetType, businessKey)
                            .eq(ConfigMigrationAsset::getSourceVersion,
                                    baseline.getTargetVersion())
                            .last("LIMIT 1"));
        }
        if (target != null || !StringUtils.hasText(baseline.getTargetHash())) {
            return target;
        }
        return assetMapper.selectOne(
                baselineTargetQuery(assetType, businessKey)
                        .eq(ConfigMigrationAsset::getContentHash,
                                baseline.getTargetHash())
                        .last("LIMIT 1"));
    }

    private LambdaQueryWrapper<ConfigMigrationAsset> baselineTargetQuery(
            String assetType,
            String businessKey) {
        return new LambdaQueryWrapper<ConfigMigrationAsset>()
                .eq(ConfigMigrationAsset::getAssetType, assetType)
                .eq(ConfigMigrationAsset::getBusinessKey, businessKey)
                .orderByDesc(ConfigMigrationAsset::getSourceVersion);
    }

    /**
     * 解析硬依赖，返回是否全部满足及缺失依赖列表。
     *
     * @param dependencies 依赖列表
     * @param packageAssets 包内已含资产集合(type:key)
     * @return 依赖解析结果
     */
    private DependencyResolution resolveDependencies(
            List<Map<String, Object>> dependencies,
            Map<String, PackageAsset> packageAssets) {
        List<Map<String, Object>> missing = new ArrayList<>();
        for (Map<String, Object> dependency : dependencies) {
            if (!Boolean.parseBoolean(String.valueOf(dependency.getOrDefault("required", false)))) {
                continue;
            }
            String type = String.valueOf(dependency.get("type"));
            String sourceKey = String.valueOf(dependency.get("key"));
            String targetKey = mappedKey(type, sourceKey);
            if (!isDependencyResolved(dependency, type, targetKey, packageAssets)) {
                Map<String, Object> value = new LinkedHashMap<>(dependency);
                value.put("targetKey", targetKey);
                missing.add(value);
            }
        }
        return new DependencyResolution(missing.isEmpty(), missing);
    }
    /**
     * 判断单个依赖在目标环境是否已满足：包内含或本地存在或存在环境映射。
     *
     * <p>支持 ENTITY/PROCESS/FORM/DICTIONARY/USER/ROLE/DEPT/GROUP/
     * FLOW_ACTION_HANDLER/CUSTOM_COMPONENT/DATA_PROVIDER 等类型。</p>
     *
     * @param type         依赖类型
     * @param key          依赖编码(经 mappedKey 转换后的目标键)
     * @param packageAssets 包内已含资产集合
     * @return 是否已满足
     */
    private boolean isDependencyResolved(
            Map<String, Object> dependency,
            String type,
            String key,
            Map<String, PackageAsset> packageAssets) {
        boolean targetOnly = Boolean.parseBoolean(String.valueOf(
                dependency.getOrDefault(
                        ConfigMigrationPackageCodec.TARGET_ONLY_DEPENDENCY,
                        false)));
        if (ConfigMigrationAssetService.ENTITY.equals(type)) {
            PackageAsset packageAsset = packageAssets.get(type + ":" + key);
            return (!targetOnly && packageAsset != null
                    && packageProvidesEntity(packageAsset.snapshot()))
                    || entityMapper.findByEntityCode(key).isPresent();
        }
        if (ConfigMigrationAssetService.PROCESS.equals(type)) {
            PackageAsset packageAsset = packageAssets.get(type + ":" + key);
            return (!targetOnly && packageAsset != null
                    && packageProvidesProcess(packageAsset.snapshot()))
                    || processMapper.findByProcessKey(key).isPresent();
        }
        if ("FORM".equals(type) && key.startsWith("wf-form://")) {
            String[] segments = key.substring("wf-form://".length()).split("/", 2);
            if (segments.length != 2) {
                return false;
            }
            PackageAsset packageAsset = packageAssets.get(
                    ConfigMigrationAssetService.ENTITY + ":" + segments[0]);
            if (!targetOnly && packageAsset != null
                    && packageContainsForm(packageAsset.snapshot(), segments[1])) {
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
            return flowActionCatalogPort.isConfiguredAndAvailable(key);
        }
        if ("CUSTOM_COMPONENT".equals(type) || "DATA_PROVIDER".equals(type)) {
            return hasMapping(type, key);
        }
        return true;
    }

    private boolean packageProvidesEntity(Map<String, Object> snapshot) {
        Map<String, Object> selection = packageCodec.selectionOf(snapshot);
        Set<String> sections = stringSet(selection.get("sections"));
        return Boolean.TRUE.equals(selection.get("full"))
                || (sections.contains("definition") && sections.contains("fields"));
    }

    private boolean packageProvidesProcess(Map<String, Object> snapshot) {
        Map<String, Object> selection = packageCodec.selectionOf(snapshot);
        Set<String> sections = stringSet(selection.get("sections"));
        return Boolean.TRUE.equals(selection.get("full"))
                || (sections.contains("definition") && sections.contains("bpmnXml"));
    }

    private boolean packageContainsForm(Map<String, Object> snapshot, String formKey) {
        return documents.readMapList(snapshot.get("forms")).stream()
                .anyMatch(form -> Objects.equals(
                        formKey, String.valueOf(form.get("formKey"))));
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
    /**
     * 对实体资产识别字段层面的危险变更风险(均为 BLOCKING 级)。
     *
     * <p>检查项：删除已有字段、字段类型变更、字段长度收窄、改为必填、改为唯一、
     * 新增必填字段无默认值等。</p>
     *
     * @param item 导入条目
     * @return 风险列表
     */
    private List<Map<String, Object>> analyzeRisks(ConfigImportItem item) {
        List<Map<String, Object>> risks = new ArrayList<>();
        if (ConfigMigrationAssetService.SYSTEM_ENTITY_UI
                .equals(item.getAssetType())) {
            return analyzeSystemEntityUiRisks(item);
        }
        if (!ConfigMigrationAssetService.ENTITY.equals(item.getAssetType())) {
            return risks;
        }
        Map<String, Object> snapshot = documents.readMap(item.getSnapshotJson());
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
        Map<String, Map<String, Object>> incomingFields = documents.readMapList(snapshot.get("fields")).stream()
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
            Integer incomingLength = documents.integerValue(incoming.get("fieldLength"));
            if (current.getFieldLength() != null && incomingLength != null
                    && incomingLength < current.getFieldLength()) {
                risks.add(risk("BLOCKING", "FIELD_LENGTH_NARROWED", current.getFieldCode(),
                        "字段长度从 " + current.getFieldLength() + " 收窄为 " + incomingLength));
            }
            if (!Boolean.TRUE.equals(current.getIsRequired()) && documents.booleanValue(incoming.get("isRequired"))) {
                risks.add(risk("BLOCKING", "FIELD_REQUIRED", current.getFieldCode(),
                        "已有字段改为必填，需要先完成数据治理"));
            }
            if (!Boolean.TRUE.equals(current.getIsUnique()) && documents.booleanValue(incoming.get("isUnique"))) {
                risks.add(risk("BLOCKING", "FIELD_UNIQUE", current.getFieldCode(),
                        "已有字段增加唯一约束，需要先检查重复数据"));
            }
        }
        for (Map<String, Object> incoming : incomingFields.values()) {
            String fieldCode = String.valueOf(incoming.get("fieldCode"));
            if (!currentFields.containsKey(fieldCode) && documents.booleanValue(incoming.get("isRequired"))
                    && !StringUtils.hasText(String.valueOf(incoming.getOrDefault("defaultValue", "")))) {
                risks.add(risk("BLOCKING", "NEW_REQUIRED_WITHOUT_DEFAULT", fieldCode,
                        "新增必填字段没有默认值，历史数据无法安全回填"));
            }
        }
        return risks;
    }
    private List<Map<String, Object>> analyzeSystemEntityUiRisks(
            ConfigImportItem item) {
        List<Map<String, Object>> risks = new ArrayList<>();
        Map<String, Object> snapshot =
                documents.readMap(item.getSnapshotJson());
        Map<String, Object> definition =
                snapshot.get("definition") instanceof Map<?, ?> map
                        ? map.entrySet().stream().collect(
                                java.util.stream.Collectors.toMap(
                                        entry -> String.valueOf(
                                                entry.getKey()),
                                        Map.Entry::getValue,
                                        (left, right) -> left,
                                        LinkedHashMap::new))
                        : Map.of();
        String entityCode = String.valueOf(
                definition.getOrDefault(
                        "entityCode",
                        item.getBusinessKey()));
        EntityDefinition entity = entityMapper
                .findByEntityCode(entityCode)
                .orElse(null);
        if (entity == null) {
            risks.add(risk(
                    "BLOCKING",
                    "SYSTEM_ENTITY_MISSING",
                    entityCode,
                    "目标环境缺少同编码的平台系统实体"));
            return risks;
        }
        if (entity.getStorageMode()
                != EntityDefinition.StorageMode.SYSTEM) {
            risks.add(risk(
                    "BLOCKING",
                    "SYSTEM_ENTITY_MODE_MISMATCH",
                    entityCode,
                    "目标环境同编码实体不是平台系统实体"));
            return risks;
        }
        if (!systemEntityFieldPolicy.isSupportedEntity(
                entityCode)) {
            risks.add(risk(
                    "BLOCKING",
                    "SYSTEM_ENTITY_NOT_SUPPORTED",
                    entityCode,
                    "目标系统实体不在通用UI配置白名单"));
            return risks;
        }
        Map<String, EntityField> fields = fieldMapper
                .findByEntityId(entity.getId())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        EntityField::getFieldCode,
                        value -> value,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Set<String> references = new LinkedHashSet<>();
        Object configured = snapshot.get("referencedFields");
        if (configured instanceof Collection<?> collection) {
            collection.forEach(value ->
                    references.add(String.valueOf(value)));
        }
        collectReferencedFieldCodes(snapshot.get("forms"), references);
        collectReferencedFieldCodes(snapshot.get("lists"), references);
        references.removeIf(value ->
                !StringUtils.hasText(value));
        for (String fieldCode : references) {
            EntityField field = fields.get(fieldCode);
            if (field == null) {
                risks.add(risk(
                        "BLOCKING",
                        "SYSTEM_FIELD_MISSING",
                        fieldCode,
                        "目标系统实体缺少已引用字段"));
            } else if (!systemEntityFieldPolicy
                    .isRuntimeReadable(entity, field)) {
                risks.add(risk(
                        "BLOCKING",
                        "SYSTEM_FIELD_NOT_READABLE",
                        fieldCode,
                        "字段属于安全字段，禁止进入系统实体UI配置"));
            }
        }
        return risks;
    }
    private void collectReferencedFieldCodes(
            Object value,
            Set<String> result) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> {
                if ("fieldCode".equals(String.valueOf(key))
                        && child != null) {
                    result.add(String.valueOf(child));
                }
                collectReferencedFieldCodes(child, result);
            });
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(child ->
                    collectReferencedFieldCodes(child, result));
            return;
        }
        if (value instanceof String text
                && (text.trim().startsWith("{")
                || text.trim().startsWith("["))) {
            Object parsed = documents.parseJson(text, null);
            if (parsed != null) {
                collectReferencedFieldCodes(parsed, result);
            }
        }
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
    private Set<String> stringSet(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return Set.of();
        }
        return collection.stream()
                .map(String::valueOf)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new));
    }
    private record ExpandedExport(
            List<ConfigMigrationAsset> assets,
            Map<String, Object> selections) {
    }
    private record DependencyAsset(
            ConfigMigrationAsset asset,
            Map<String, Object> selection) {
    }
    private record PackageAsset(
            String assetType,
            String businessKey,
            Map<String, Object> snapshot) {
    }
    private record BaselineHashes(String sourceHash, String targetHash) {
    }
    private record DependencyResolution(boolean resolved, List<Map<String, Object>> missing) {
    }
}
