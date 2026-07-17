package com.workflow.service.migration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.migration.MigrationAssetRecorder;
import com.workflow.dto.migration.ConfigMigrationAssetQuery;
import com.workflow.dto.migration.ConfigMigrationMarkRequest;
import com.workflow.dto.migration.ConfigMigrationPublishRequest;
import com.workflow.entity.AssigneeConfig;
import com.workflow.entity.EntityCodeRule;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.EntityPublishHistory;
import com.workflow.entity.FlowAction;
import com.workflow.entity.NodeConfig;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.entity.ProcessVersionHistory;
import com.workflow.entity.SysOrganization;
import com.workflow.entity.SysUser;
import com.workflow.entity.migration.ConfigMigrationAsset;
import com.workflow.mapper.AssigneeConfigMapper;
import com.workflow.mapper.EntityCodeRuleMapper;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldFileItemMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityFlowStatusMappingMapper;
import com.workflow.mapper.EntityFormFieldMapper;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.EntityListFieldMapper;
import com.workflow.mapper.EntityListPermissionMapper;
import com.workflow.mapper.EntityPublishHistoryMapper;
import com.workflow.mapper.EntityRelationMapper;
import com.workflow.mapper.EntityStatusMapper;
import com.workflow.mapper.FlowActionMapper;
import com.workflow.mapper.NodeConfigMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.mapper.ProcessNodeApprovalMapper;
import com.workflow.mapper.ProcessNodeFormMapper;
import com.workflow.mapper.ProcessVersionHistoryMapper;
import com.workflow.mapper.SysMenuMapper;
import com.workflow.mapper.SysOrganizationMapper;
import com.workflow.mapper.SysUserMapper;
import com.workflow.mapper.migration.ConfigMigrationAssetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ConfigMigrationAssetService implements MigrationAssetRecorder {

    public static final String ENTITY = "ENTITY";
    public static final String PROCESS = "PROCESS";
    public static final String COMPLETE = "COMPLETE";
    public static final String PARTIAL = "PARTIAL";

    private static final int SNAPSHOT_SCHEMA_VERSION = 1;
    private static final DateTimeFormatter TAG_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Set<String> TECHNICAL_KEYS = Set.of(
            "id", "entityId", "formId", "fieldId", "listConfigId", "processConfigId",
            "nodeConfigId", "versionId", "historyId", "deploymentId", "sourceHistoryId",
            "processDefinitionId", "refEntityId", "parentEntityId", "parentFieldId",
            "childEntityId", "createdAt", "updatedAt", "createTime", "updateTime",
            "createdBy", "updatedBy", "deleted", "isPublished", "currentSeq", "seqDate");
    private static final Pattern SENSITIVE_XML = Pattern.compile(
            "(?i)(password|secret|token|apiKey)(\\s*=\\s*\")([^\"]*)(\")");

    private final ConfigMigrationAssetMapper assetMapper;
    private final EntityDefinitionMapper entityMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityFieldFileItemMapper fileItemMapper;
    private final EntityRelationMapper relationMapper;
    private final EntityStatusMapper statusMapper;
    private final EntityCodeRuleMapper codeRuleMapper;
    private final EntityFormMapper formMapper;
    private final EntityFormFieldMapper formFieldMapper;
    private final EntityListConfigMapper listConfigMapper;
    private final EntityListFieldMapper listFieldMapper;
    private final EntityListPermissionMapper listPermissionMapper;
    private final SysMenuMapper menuMapper;
    private final ProcessDefinitionConfigMapper processMapper;
    private final NodeConfigMapper nodeConfigMapper;
    private final AssigneeConfigMapper assigneeConfigMapper;
    private final ProcessNodeFormMapper nodeFormMapper;
    private final ProcessNodeApprovalMapper nodeApprovalMapper;
    private final FlowActionMapper flowActionMapper;
    private final EntityFlowStatusMappingMapper statusMappingMapper;
    private final EntityPublishHistoryMapper entityHistoryMapper;
    private final ProcessVersionHistoryMapper processHistoryMapper;
    private final SysUserMapper userMapper;
    private final SysOrganizationMapper organizationMapper;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ConfigMigrationAsset> query(ConfigMigrationAssetQuery query) {
        LambdaQueryWrapper<ConfigMigrationAsset> wrapper = new LambdaQueryWrapper<ConfigMigrationAsset>()
                .eq(StringUtils.hasText(query.getAssetType()), ConfigMigrationAsset::getAssetType, query.getAssetType())
                .like(StringUtils.hasText(query.getBusinessKey()), ConfigMigrationAsset::getBusinessKey, query.getBusinessKey())
                .eq(StringUtils.hasText(query.getMigrationTag()), ConfigMigrationAsset::getMigrationTag, query.getMigrationTag())
                .eq(query.getMarkForExport() != null, ConfigMigrationAsset::getMarkForExport, query.getMarkForExport())
                .eq(StringUtils.hasText(query.getExportStatus()), ConfigMigrationAsset::getExportStatus, query.getExportStatus())
                .eq(StringUtils.hasText(query.getSnapshotCompleteness()),
                        ConfigMigrationAsset::getSnapshotCompleteness, query.getSnapshotCompleteness())
                .orderByDesc(ConfigMigrationAsset::getPublishedAt)
                .orderByDesc(ConfigMigrationAsset::getCreatedAt);
        return assetMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public ConfigMigrationAsset getRequired(String id) {
        ConfigMigrationAsset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new IllegalArgumentException("迁移资产不存在: " + id);
        }
        return asset;
    }

    @Transactional(readOnly = true)
    public ConfigMigrationAsset findLatest(String assetType, String businessKey) {
        return assetMapper.selectOne(new LambdaQueryWrapper<ConfigMigrationAsset>()
                .eq(ConfigMigrationAsset::getAssetType, assetType)
                .eq(ConfigMigrationAsset::getBusinessKey, businessKey)
                .orderByDesc(ConfigMigrationAsset::getSourceVersion)
                .last("LIMIT 1"));
    }

    @Transactional
    public ConfigMigrationAsset updateMark(String id, ConfigMigrationMarkRequest request) {
        ConfigMigrationAsset asset = getRequired(id);
        if (request.getMarkForExport() != null) {
            asset.setMarkForExport(request.getMarkForExport());
        }
        if (StringUtils.hasText(request.getMigrationTag())) {
            asset.setMigrationTag(normalizeTag(request.getMigrationTag()));
        }
        asset.setUpdatedAt(LocalDateTime.now());
        assetMapper.updateById(asset);
        return asset;
    }

    @Transactional
    public ConfigMigrationAsset recordEntity(EntityDefinition entity,
                                             EntityPublishHistory history,
                                             ConfigMigrationPublishRequest request) {
        Map<String, Object> snapshot = buildEntitySnapshot(entity);
        return saveAsset(
                ENTITY,
                entity.getEntityCode(),
                entity.getEntityName(),
                history.getId(),
                history.getVersion(),
                effectiveDescription(request, history.getVersionDescription()),
                effectiveTag(request),
                effectiveMark(request),
                COMPLETE,
                snapshot,
                castList(snapshot.get("dependencies")),
                history.getPublishedAt(),
                firstNonBlank(history.getPublishedByName(), history.getPublishedBy()));
    }

    @Override
    @Transactional
    public void recordEntity(String entityId,
                             String publishHistoryId,
                             ConfigMigrationPublishRequest request) {
        EntityDefinition entity = entityMapper.selectById(entityId);
        EntityPublishHistory history = entityHistoryMapper.selectById(publishHistoryId);
        if (entity == null || history == null) {
            throw new IllegalStateException("实体发布快照上下文不存在: " + entityId);
        }
        recordEntity(entity, history, request);
    }

    @Transactional
    public ConfigMigrationAsset recordProcess(ProcessDefinitionConfig config,
                                              ProcessVersionHistory history,
                                              ConfigMigrationPublishRequest request) {
        Map<String, Object> snapshot = buildProcessSnapshot(config, history);
        return saveAsset(
                PROCESS,
                config.getProcessKey(),
                config.getProcessName(),
                history.getId(),
                history.getVersion(),
                effectiveDescription(request, history.getVersionDescription()),
                effectiveTag(request),
                effectiveMark(request),
                COMPLETE,
                snapshot,
                castList(snapshot.get("dependencies")),
                history.getPublishedAt(),
                history.getPublishedBy());
    }

    @Override
    @Transactional
    public void recordProcess(String processId,
                              String versionHistoryId,
                              ConfigMigrationPublishRequest request) {
        ProcessDefinitionConfig process = processMapper.selectById(processId);
        ProcessVersionHistory history = processHistoryMapper.selectById(versionHistoryId);
        if (process == null || history == null) {
            throw new IllegalStateException("流程发布快照上下文不存在: " + processId);
        }
        recordProcess(process, history, request);
    }

    @Transactional
    public int backfillLegacyAssets() {
        int created = 0;
        List<EntityPublishHistory> entityHistories = entityHistoryMapper.selectList(
                new LambdaQueryWrapper<EntityPublishHistory>().orderByAsc(EntityPublishHistory::getPublishedAt));
        for (EntityPublishHistory history : entityHistories) {
            if (exists(ENTITY, history.getId())) {
                continue;
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("schemaVersion", SNAPSHOT_SCHEMA_VERSION);
            snapshot.put("assetType", ENTITY);
            snapshot.put("businessKey", history.getEntityCode());
            snapshot.put("version", history.getVersion());
            snapshot.put("fields", parseJson(history.getFieldsSnapshot(), List.of()));
            snapshot.put("legacyNote", "历史实体版本缺少表单、列表、权限等完整发布快照，请重新发布后导出");
            saveAsset(ENTITY, history.getEntityCode(), history.getEntityName(), history.getId(),
                    history.getVersion(), history.getVersionDescription(), legacyTag(history.getPublishedAt()),
                    false, PARTIAL, snapshot, List.of(), history.getPublishedAt(),
                    firstNonBlank(history.getPublishedByName(), history.getPublishedBy()));
            created++;
        }

        List<ProcessVersionHistory> processHistories = processHistoryMapper.selectList(
                new LambdaQueryWrapper<ProcessVersionHistory>().orderByAsc(ProcessVersionHistory::getPublishedAt));
        for (ProcessVersionHistory history : processHistories) {
            if (exists(PROCESS, history.getId())) {
                continue;
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("schemaVersion", SNAPSHOT_SCHEMA_VERSION);
            snapshot.put("assetType", PROCESS);
            snapshot.put("businessKey", history.getProcessKey());
            snapshot.put("version", history.getVersion());
            snapshot.put("bpmnXml", redactSensitiveXml(history.getBpmnXml()));
            snapshot.put("nodeForms", parseJson(history.getNodeFormsSnapshot(), List.of()));
            snapshot.put("legacyNote", "历史流程版本缺少完整节点配置和迁移依赖，请重新发布后导出");
            saveAsset(PROCESS, history.getProcessKey(), history.getProcessName(), history.getId(),
                    history.getVersion(), history.getVersionDescription(), legacyTag(history.getPublishedAt()),
                    false, PARTIAL, snapshot, List.of(), history.getPublishedAt(), history.getPublishedBy());
            created++;
        }
        return created;
    }

    private Map<String, Object> buildEntitySnapshot(EntityDefinition entity) {
        Map<String, Object> snapshot = baseSnapshot(ENTITY, entity.getEntityCode(), entity.getEntityName());
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("entityCode", entity.getEntityCode());
        definition.put("entityName", entity.getEntityName());
        definition.put("description", entity.getDescription());
        definition.put("enableProcess", entity.getEnableProcess());
        ProcessDefinitionConfig process = StringUtils.hasText(entity.getProcessDefinitionId())
                ? processMapper.selectById(entity.getProcessDefinitionId()) : null;
        definition.put("processKey", process == null ? null : process.getProcessKey());
        snapshot.put("definition", definition);

        List<Map<String, Object>> fields = new ArrayList<>();
        Map<String, String> fieldCodesById = new LinkedHashMap<>();
        for (EntityField field : fieldMapper.findByEntityId(entity.getId())) {
            fieldCodesById.put(field.getId(), field.getFieldCode());
            Map<String, Object> fieldSnapshot = portableMap(field);
            if (StringUtils.hasText(field.getRefEntityId())) {
                EntityDefinition referenced = entityMapper.selectById(field.getRefEntityId());
                fieldSnapshot.put("refEntityCode", referenced == null ? null : referenced.getEntityCode());
            }
            fieldSnapshot.put("fileItems", portableList(fileItemMapper.findByFieldId(field.getId())));
            fields.add(fieldSnapshot);
        }
        fields.sort(Comparator.comparing(value -> value.get("sortOrder") == null
                ? Integer.MAX_VALUE : Integer.parseInt(String.valueOf(value.get("sortOrder")))));
        snapshot.put("fields", fields);
        snapshot.put("relations", portableList(relationMapper.selectByParentEntityId(entity.getId())));
        snapshot.put("statuses", portableList(statusMapper.findByEntityCode(entity.getEntityCode())));

        EntityCodeRule codeRule = codeRuleMapper.findByEntityCode(entity.getEntityCode()).orElse(null);
        snapshot.put("codeRule", codeRule == null ? null : portableMap(codeRule));

        List<Map<String, Object>> forms = new ArrayList<>();
        for (EntityForm form : formMapper.selectByEntityId(entity.getId())) {
            Map<String, Object> formSnapshot = portableMap(form);
            List<Map<String, Object>> formFields = new ArrayList<>();
            formFieldMapper.selectByFormId(form.getId()).forEach(formField -> {
                Map<String, Object> formFieldSnapshot = portableMap(formField);
                formFieldSnapshot.put("fieldCode",
                        firstNonBlank(formField.getFieldCode(), fieldCodesById.get(formField.getFieldId())));
                formFields.add(formFieldSnapshot);
            });
            formSnapshot.put("fields", formFields);
            forms.add(formSnapshot);
        }
        snapshot.put("forms", forms);

        List<EntityListConfig> listConfigs = listConfigMapper.findByEntityId(entity.getId());
        Map<String, String> listKeysById = new LinkedHashMap<>();
        List<Map<String, Object>> lists = new ArrayList<>();
        for (EntityListConfig listConfig : listConfigs) {
            listKeysById.put(listConfig.getId(), listConfig.getListKey());
            Map<String, Object> listSnapshot = portableMap(listConfig);
            listSnapshot.put("fields", portableList(listFieldMapper.findByListConfigId(listConfig.getId())));
            lists.add(listSnapshot);
        }
        snapshot.put("lists", lists);

        List<Map<String, Object>> permissions = new ArrayList<>();
        listPermissionMapper.selectList(new LambdaQueryWrapper<com.workflow.entity.EntityListPermission>()
                        .eq(com.workflow.entity.EntityListPermission::getEntityCode, entity.getEntityCode()))
                .forEach(permission -> {
                    Map<String, Object> value = portableMap(permission);
                    value.put("listKey", listKeysById.get(permission.getListConfigId()));
                    permissions.add(value);
                });
        snapshot.put("dataPermissions", permissions);

        List<Map<String, Object>> menus = new ArrayList<>();
        menuMapper.selectList(new LambdaQueryWrapper<com.workflow.entity.SysMenu>()
                        .eq(com.workflow.entity.SysMenu::getEntityCode, entity.getEntityCode()))
                .forEach(menu -> {
                    Map<String, Object> value = portableMap(menu);
                    if (StringUtils.hasText(menu.getParentId())) {
                        com.workflow.entity.SysMenu parent = menuMapper.selectById(menu.getParentId());
                        value.put("parentPath", parent == null ? null : parent.getPath());
                    }
                    menus.add(value);
                });
        snapshot.put("menus", menus);

        List<Map<String, Object>> dependencies = new ArrayList<>();
        if (process != null) {
            addDependency(dependencies, PROCESS, process.getProcessKey(), true, "实体绑定流程");
        }
        for (EntityField field : fieldMapper.findByEntityId(entity.getId())) {
            if (field.getRefEntityType() == EntityField.RefEntityType.CUSTOM && StringUtils.hasText(field.getRefEntityId())) {
                EntityDefinition referenced = entityMapper.selectById(field.getRefEntityId());
                if (referenced != null) {
                    addDependency(dependencies, ENTITY, referenced.getEntityCode(), true, "实体引用字段");
                }
            }
        }
        collectExtensionDependencies(snapshot, dependencies);
        snapshot.put("dependencies", deduplicateDependencies(dependencies));
        return snapshot;
    }

    private Map<String, Object> buildProcessSnapshot(ProcessDefinitionConfig config, ProcessVersionHistory history) {
        Map<String, Object> snapshot = baseSnapshot(PROCESS, config.getProcessKey(), config.getProcessName());
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("processKey", config.getProcessKey());
        definition.put("processName", config.getProcessName());
        definition.put("description", config.getDescription());
        definition.put("category", config.getCategory());
        snapshot.put("definition", definition);

        List<ProcessNodeForm> nodeForms = nodeFormMapper.selectByProcessConfigId(config.getId());
        Map<String, String> portableForms = new LinkedHashMap<>();
        List<Map<String, Object>> nodeFormSnapshots = new ArrayList<>();
        for (ProcessNodeForm nodeForm : nodeForms) {
            Map<String, Object> nodeFormSnapshot = portableMap(nodeForm);
            String portableForm = portableFormReference(nodeForm.getFormId());
            nodeFormSnapshot.put("formRef", portableForm);
            portableForms.put(nodeForm.getFormId(), portableForm);
            nodeFormSnapshots.add(nodeFormSnapshot);
        }
        Map<String, String> portableAssignees = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (NodeConfig node : nodeConfigMapper.findByProcessConfigId(config.getId())) {
            Map<String, Object> nodeSnapshot = portableMap(node);
            List<Map<String, Object>> assignees = new ArrayList<>();
            for (AssigneeConfig assignee : assigneeConfigMapper.findByNodeConfigId(node.getId())) {
                Map<String, Object> assigneeSnapshot = portableMap(assignee);
                String portableValue = portableAssigneeValue(assignee);
                assigneeSnapshot.put("assigneeValue", portableValue);
                if (StringUtils.hasText(assignee.getAssigneeValue())
                        && StringUtils.hasText(portableValue)
                        && !assignee.getAssigneeValue().equals(portableValue)) {
                    portableAssignees.put(assignee.getAssigneeValue(), portableValue);
                }
                assignees.add(assigneeSnapshot);
            }
            nodeSnapshot.put("assignees", assignees);
            nodes.add(nodeSnapshot);
        }
        String portableBpmn = replacePortableForms(redactSensitiveXml(history.getBpmnXml()), portableForms);
        portableBpmn = replacePortableForms(portableBpmn, portableAssignees);
        snapshot.put("bpmnXml", portableBpmn);
        snapshot.put("nodes", nodes);
        snapshot.put("nodeForms", nodeFormSnapshots);
        snapshot.put("nodeApprovals", portableList(nodeApprovalMapper.selectByProcessConfigId(config.getId())));
        List<FlowAction> actions = flowActionMapper.findPublishedActionsByVersionId(history.getId());
        snapshot.put("flowActions", portableList(actions));
        snapshot.put("statusMappings", portableList(statusMappingMapper.findByProcessConfigId(config.getId())));

        List<Map<String, Object>> dependencies = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            for (Map<String, Object> assignee : castMapList(node.get("assignees"))) {
                String type = String.valueOf(assignee.get("assigneeType"));
                String value = String.valueOf(assignee.get("assigneeValue"));
                if ("USER".equals(type)) {
                    addDependency(dependencies, "USER", stripPortablePrefix(value), true, "节点办理人");
                } else if ("DEPT".equals(type)) {
                    addDependency(dependencies, "DEPT", stripPortablePrefix(value), true, "节点办理部门");
                } else if ("ROLE".equals(type)) {
                    addDependency(dependencies, "ROLE", value, true, "节点办理角色");
                }
            }
        }
        for (String formRef : new LinkedHashSet<>(portableForms.values())) {
            if (StringUtils.hasText(formRef)) {
                addDependency(dependencies, "FORM", formRef, true, "节点表单");
            }
        }
        statusMappingMapper.findByProcessConfigId(config.getId()).forEach(mapping -> {
            if (StringUtils.hasText(mapping.getEntityCode())) {
                addDependency(dependencies, ENTITY, mapping.getEntityCode(), true, "实体状态映射");
            }
        });
        actions.forEach(action -> {
            if (StringUtils.hasText(action.getInterfaceName())) {
                addDependency(dependencies, "FLOW_ACTION_HANDLER", action.getInterfaceName(), true, "流程动作");
            }
        });
        collectCalledProcesses(history.getBpmnXml(), dependencies);
        snapshot.put("dependencies", deduplicateDependencies(dependencies));
        return snapshot;
    }

    private Map<String, Object> baseSnapshot(String assetType, String businessKey, String assetName) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", SNAPSHOT_SCHEMA_VERSION);
        snapshot.put("assetType", assetType);
        snapshot.put("businessKey", businessKey);
        snapshot.put("assetName", assetName);
        return snapshot;
    }

    private ConfigMigrationAsset saveAsset(String assetType,
                                           String businessKey,
                                           String assetName,
                                           String sourceHistoryId,
                                           Integer sourceVersion,
                                           String versionDescription,
                                           String migrationTag,
                                           boolean markForExport,
                                           String completeness,
                                           Map<String, Object> snapshot,
                                           List<Map<String, Object>> dependencies,
                                           LocalDateTime publishedAt,
                                           String publishedBy) {
        ConfigMigrationAsset existing = findByHistory(assetType, sourceHistoryId);
        if (existing != null) {
            return existing;
        }
        String snapshotJson = writeJson(snapshot);
        ConfigMigrationAsset asset = new ConfigMigrationAsset();
        asset.setAssetType(assetType);
        asset.setBusinessKey(businessKey);
        asset.setAssetName(assetName);
        asset.setSourceHistoryId(sourceHistoryId);
        asset.setSourceVersion(sourceVersion);
        asset.setVersionDescription(versionDescription);
        asset.setMigrationTag(normalizeTag(migrationTag));
        asset.setMarkForExport(markForExport);
        asset.setSnapshotCompleteness(completeness);
        asset.setSnapshotSchemaVersion(SNAPSHOT_SCHEMA_VERSION);
        asset.setSnapshotJson(snapshotJson);
        asset.setContentHash(sha256(snapshotJson.getBytes(StandardCharsets.UTF_8)));
        asset.setDependenciesJson(writeJson(dependencies));
        asset.setDependencyCount(dependencies.size());
        asset.setMissingDependencyCount(0);
        asset.setExportStatus("PENDING");
        asset.setPublishedAt(publishedAt);
        asset.setPublishedBy(publishedBy);
        asset.setExportCount(0);
        asset.setCreatedAt(LocalDateTime.now());
        asset.setUpdatedAt(LocalDateTime.now());
        asset.setDeleted(0);
        assetMapper.insert(asset);
        return asset;
    }

    private ConfigMigrationAsset findByHistory(String assetType, String sourceHistoryId) {
        return assetMapper.selectOne(new LambdaQueryWrapper<ConfigMigrationAsset>()
                .eq(ConfigMigrationAsset::getAssetType, assetType)
                .eq(ConfigMigrationAsset::getSourceHistoryId, sourceHistoryId)
                .last("LIMIT 1"));
    }

    private boolean exists(String assetType, String sourceHistoryId) {
        return findByHistory(assetType, sourceHistoryId) != null;
    }

    private Map<String, Object> portableMap(Object source) {
        if (source == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> converted = objectMapper.convertValue(source, LinkedHashMap.class);
        return sanitizeMap(converted);
    }

    private List<Map<String, Object>> portableList(Collection<?> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(this::portableMap).toList();
    }

    private Map<String, Object> sanitizeMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!TECHNICAL_KEYS.contains(key)) {
                result.put(key, sanitizeValue(value));
            }
        });
        return result;
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((key, item) -> converted.put(String.valueOf(key), item));
            return sanitizeMap(converted);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::sanitizeValue).toList();
        }
        return value;
    }

    private String portableFormReference(String formId) {
        if (!StringUtils.hasText(formId)) {
            return null;
        }
        EntityForm form = formMapper.selectById(formId);
        if (form == null) {
            return "wf-form://missing/" + formId;
        }
        EntityDefinition entity = entityMapper.selectById(form.getEntityId());
        String entityCode = entity == null ? "missing" : entity.getEntityCode();
        return "wf-form://" + entityCode + "/" + form.getFormKey();
    }

    private String portableAssigneeValue(AssigneeConfig assignee) {
        if (!StringUtils.hasText(assignee.getAssigneeValue()) || assignee.getAssigneeType() == null) {
            return assignee.getAssigneeValue();
        }
        if (assignee.getAssigneeType() == AssigneeConfig.AssigneeType.USER) {
            SysUser user = userMapper.selectById(assignee.getAssigneeValue());
            return user == null ? "wf-user://missing/" + assignee.getAssigneeValue()
                    : "wf-user://" + user.getUsername();
        }
        if (assignee.getAssigneeType() == AssigneeConfig.AssigneeType.DEPT) {
            SysOrganization organization = organizationMapper.selectById(assignee.getAssigneeValue());
            return organization == null ? "wf-dept://missing/" + assignee.getAssigneeValue()
                    : "wf-dept://" + organization.getOrgCode();
        }
        return assignee.getAssigneeValue();
    }

    private String stripPortablePrefix(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("wf-user://")) {
            return value.substring("wf-user://".length());
        }
        if (value.startsWith("wf-dept://")) {
            return value.substring("wf-dept://".length());
        }
        return value;
    }

    private String replacePortableForms(String bpmnXml, Map<String, String> formReferences) {
        if (!StringUtils.hasText(bpmnXml)) {
            return bpmnXml;
        }
        String result = bpmnXml;
        for (Map.Entry<String, String> entry : formReferences.entrySet()) {
            if (StringUtils.hasText(entry.getKey()) && StringUtils.hasText(entry.getValue())) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private String redactSensitiveXml(String xml) {
        if (!StringUtils.hasText(xml)) {
            return xml;
        }
        Matcher matcher = SENSITIVE_XML.matcher(xml);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String environmentKey = matcher.group(1).replaceAll("([a-z])([A-Z])", "$1_$2")
                    .toUpperCase(Locale.ROOT);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + matcher.group(2) + "${ENV:" + environmentKey + "}" + matcher.group(4)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private void collectCalledProcesses(String bpmnXml, List<Map<String, Object>> dependencies) {
        if (!StringUtils.hasText(bpmnXml)) {
            return;
        }
        Matcher matcher = Pattern.compile("calledElement\\s*=\\s*\"([^\"]+)\"").matcher(bpmnXml);
        while (matcher.find()) {
            String calledElement = matcher.group(1);
            if (!calledElement.startsWith("${")) {
                addDependency(dependencies, PROCESS, calledElement, true, "调用子流程");
            }
        }
    }

    private void collectExtensionDependencies(Object value, List<Map<String, Object>> dependencies) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> {
                String name = String.valueOf(key);
                if (child instanceof String text && StringUtils.hasText(text)) {
                    if ("customComponent".equals(name) || "renderComponent".equals(name)) {
                        addDependency(dependencies, "CUSTOM_COMPONENT", text, true, name);
                    } else if ("dataProvider".equals(name) || "providerName".equals(name)) {
                        addDependency(dependencies, "DATA_PROVIDER", text, true, name);
                    } else if ("dictCode".equals(name)) {
                        addDependency(dependencies, "DICTIONARY", text, false, name);
                    }
                }
                collectExtensionDependencies(child, dependencies);
            });
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(child -> collectExtensionDependencies(child, dependencies));
        } else if (value instanceof String text && text.trim().startsWith("{")) {
            Object parsed = parseJson(text, null);
            if (parsed != null) {
                collectExtensionDependencies(parsed, dependencies);
            }
        }
    }

    private void addDependency(List<Map<String, Object>> dependencies,
                               String type,
                               String key,
                               boolean required,
                               String source) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        Map<String, Object> dependency = new LinkedHashMap<>();
        dependency.put("type", type);
        dependency.put("key", key);
        dependency.put("required", required);
        dependency.put("source", source);
        dependencies.add(dependency);
    }

    private List<Map<String, Object>> deduplicateDependencies(List<Map<String, Object>> dependencies) {
        Map<String, Map<String, Object>> values = new LinkedHashMap<>();
        for (Map<String, Object> dependency : dependencies) {
            values.put(dependency.get("type") + ":" + dependency.get("key"), dependency);
        }
        return new ArrayList<>(values.values());
    }

    private String effectiveDescription(ConfigMigrationPublishRequest request, String fallback) {
        return request != null && StringUtils.hasText(request.getVersionDescription())
                ? request.getVersionDescription().trim() : fallback;
    }

    private boolean effectiveMark(ConfigMigrationPublishRequest request) {
        return request == null || request.getMarkForExport() == null || request.getMarkForExport();
    }

    private String effectiveTag(ConfigMigrationPublishRequest request) {
        return request == null ? null : request.getMigrationTag();
    }

    public String generateMigrationTag() {
        return "REL-" + LocalDateTime.now().format(TAG_FORMAT);
    }

    private String normalizeTag(String value) {
        String tag = StringUtils.hasText(value) ? value.trim() : generateMigrationTag();
        tag = tag.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9._-]", "-");
        return tag.length() > 100 ? tag.substring(0, 100) : tag;
    }

    private String legacyTag(LocalDateTime publishedAt) {
        return "LEGACY-" + (publishedAt == null ? LocalDateTime.now() : publishedAt).format(TAG_FORMAT);
    }

    private Object parseJson(String json, Object fallback) {
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException ignored) {
            return fallback;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("配置迁移快照序列化失败", e);
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception e) {
            throw new IllegalStateException("配置迁移哈希计算失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

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

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
