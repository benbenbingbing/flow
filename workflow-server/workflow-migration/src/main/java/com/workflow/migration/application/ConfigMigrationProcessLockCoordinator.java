package com.workflow.migration.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigEnvironmentMappingMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigEnvironmentMapping;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportItem;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 配置迁移发布前的流程行锁协调器。
 *
 * <p>实体绑定采用“流程行 -> 实体行”的锁序。迁移导入会先写实体配置，若不在写实体前
 * 预锁本批次涉及的现有流程，就可能与并发的普通绑定形成 E-&gt;P / P-&gt;E 反向锁序。
 * 本协调器统一收集导入流程、实体当前绑定和实体快照目标流程，先按规范化流程主键稳定
 * 排序锁定全部流程（包括已逻辑删除的绑定门闩），再按实体主键排序锁定实体并校验绑定
 * 快照未变化。尚未在目标环境创建的流程没有可锁行，会由后续正常创建流程处理。</p>
 */
@Component
@RequiredArgsConstructor
public class ConfigMigrationProcessLockCoordinator {

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() { };

    private final EntityDefinitionMapper entityMapper;
    private final ProcessDefinitionConfigMapper processMapper;
    private final ConfigEnvironmentMappingMapper environmentMappingMapper;
    private final ObjectMapper objectMapper;

    /**
     * 在迁移写入任何实体定义前，按常规绑定路径的相同顺序锁流程再锁实体，
     * 并拒绝读取流程集合后发生的并发绑定变化。
     *
     * @param items 本次实际会应用的导入条目
     * @throws IllegalArgumentException 快照 JSON 无法解析
     */
    public void lockAffectedExistingProcesses(Collection<ConfigImportItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<String, ProcessDefinitionConfig> processesByKey = new LinkedHashMap<>();
        Map<String, String> processIds = new LinkedHashMap<>();
        Map<String, EntityBindingSnapshot> entitySnapshots = new LinkedHashMap<>();
        for (ConfigImportItem item : items) {
            if (item == null) {
                continue;
            }
            Map<String, Object> snapshot = readMap(item.getSnapshotJson());
            Map<String, Object> definition = mapValue(snapshot.get("definition"));
            if (ConfigMigrationAssetService.PROCESS.equals(item.getAssetType())) {
                addExistingProcessByKey(
                        text(definition.get("processKey"), item.getBusinessKey()),
                        processesByKey,
                        processIds);
                continue;
            }
            if (!ConfigMigrationAssetService.ENTITY.equals(item.getAssetType())) {
                continue;
            }

            String entityCode = text(definition.get("entityCode"), item.getBusinessKey());
            EntityDefinition existingEntity = entityMapper.findByEntityCode(entityCode)
                    .orElse(null);
            if (existingEntity != null) {
                addProcessId(existingEntity.getProcessDefinitionId(), processIds);
                addEntitySnapshot(existingEntity, entitySnapshots);
            }

            String sourceProcessKey = text(definition.get("processKey"), null);
            if (StringUtils.hasText(sourceProcessKey)) {
                addExistingProcessByKey(
                        mappedProcessKey(sourceProcessKey),
                        processesByKey,
                        processIds);
            }
        }

        // 保持与 EntityDefinitionService / ProcessCatalogAdapter 相同的规范化与
        // 字符串排序规则；多个事务即使输入条目顺序不同，也会以同一顺序取得流程行锁。
        processIds.keySet().stream()
                .sorted()
                .forEach(processMapper::selectAnyByIdForBindingUpdate);

        // 流程门闩全部取得后才锁实体。若绑定在“读取 -> 加锁”窗口发生变化，
        // 当前流程锁集合已不再完整，必须中止本次导入并让调用方重试。
        entitySnapshots.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> lockAndValidateEntity(entry.getValue()));
    }

    private void addExistingProcessByKey(
            String processKey,
            Map<String, ProcessDefinitionConfig> processesByKey,
            Map<String, String> processIds) {
        if (!StringUtils.hasText(processKey)) {
            return;
        }
        ProcessDefinitionConfig process = processesByKey.computeIfAbsent(
                processKey,
                key -> processMapper.findByProcessKey(key).orElse(null));
        if (process != null) {
            addProcessId(process.getId(), processIds);
        }
    }

    private void addProcessId(String processId, Map<String, String> processIds) {
        String canonicalId = canonicalProcessId(processId);
        if (canonicalId != null) {
            processIds.putIfAbsent(canonicalId, canonicalId);
        }
    }

    private void addEntitySnapshot(
            EntityDefinition entity,
            Map<String, EntityBindingSnapshot> entitySnapshots) {
        String canonicalId = canonicalPositiveId(entity.getId());
        if (canonicalId == null) {
            throw new IllegalStateException(
                    "迁移实体主键不是有效正整数: " + entity.getEntityCode());
        }
        EntityBindingSnapshot snapshot = new EntityBindingSnapshot(
                canonicalId,
                entity.getEntityCode(),
                entity.getProcessDefinitionId());
        EntityBindingSnapshot previous = entitySnapshots.putIfAbsent(
                canonicalId, snapshot);
        if (previous != null
                && !Objects.equals(previous.processDefinitionId(),
                snapshot.processDefinitionId())) {
            throw bindingChanged(entity.getEntityCode());
        }
    }

    private void lockAndValidateEntity(EntityBindingSnapshot snapshot) {
        EntityDefinition locked = entityMapper.findByIdForUpdate(snapshot.entityId())
                .orElseThrow(() -> bindingChanged(snapshot.entityCode()));
        if (!Objects.equals(
                snapshot.processDefinitionId(),
                locked.getProcessDefinitionId())) {
            throw bindingChanged(snapshot.entityCode());
        }
    }

    private BusinessConflictException bindingChanged(String entityCode) {
        return new BusinessConflictException(
                "ENTITY_WORKFLOW_BINDING_CHANGED",
                "实体流程绑定已被其他请求修改，请重试迁移: " + entityCode);
    }

    /** process_definition_config.id 是 BIGINT，只接受正整数并消除 01/1 数字别名。 */
    static String canonicalProcessId(String processId) {
        return canonicalPositiveId(processId);
    }

    private static String canonicalPositiveId(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        try {
            BigInteger value = new BigInteger(id.trim());
            return value.signum() > 0 ? value.toString() : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String mappedProcessKey(String sourceKey) {
        ConfigEnvironmentMapping mapping = environmentMappingMapper.selectOne(
                new LambdaQueryWrapper<ConfigEnvironmentMapping>()
                        .eq(ConfigEnvironmentMapping::getSourceType, "PROCESS")
                        .eq(ConfigEnvironmentMapping::getSourceKey, sourceKey)
                        .eq(ConfigEnvironmentMapping::getEnabled, true)
                        .last("LIMIT 1"));
        return mapping == null ? sourceKey : mapping.getTargetKey();
    }

    private Map<String, Object> readMap(String value) {
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalArgumentException("迁移快照 JSON 格式错误", exception);
        }
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        map.forEach((key, child) -> converted.put(String.valueOf(key), child));
        return converted;
    }

    private String text(Object value, String fallback) {
        String result = value == null ? null : String.valueOf(value);
        return StringUtils.hasText(result) ? result : fallback;
    }

    /** 加流程锁前读取的实体绑定快照，用于实体加锁后的竞态校验。 */
    private record EntityBindingSnapshot(
            String entityId,
            String entityCode,
            String processDefinitionId) {
    }
}
