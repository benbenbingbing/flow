package com.workflow.migration.application;

import com.workflow.integration.database.api.query.DatabaseQueryDialect;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.contracts.process.action.port.FlowActionCatalogPort;
import com.workflow.migration.api.request.ConfigEnvironmentMappingRequest;
import com.workflow.migration.api.request.ConfigExportRequest;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
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
    private final com.workflow.entity.definition.application.EntityCodeGeneratorService codeGeneratorService;
    private final ConfigMigrationReferenceService referenceService;
    private final ConfigMigrationSubFormReferences subFormReferences;
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
    private final UiExtensionDefinitionMapper extensionDefinitionMapper;
    private final ProcessDefinitionConfigMapper processMapper;
    private final SysDictMapper dictMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysGroupMapper groupMapper;
    private final FlowActionCatalogPort flowActionCatalogPort;
    private final ConfigMigrationPackageDocumentSupport documents;
    private final ConfigMigrationAssignmentTargetValidator assignmentTargetValidator;
    private final DatabaseQueryDialect queryDialect;
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
        // 文件名突出用户选中的主体，依赖补齐后的包内容仍由原有清单和签名记录。
        Set<String> requestedIds = new LinkedHashSet<>(request.getAssetIds());
        List<ConfigMigrationAsset> selectedAssets = assets.stream()
                .filter(asset -> requestedIds.contains(asset.getId()))
                .toList();
        log.info("生成配置迁移包，packageNo={}，assetCount={}，selections={}",
                packageNo, assets.size(), expanded.selections());
        ConfigExportPackage exportPackage = new ConfigExportPackage();
        exportPackage.setPackageNo(packageNo);
        exportPackage.setMigrationTag(migrationTag);
        exportPackage.setFileName(ConfigMigrationExportFileName.create(packageNo, selectedAssets));
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
            ConfigMigrationAsset exportState = new ConfigMigrationAsset();
            exportState.setId(asset.getId());
            exportState.setExportStatus(asset.getExportStatus());
            exportState.setLastExportAt(asset.getLastExportAt());
            exportState.setExportCount(asset.getExportCount());
            exportState.setUpdatedAt(asset.getUpdatedAt());
            assetMapper.updateById(exportState);
        }
        return exportSummary(exportPackage);
    }
    /** 导出副本升级为新引用协议；历史资产及其哈希保持不变。 */
    private List<ConfigMigrationAsset> portableExportAssets(List<ConfigMigrationAsset> assets) {
        return assets.stream().map(asset -> {
            ConfigMigrationAsset copy = new ConfigMigrationAsset();
            org.springframework.beans.BeanUtils.copyProperties(asset, copy);
            Map<String, Object> snapshot = subFormReferences.exportReferences(referenceService.exportReferences(documents.readMap(asset.getSnapshotJson())));
            snapshot.put("schemaVersion", 2);
            copy.setSnapshotJson(documents.writeJson(snapshot));
            copy.setSnapshotSchemaVersion(2);
            return copy;
        }).toList();
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
     * <p>包体校验通过、签名通过或已人工确认后，若同校验和批次已存在则直接返回；否则新建导入批次，
     * 为每个资产生成导入条目并初始化比较状态、依赖映射状态与发布状态。</p>
     *
     * @param file              发布包文件
     * @param sourceEnvironment 源环境名称(可选，覆盖包内信息)
     * @param confirmedChecksum 用户确认信任的文件摘要；未确认时为空，必须与本次上传文件完全匹配
     * @return 导入批次摘要，或尚未入库的签名确认提示
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
    public Map<String, Object> importPackage(MultipartFile file, String sourceEnvironment, String confirmedChecksum) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择 wfpack 文件");
        }
        try {
            byte[] packageData = file.getBytes();
            ConfigMigrationPackageCodec.DecodedPackage decoded = packageCodec.decode(packageData);
            // 先完成包体校验，再要求人工确认来源。确认绑定完整文件摘要，不能用于另一份包。
            if (StringUtils.hasText(confirmedChecksum) && !decoded.checksum().equals(confirmedChecksum)) {
                throw new IllegalArgumentException("确认的文件与本次上传不一致，请重新上传并确认");
            }
            if (!decoded.signatureVerified() && !decoded.checksum().equals(confirmedChecksum)) {
                return Map.of("confirmationRequired", true,
                        "checksum", decoded.checksum(), "packageNo", decoded.packageNo(),
                        "message", "发布包签名校验未通过，可能是两端环境密钥不同，也可能是文件被修改。请确认来源可信后继续导入。");
            }
            ConfigImportPackage existing = importPackageMapper.selectOne(
                    new LambdaQueryWrapper<ConfigImportPackage>()
                            .eq(ConfigImportPackage::getChecksum, decoded.checksum()));
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
            importPackage.setPackageData(packageData);
            importPackage.setImportedBy(UserContext.getUsername());
            importPackage.setImportedAt(LocalDateTime.now());
            importPackage.setSignatureStatus(decoded.signatureVerified() ? "VERIFIED" : "MISMATCH_CONFIRMED");
            if (!decoded.signatureVerified()) {
                importPackage.setSignatureConfirmedBy(importPackage.getImportedBy());
                importPackage.setSignatureConfirmedAt(importPackage.getImportedAt());
            }
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
            log.info("导入配置迁移包，importId={}，packageNo={}，assetCount={}，signatureStatus={}，confirmedBy={}，checksum={}",
                    importPackage.getId(), importPackage.getPackageNo(), decoded.assets().size(),
                    importPackage.getSignatureStatus(), importPackage.getSignatureConfirmedBy(), decoded.checksum());
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
            List<Map<String, Object>> risks = new ArrayList<>(analyzeRisks(item));
            try {
                Map<String, Object> snapshot = documents.readMap(item.getSnapshotJson());
                referenceService.importReferences(snapshot, this::mappedKey);
                referenceService.validateAssignments(snapshot, this::mappedKey);
                subFormReferences.validate(snapshot, packageFormReferences(items), this::mappedKey);
            } catch (RuntimeException exception) {
                risks.add(Map.of("level", "BLOCKING", "code", "REFERENCE_INVALID",
                        "message", String.valueOf(exception.getMessage())));
            }
            if ("PROCESS".equals(item.getAssetType())) {
                try {
                    ConfigMigrationAssignmentSupport.validateBpmn(
                            ConfigMigrationAssignmentSupport.text(documents.readMap(item.getSnapshotJson()).get("bpmnXml")));
                } catch (IllegalArgumentException exception) {
                    risks.add(Map.of("level", "BLOCKING", "code", "ASSIGNMENT_CONFIG_INVALID",
                            "message", exception.getMessage()));
                }
            }
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
                            .eq(ConfigEnvironmentMapping::getSourceKey, value.getSourceKey()));
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
     * <p>可打包的配置硬依赖缺失会抛异常；人员目录、解析器和系统实体依赖留到目标环境校验，
     * validateOnlyDependencies 中的依赖仅校验存在性而不打包。</p>
     *
     * @param request 本次请求，后续经校验后用于处理{@code expand}依赖集合
     * @return 处理后的{@code expand}依赖集合结果，供调用方继续处理
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
                // 兼容尚未带 targetOnly 标记的历史快照：系统实体没有 ENTITY 发布资产，
                // 仍保留包中的 required 依赖，由导入预检确认目标环境存在该实体。
                if (ConfigMigrationAssetService.ENTITY.equals(type)
                        && entityMapper.findByEntityCode(key)
                                .filter(entity -> entity.getStorageMode()
                                        == EntityDefinition.StorageMode.SYSTEM)
                                .isPresent()) {
                    continue;
                }
                if (dependencyProvidedBySnapshot(
                        selectedSnapshot, dependency, type, key)) {
                    continue;
                }
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

    /**
     * 添加或{@code merge}导出资产；结果供后续流程传递或持久化。
     *
     * @param selected 已选择，供本方法添加或{@code merge}导出资产时使用
     * @param selections {@code selections}，作为 {@code packageCodec.normalizeSelection} 的输入影响后续处理
     * @param queue 队列，供本方法添加或{@code merge}导出资产时使用
     * @param asset 资产，作为 {@code selected.putIfAbsent} 的输入影响后续处理
     * @param selection 选择，作为 {@code packageCodec.normalizeSelection} 的输入影响后续处理
     */
    private void addOrMergeExportAsset(
            Map<String, ConfigMigrationAsset> selected,
            Map<String, Object> selections,
            Deque<String> queue,
            ConfigMigrationAsset asset,
            Object selection) {
        String assetKey = asset.getAssetType() + ":" + asset.getBusinessKey();
        if (!selected.containsKey(assetKey)) asset = portableExportAssets(List.of(asset)).get(0);
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
        if (ConfigMigrationAssetService.DICTIONARY.equals(type)) {
            // 字典没有独立发布历史，导出时按当前生效内容惰性生成不可变资产。
            ConfigMigrationAsset asset = assetService.ensureDictionaryAsset(key);
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
    /**
     * 校验{@code exportable}；不满足约束时阻止后续处理。
     *
     * @param asset 资产，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateExportable(ConfigMigrationAsset asset) {
        if (!ConfigMigrationAssetService.COMPLETE.equals(asset.getSnapshotCompleteness())) {
            throw new IllegalArgumentException("历史资产 " + asset.getBusinessKey() + " 不是完整发布快照，请重新发布");
        }
    }
    /**
     * 确保依赖存在；不满足约束时阻止后续处理。
     *
     * @param type 类型标识，决定后续依赖存在采用的处理分支
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void ensureDependencyExists(String type, String key) {
        if (!isDependencyResolved(
                Map.of("type", type, "key", key, "required", true),
                type,
                key,
                Map.of())) {
            throw new IllegalArgumentException("依赖仅校验失败: " + type + ":" + key);
        }
    }
    /**
     * 解析包标签；输出作为后续校验或处理的输入。
     *
     * @param requested 请求，作为 {@code normalizeTag} 的输入影响后续处理
     * @param assets {@code assets}，供本方法解析包标签时使用
     * @return 解析后的包标签文本，供调用方比较或展示
     */
    private String resolvePackageTag(String requested, List<ConfigMigrationAsset> assets) {
        if (StringUtils.hasText(requested)) {
            return normalizeTag(requested);
        }
        Set<String> tags = assets.stream().map(ConfigMigrationAsset::getMigrationTag)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return tags.size() == 1 ? tags.iterator().next() : assetService.generateMigrationTag();
    }
    /**
     * 规范化标签；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化标签的原始输入，结果供调用方继续使用
     * @return 规范化后的标签文本，供调用方比较或展示
     */
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
                .eq(ConfigAssetBaseline::getScopeKey, scopeKey));
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

    /**
     * 判断是否{@code fine}{@code grained}快照；判断结果决定调用方的后续分支。
     *
     * @param snapshot 快照，供本方法判断是否{@code fine}{@code grained}快照时使用
     * @return {@code fine}{@code grained}快照条件成立时为 true，否则为 false
     */
    private boolean isFineGrainedSnapshot(Map<String, Object> snapshot) {
        if (!(snapshot.get(ConfigMigrationPackageCodec.SELECTION_METADATA)
                instanceof Map<?, ?> selection)) {
            return false;
        }
        return !Boolean.parseBoolean(String.valueOf(selection.get("full")));
    }

    /**
     * 判断{@code live}资产存在条件是否成立，供调用方选择后续分支。
     *
     * @param assetType 资产类型标识，决定后续{@code live}资产存在采用的处理分支
     * @param businessKey 业务键，后续用于授权校验、关联或幂等去重
     * @return {@code live}资产存在条件成立时为 true，否则为 false
     */
    private boolean liveAssetExists(String assetType, String businessKey) {
        if (ConfigMigrationAssetService.ENTITY.equals(assetType)) {
            return entityMapper.findByEntityCode(businessKey).isPresent();
        }
        if (ConfigMigrationAssetService.PROCESS.equals(assetType)) {
            return processMapper.findByProcessKey(businessKey).isPresent();
        }
        if (ConfigMigrationAssetService.DICTIONARY.equals(assetType)) {
            return dictMapper.existsDictCode(businessKey, "");
        }
        return false;
    }

    /**
     * 处理{@code derive}{@code baseline}{@code hashes}，并将结果传给后续步骤。
     *
     * @param item 条目，作为 {@code eq} 的输入影响后续处理
     * @param selection 选择，供本方法处理{@code derive}{@code baseline}{@code hashes}时使用
     * @param scopeKey 作用域键，后续用于授权校验、关联或幂等去重
     * @return 处理后的{@code derive}{@code baseline}{@code hashes}结果，供调用方继续处理
     */
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
                        .eq(ConfigAssetBaseline::getScopeKey, "FULL"));
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

    /**
     * 查询{@code baseline}目标；查询结果供调用方展示或继续处理。
     *
     * @param assetType 资产类型标识，决定后续{@code baseline}目标采用的处理分支
     * @param businessKey 业务键，后续用于授权校验、关联或幂等去重
     * @param baseline {@code baseline}，供本方法查询{@code baseline}目标时使用
     * @return 符合条件的配置迁移资产结果，供调用方继续处理
     */
    private ConfigMigrationAsset findBaselineTarget(
            String assetType,
            String businessKey,
            ConfigAssetBaseline baseline) {
        ConfigMigrationAsset target = null;
        if (baseline.getTargetVersion() != null) {
            target = assetMapper.selectPage(new Page<ConfigMigrationAsset>(1, 1, false),
                    baselineTargetQuery(assetType, businessKey)
                            .eq(ConfigMigrationAsset::getSourceVersion,
                                    baseline.getTargetVersion()))
                    .getRecords().stream().findFirst().orElse(null);
        }
        if (target != null || !StringUtils.hasText(baseline.getTargetHash())) {
            return target;
        }
        return assetMapper.selectPage(new Page<ConfigMigrationAsset>(1, 1, false),
                baselineTargetQuery(assetType, businessKey)
                        .eq(ConfigMigrationAsset::getContentHash,
                                baseline.getTargetHash()))
                .getRecords().stream().findFirst().orElse(null);
    }

    /**
     * 同版本或同内容可能有多次发布历史，按版本及主键稳定选取，保留原业务筛选范围。
     *
     * @param assetType 资产类型标识，决定后续{@code baseline}目标查询采用的处理分支
     * @param businessKey 业务键，后续用于授权校验、关联或幂等去重
     * @return 处理后的{@code baseline}目标查询结果，供调用方继续处理
     */
    private LambdaQueryWrapper<ConfigMigrationAsset> baselineTargetQuery(
            String assetType,
            String businessKey) {
        return new LambdaQueryWrapper<ConfigMigrationAsset>()
                .eq(ConfigMigrationAsset::getAssetType, assetType)
                .eq(ConfigMigrationAsset::getBusinessKey, businessKey)
                .orderByDesc(ConfigMigrationAsset::getSourceVersion, ConfigMigrationAsset::getId);
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
            boolean resolved;
            String reason = null;
            try {
                resolved = isDependencyResolved(dependency, type, targetKey, packageAssets);
            } catch (IllegalArgumentException | com.workflow.contracts.identity.position.error.OrganizationPositionDirectoryException exception) {
                resolved = false;
                reason = exception.getMessage();
            }
            if (!resolved) {
                Map<String, Object> value = new LinkedHashMap<>(dependency);
                value.put("targetKey", targetKey);
                value.put("reason", reason == null ? "目标系统不存在对应编码或配置不可用" : reason);
                missing.add(value);
            }
        }
        return new DependencyResolution(missing.isEmpty(), missing);
    }

    /**
     * 发布前重查目标依赖，防止分析通过后账号、目录或映射已被删除或修改。
     *
     * @param items 条目，供本方法校验并获取已解析依赖集合时使用
     */
    @Transactional(readOnly = true)
    public void requireResolvedDependencies(List<ConfigImportItem> items) {
        Map<String, PackageAsset> assets = items.stream().collect(java.util.stream.Collectors.toMap(
                item -> item.getAssetType() + ":" + item.getBusinessKey(),
                item -> new PackageAsset(item.getAssetType(), item.getBusinessKey(),
                        documents.readMap(item.getSnapshotJson())), (left, right) -> left));
        for (ConfigImportItem item : items) {
            Map<String, Object> snapshot = documents.readMap(item.getSnapshotJson());
            try {
                referenceService.importReferences(snapshot, this::mappedKey);
                referenceService.validateAssignments(snapshot, this::mappedKey);
                subFormReferences.validate(snapshot, packageFormReferences(items), this::mappedKey);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("迁移引用校验失败: " + exception.getMessage(), exception);
            }
            if ("PROCESS".equals(item.getAssetType())) {
                ConfigMigrationAssignmentSupport.validateBpmn(
                        ConfigMigrationAssignmentSupport.text(documents.readMap(item.getSnapshotJson()).get("bpmnXml")));
            }
            DependencyResolution resolution = resolveDependencies(
                    documents.readMapList(item.getDependenciesJson()), assets);
            if (!resolution.resolved()) {
                Map<String, Object> missing = resolution.missing().get(0);
                throw new IllegalStateException("目标依赖校验失败: " + missing.get("type") + ":"
                        + missing.get("targetKey") + "；" + missing.get("source") + "；" + missing.get("reason"));
            }
        }
    }
    /** 按裁剪后的实际表单清单判断是否会恢复宿主，防止固定版本导入覆盖包外草稿。 */
    private Set<String> packageFormReferences(List<ConfigImportItem> items) {
        Set<String> forms = new LinkedHashSet<>();
        for (ConfigImportItem item : items) {
            if ("CONSISTENT".equals(item.getComparisonStatus())) continue;
            for (Map<String, Object> form : documents.readMapList(documents.readMap(item.getSnapshotJson()).get("forms"))) {
                forms.add("wf-form://" + item.getBusinessKey() + "/" + form.get("formKey"));
            }
        }
        return forms;
    }

    /**
     * 判断单个依赖在目标环境是否已满足。人员映射必须落到真实登录名/编码，不能以映射记录代替目标对象。
     *
     * <p>支持 ENTITY/PROCESS/FORM/DICTIONARY/USER/ROLE/DEPT/GROUP/
     * FLOW_ACTION_HANDLER/CUSTOM_COMPONENT/DATA_PROVIDER 等类型。</p>
     *
     * @param dependency 依赖，作为 {@code Boolean.parseBoolean} 的输入影响后续处理
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
        if ("FLOW_ACTION_CODE".equals(type)) {
            var action = referenceService.actionByCode(key);
            return Boolean.TRUE.equals(action.getEnabled())
                    && flowActionCatalogPort.isConfiguredAndAvailable(action.getHandlerName());
        }
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
        if (ConfigMigrationAssetService.DICTIONARY.equals(type)) {
            return (!targetOnly && packageAssets.containsKey(type + ":" + key))
                    || dictMapper.existsDictCode(key, "");
        }
        if ("USER".equals(type)) {
            return userMapper.selectByUsername(key) != null;
        }
        if ("ROLE".equals(type)) {
            return roleMapper.existsRoleCode(key, "");
        }
        if ("DEPT".equals(type)) {
            return organizationMapper.selectByCode(key) != null;
        }
        if ("GROUP".equals(type)) {
            return groupMapper.selectByGroupCode(key) != null;
        }
        if (Set.of("PERSON_RESOLVER", "POSITION", "ORG_BUSINESS_LEVEL", "ENTITY_USER_FIELD").contains(type)) {
            return assignmentTargetValidator.resolved(dependency, type, key, this::mappedKey,
                    coordinate -> findTargetUserField(coordinate, packageAssets));
        }
        if ("FLOW_ACTION_HANDLER".equals(type)) {
            return flowActionCatalogPort.isConfiguredAndAvailable(key);
        }
        if ("CUSTOM_COMPONENT".equals(type) || "DATA_PROVIDER".equals(type)) {
            if ("CUSTOM_COMPONENT".equals(type)
                    && packageAssets.values().stream().anyMatch(asset ->
                    dependencyProvidedBySnapshot(
                            asset.snapshot(), dependency, type, key))) {
                return true;
            }
            Integer version = integer(dependency.get("version"));
            String componentKey = componentName(key);
            // 这里只验证存在性；不加载任意一条扩展，允许同名组件有多个版本或类型。
            if ("CUSTOM_COMPONENT".equals(type)
                    && extensionDefinitionMapper.selectCount(
                    new LambdaQueryWrapper<UiExtensionDefinition>()
                            .eq(UiExtensionDefinition::getExtensionKey,
                                    componentKey)
                            .eq(version != null,
                                    UiExtensionDefinition::getVersion,
                                    version)
                            .eq(UiExtensionDefinition::getDeleted, 0)) > 0) {
                return true;
            }
            return hasMapping(type, key);
        }
        if ("INTERFACE".equals(type) || "INTERFACE_SERVICE".equals(type)) {
            if (packageAssets.values().stream().anyMatch(asset ->
                    dependencyProvidedBySnapshot(
                            asset.snapshot(), dependency, type, key))) {
                return true;
            }
            return extensionDefinitionMapper.selectCount(
                    new LambdaQueryWrapper<UiExtensionDefinition>()
                            .eq(UiExtensionDefinition::getExtensionType,
                                    "INTERFACE")
                            .eq(UiExtensionDefinition::getExtensionKey, key)
                            .eq(UiExtensionDefinition::getDeleted, 0)) > 0;
        }
        return true;
    }

    /**
     * 同包字段优先于目标旧字段；只接受用户单选/多选关系，不把普通字符串字段当成人员。
     *
     * @param coordinate 坐标，供本方法查询目标用户字段时使用
     * @param packageAssets 包{@code assets}，供本方法查询目标用户字段时使用
     * @return 目标用户字段键值结果，供调用方继续处理
     */
    private Map<String, Object> findTargetUserField(String coordinate, Map<String, PackageAsset> packageAssets) {
        String[] parts = coordinate.split("/", 2);
        if (parts.length != 2) return Map.of();
        String entityCode = parts[0];
        for (PackageAsset asset : packageAssets.values()) {
            // 引用映射不等于重命名包内实体，只有实际将发布到该编码的字段才能满足依赖。
            if ("ENTITY".equals(asset.assetType())
                    && entityCode.equals(asset.businessKey())
                    && asset.snapshot().containsKey("fields")) {
                return documents.readMapList(asset.snapshot().get("fields")).stream()
                        .filter(field -> parts[1].equals(field.get("fieldCode")))
                        .filter(this::isUserReferenceField).findFirst().orElse(Map.of());
            }
        }
        EntityDefinition entity = entityMapper.findByEntityCode(entityCode).orElse(null);
        if (entity == null) return Map.of();
        EntityField field = fieldMapper.findByEntityIdAndFieldCode(entity.getId(), parts[1]);
        if (field == null || !Boolean.TRUE.equals(field.getIsPublished())) return Map.of();
        String referenceCode = field.getRefEntityCode();
        if (StringUtils.hasText(field.getRefEntityId())) {
            EntityDefinition referenced = entityMapper.selectById(field.getRefEntityId());
            referenceCode = referenced == null ? "" : referenced.getEntityCode();
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("fieldType", String.valueOf(field.getFieldType()));
        value.put("refEntityCode", referenceCode);
        return isUserReferenceField(value) ? value : Map.of();
    }

    /**
     * 判断是否用户引用字段；判断结果决定调用方的后续分支。
     *
     * @param field 字段，供本方法判断是否用户引用字段时使用
     * @return 用户引用字段条件成立时为 true，否则为 false
     */
    private boolean isUserReferenceField(Map<String, Object> field) {
        String type = String.valueOf(field.get("fieldType"));
        return "USER".equals(type) || (Set.of("REFERENCE", "MULTI_REFERENCE").contains(type)
                && "sys_user".equals(field.get("refEntityCode")));
    }

    /**
     * 判断接口扩展或组件是否已作为所属实体快照的内嵌定义随包迁移。
     *
     * @param snapshot 快照，作为 {@code documents.readMapList} 的输入影响后续处理
     * @param dependency 依赖，作为 {@code componentVersion} 的输入影响后续处理
     * @param type 类型标识，决定后续依赖{@code provided}快照采用的处理分支
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 依赖{@code provided}快照条件成立时为 true，否则为 false
     */
    private boolean dependencyProvidedBySnapshot(
            Map<String, Object> snapshot,
            Map<String, Object> dependency,
            String type,
            String key) {
        if ("INTERFACE".equals(type)) {
            return documents.readMapList(
                            snapshot.get("interfaceExtensions")).stream()
                    .anyMatch(value -> Objects.equals(
                            key, String.valueOf(value.get("extensionKey"))));
        }
        if ("INTERFACE_SERVICE".equals(type)) {
            // 旧迁移包仅作读取兼容。
            return documents.readMapList(snapshot.get("dataSources")).stream()
                    .anyMatch(value -> Objects.equals(
                            key, String.valueOf(value.get("sourceCode"))));
        }
        if ("CUSTOM_COMPONENT".equals(type)) {
            Integer version = componentVersion(
                    key, integer(dependency.get("version")));
            String componentName = componentName(key);
            return documents.readMapList(snapshot.get("extensions")).stream()
                    .anyMatch(value -> Objects.equals(
                                    componentName,
                                    String.valueOf(value.get("extensionKey")))
                            && (version == null || Objects.equals(
                                    version, integer(value.get("version")))));
        }
        return false;
    }

    /**
     * 生成组件名称文本，供后续匹配或展示。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 处理后的组件名称文本，供调用方比较或展示
     */
    private String componentName(String key) {
        if (!StringUtils.hasText(key)) {
            return key;
        }
        int separator = key.lastIndexOf('@');
        return separator > 0 ? key.substring(0, separator) : key;
    }

    /**
     * 处理组件版本，并将结果传给后续步骤。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的组件版本结果，供调用方继续处理
     */
    private Integer componentVersion(String key, Integer fallback) {
        if (fallback != null || !StringUtils.hasText(key)) {
            return fallback;
        }
        int separator = key.lastIndexOf('@');
        return separator > 0 ? integer(key.substring(separator + 1)) : null;
    }

    /**
     * 判断包{@code provides}实体条件是否成立，供调用方选择后续分支。
     *
     * @param snapshot 快照，作为 {@code packageCodec.selectionOf} 的输入影响后续处理
     * @return 包{@code provides}实体条件成立时为 true，否则为 false
     */
    private boolean packageProvidesEntity(Map<String, Object> snapshot) {
        Map<String, Object> selection = packageCodec.selectionOf(snapshot);
        Set<String> sections = stringSet(selection.get("sections"));
        return Boolean.TRUE.equals(selection.get("full"))
                || (sections.contains("definition") && sections.contains("fields"));
    }

    /**
     * 判断包{@code provides}流程条件是否成立，供调用方选择后续分支。
     *
     * @param snapshot 快照，作为 {@code packageCodec.selectionOf} 的输入影响后续处理
     * @return 包{@code provides}流程条件成立时为 true，否则为 false
     */
    private boolean packageProvidesProcess(Map<String, Object> snapshot) {
        Map<String, Object> selection = packageCodec.selectionOf(snapshot);
        Set<String> sections = stringSet(selection.get("sections"));
        return Boolean.TRUE.equals(selection.get("full"))
                || (sections.contains("definition") && sections.contains("bpmnXml"));
    }

    /**
     * 判断包包含表单条件是否成立，供调用方选择后续分支。
     *
     * @param snapshot 快照，作为 {@code documents.readMapList} 的输入影响后续处理
     * @param formKey 表单键，后续用于授权校验、关联或幂等去重
     * @return 包包含表单条件成立时为 true，否则为 false
     */
    private boolean packageContainsForm(Map<String, Object> snapshot, String formKey) {
        return documents.readMapList(snapshot.get("forms")).stream()
                .anyMatch(form -> Objects.equals(
                        formKey, String.valueOf(form.get("formKey"))));
    }
    /**
     * 生成{@code mapped}键文本，供后续匹配或展示。
     *
     * @param type 类型标识，决定后续{@code mapped}键采用的处理分支
     * @param sourceKey 来源键，后续用于授权校验、关联或幂等去重
     * @return 处理后的{@code mapped}键文本，供调用方比较或展示
     */
    private String mappedKey(String type, String sourceKey) {
        ConfigEnvironmentMapping mapping = environmentMappingMapper.selectOne(
                new LambdaQueryWrapper<ConfigEnvironmentMapping>()
                        .eq(ConfigEnvironmentMapping::getSourceType, type)
                        .eq(ConfigEnvironmentMapping::getSourceKey, sourceKey)
                        .eq(ConfigEnvironmentMapping::getEnabled, true));
        return mapping == null ? sourceKey : mapping.getTargetKey();
    }
    /**
     * 判断是否具有映射；判断结果决定调用方的后续分支。
     *
     * @param type 类型标识，决定后续映射采用的处理分支
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 映射条件成立时为 true，否则为 false
     */
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
        if (snapshot.get("codeRule") instanceof Map<?, ?> rawRule) {
            try {
                var rule = documents.readCodeRule(rawRule);
                rule.setEntityCode(item.getBusinessKey());
                codeGeneratorService.validateConfiguration(rule);
            } catch (IllegalArgumentException exception) {
                risks.add(risk("BLOCKING", "CODE_GENERATOR_UNAVAILABLE", "codeRule", exception.getMessage()));
            }
        }
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
    /**
     * 整理{@code analyze}系统实体界面{@code risks}数据，供调用方遍历或继续处理。
     *
     * @param item 条目，作为 {@code documents.readMap} 的输入影响后续处理
     * @return 配置迁移包集合，供调用方遍历或展示
     */
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
    /**
     * 收集已引用字段编码集合；结果供调用方的后续步骤使用。
     *
     * @param value 待收集已引用字段编码集合的原始输入，结果供调用方继续使用
     * @param result 结果，作为 {@code collection.forEach} 的输入影响后续处理
     */
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
    /**
     * 整理风险数据，供调用方遍历或继续处理。
     *
     * @param level 层级，作为 {@code value.put} 的输入影响后续处理
     * @param code 编码，后续用于处理风险时定位或关联目标
     * @param fieldCode 字段编码，后续用于处理风险时定位或关联目标
     * @param message 消息，作为 {@code value.put} 的输入影响后续处理
     * @return 风险键值结果，供调用方继续处理
     */
    private Map<String, Object> risk(String level, String code, String fieldCode, String message) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("level", level);
        value.put("code", code);
        value.put("fieldCode", fieldCode);
        value.put("message", message);
        return value;
    }
    /**
     * 生成{@code summarize}失败文本，供后续匹配或展示。
     *
     * @param missing 缺失，作为 {@code reasons.add} 的输入影响后续处理
     * @param risks {@code risks}，供本方法处理{@code summarize}失败时使用
     * @param comparisonStatus 比较状态标识，决定后续{@code summarize}失败采用的处理分支
     * @return 处理后的{@code summarize}失败文本，供调用方比较或展示
     */
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
    /**
     * 处理必填导入，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 处理后的必填导入结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private ConfigImportPackage requiredImport(String id) {
        ConfigImportPackage importPackage = importPackageMapper.selectById(id);
        if (importPackage == null) {
            throw new IllegalArgumentException("导入批次不存在: " + id);
        }
        return importPackage;
    }
    /**
     * 整理导出摘要数据，供调用方遍历或继续处理。
     *
     * @param value 待处理导出摘要的原始输入，结果供调用方继续使用
     * @return 导出摘要键值结果，供调用方继续处理
     */
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
    /**
     * 整理导入摘要数据，供调用方遍历或继续处理。
     *
     * @param value 待处理导入摘要的原始输入，结果供调用方继续使用
     * @return 导入摘要键值结果，供调用方继续处理
     */
    private Map<String, Object> importSummary(ConfigImportPackage value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.getId());
        result.put("packageNo", value.getPackageNo());
        result.put("sourceEnvironment", value.getSourceEnvironment());
        result.put("signatureStatus", value.getSignatureStatus());
        result.put("signatureConfirmedBy", value.getSignatureConfirmedBy());
        result.put("signatureConfirmedAt", value.getSignatureConfirmedAt());
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
    /**
     * 整理字符串设置数据，供调用方遍历或继续处理。
     *
     * @param value 待处理字符串设置的原始输入，结果供调用方继续使用
     * @return 配置迁移包集合，供调用方遍历或展示
     */
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
    /**
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @return 处理后的整数结果，供调用方继续处理
     */
    private Integer integer(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
    /**
     * 封装{@code expanded}导出的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param assets {@code assets}，保存在对象中供后续校验、查询或展示
     * @param selections {@code selections}，保存在对象中供后续校验、查询或展示
     */
    private record ExpandedExport(
            List<ConfigMigrationAsset> assets,
            Map<String, Object> selections) {
    }
    /**
     * 封装依赖资产的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param asset 资产，保存在对象中供后续校验、查询或展示
     * @param selection 选择，保存在对象中供后续校验、查询或展示
     */
    private record DependencyAsset(
            ConfigMigrationAsset asset,
            Map<String, Object> selection) {
    }
    /**
     * 封装包资产的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param assetType 资产类型标识，决定后续包资产采用的处理分支
     * @param businessKey 业务键，后续用于授权校验、关联或幂等去重
     * @param snapshot 快照，保存在对象中供后续校验、查询或展示
     */
    private record PackageAsset(
            String assetType,
            String businessKey,
            Map<String, Object> snapshot) {
    }
    /**
     * 封装{@code baseline}{@code hashes}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param sourceHash 来源哈希，保存在对象中供后续校验、查询或展示
     * @param targetHash 目标哈希，保存在对象中供后续校验、查询或展示
     */
    private record BaselineHashes(String sourceHash, String targetHash) {
    }
    /**
     * 封装依赖解析的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param resolved 已解析，保存在对象中供后续校验、查询或展示
     * @param missing 缺失，保存在对象中供后续校验、查询或展示
     */
    private record DependencyResolution(boolean resolved, List<Map<String, Object>> missing) {
    }
}
