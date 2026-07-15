package com.workflow.service;

import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.EntityListField;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.EntityListFieldMapper;
import com.workflow.service.listfield.ListFieldDataProvider;
import com.workflow.service.listfield.ListFieldDataProviderRegistry;
import com.workflow.service.permission.EntityActionCapabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 实体数据列表配置服务
 * 支持基础查询 + 列表配置驱动的自定义字段数据补充
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityDataListConfigService {

    private final EntityDataDynamicService dynamicService;
    private final EntityListConfigMapper configMapper;
    private final EntityListFieldMapper fieldMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final ListFieldDataProviderRegistry providerRegistry;
    private final EntityActionCapabilityService actionCapabilityService;

    /**
     * 查询实体数据列表（带列表配置扩展）
     *
     * @param entityCode    实体编码
     * @param listKey       列表标识（为空时使用默认列表）
     * @param condition     查询条件
     * @return 带扩展字段的实体数据列表
     */
    @Transactional(readOnly = true)
    public List<EntityDataDTO> findListWithConfig(String entityCode, String listKey, Map<String, Object> condition) {
        // 1. 先加载列表配置，获取 listConfigId 用于数据权限
        EntityListConfig config = findListConfig(entityCode, listKey);
        String listConfigId = config != null ? config.getId() : null;

        // 2. 基础查询（传入 listConfigId 以应用列表级权限规则）
        List<EntityDataDTO> records;
        if (condition != null && !condition.isEmpty()) {
            records = dynamicService.findByCondition(entityCode, listConfigId, condition);
        } else {
            records = dynamicService.findByEntityCode(entityCode, listConfigId);
        }

        if (records.isEmpty()) {
            return records;
        }

        if (config != null) {
            // 3. 加载字段配置，筛选出非 ENTITY_FIELD 的字段
            List<EntityListField> allFields = fieldMapper.findByListConfigId(config.getId());
            List<EntityListField> customFields = allFields.stream()
                    .filter(f -> f.getShowInList() != null && f.getShowInList())
                    .filter(f -> !"ENTITY_FIELD".equals(f.getDataSourceType()) && f.getDataSourceType() != null)
                    .collect(Collectors.toList());

            // 4. 按数据源类型分组，调用对应的数据提供者
            Map<String, List<EntityListField>> fieldsByType = customFields.stream()
                    .collect(Collectors.groupingBy(EntityListField::getDataSourceType));

            Map<String, Object> context = new HashMap<>();
            context.put("entityCode", entityCode);
            context.put("listKey", listKey);
            context.put("listConfigId", config.getId());

            for (Map.Entry<String, List<EntityListField>> entry : fieldsByType.entrySet()) {
                String dataSourceType = entry.getKey();
                List<EntityListField> fields = entry.getValue();

                ListFieldDataProvider provider = providerRegistry.getProvider(dataSourceType);
                if (provider == null) {
                    log.warn("未找到数据源类型的数据提供者: type={}, fields={}", dataSourceType,
                            fields.stream().map(EntityListField::getFieldCode).collect(Collectors.toList()));
                    continue;
                }

                try {
                    provider.enrich(records, fields, context);
                } catch (Exception e) {
                    log.error("列表字段数据补充失败: type={}, entityCode={}", dataSourceType, entityCode, e);
                }
            }
        }

        actionCapabilityService.enrichRows(entityCode, config, records);
        return records;
    }

    /**
     * 查找列表配置
     */
    public EntityListConfig findListConfig(String entityCode, String listKey) {
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode).orElse(null);
        if (definition == null) {
            return null;
        }

        EntityListConfig config;
        if (StringUtils.hasText(listKey)) {
            config = configMapper.findByEntityIdAndListKey(definition.getId(), listKey);
        } else {
            // 查找默认列表
            List<EntityListConfig> configs = configMapper.findByEntityId(definition.getId());
            config = configs.stream()
                    .filter(c -> Boolean.TRUE.equals(c.getIsDefault()))
                    .findFirst()
                    .orElse(configs.isEmpty() ? null : configs.get(0));
        }
        return config;
    }
}
