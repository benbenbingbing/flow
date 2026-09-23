package com.workflow.entity.list.application;

import com.workflow.core.logging.LogValue;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;

import com.workflow.core.result.PageResult;
import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.ui.api.request.UiExtensionExecuteRequest;
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListFieldMapper;
import com.workflow.entity.list.extension.ListFieldDataProvider;
import com.workflow.entity.list.extension.ListFieldConditionEvaluator;
import com.workflow.entity.list.extension.ListFieldDataProviderRegistry;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
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
    private final ListFieldConditionEvaluator conditionEvaluator;
    private final EntityActionCapabilityService actionCapabilityService;
    private final EntityListPublishedRuntimeService publishedRuntimeService;
    private final UiInterfaceExtensionService uiDataSourceService;

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
        EntityListConfig config = findListConfig(entityCode, listKey);
        requireRequestedList(listKey, config);
        return findListWithResolvedConfig(
                entityCode,
                listKey,
                config,
                condition);
    }

    /**
     * 查询列表已解析配置；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param config 配置内容，决定后续列表已解析配置的处理规则
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @return 实体数据集合，供调用方遍历或展示
     */
    public List<EntityDataDTO> findListWithResolvedConfig(
            String entityCode,
            String listKey,
            EntityListConfig config,
            Map<String, Object> condition) {
        // 1. 使用同一已解析发布版本，避免查询过程中重新落到当前 ACTIVE。
        String resolvedListKey = config != null ? config.getListKey() : null;
        List<EntityListField> allFields = config == null
                ? List.of()
                : publishedRuntimeService.resolveFields(
                        config,
                        fieldMapper.findByListConfigId(config.getId()));
        ConditionPartition conditionPartition = partitionCondition(allFields, condition);

        // 2. 基础查询（传入 listConfigId 以应用列表级权限规则）
        List<EntityDataDTO> records;
        if (!conditionPartition.baseCondition().isEmpty()) {
            records = dynamicService.findByCondition(
                    entityCode,
                    resolvedListKey,
                    conditionPartition.baseCondition());
        } else {
            records = dynamicService.findByEntityCode(entityCode, resolvedListKey);
        }

        if (records.isEmpty()) {
            return records;
        }

        return enrichRecords(
                entityCode,
                listKey,
                config,
                allFields,
                records,
                conditionPartition.extensionCondition());
    }

    /**
     * 按筛选条件分页查询实体数据；结果供列表展示。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 符合条件的实体数据结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public PageResult<EntityDataDTO> findPageWithConfig(
            String entityCode,
            String listKey,
            Map<String, Object> condition,
            long pageNum,
            long pageSize) {
        EntityListConfig config = findListConfig(entityCode, listKey);
        requireRequestedList(listKey, config);
        return findPageWithResolvedConfig(
                entityCode,
                listKey,
                config,
                condition,
                pageNum,
                pageSize);
    }

    /**
     * 按筛选条件分页查询实体数据；结果供列表展示。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param config 配置内容，决定后续分页已解析配置的处理规则
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 符合条件的实体数据结果，供调用方继续处理
     */
    public PageResult<EntityDataDTO> findPageWithResolvedConfig(
            String entityCode,
            String listKey,
            EntityListConfig config,
            Map<String, Object> condition,
            long pageNum,
            long pageSize) {
        String resolvedListKey = config != null ? config.getListKey() : null;
        List<EntityListField> allFields = config == null
                ? List.of()
                : publishedRuntimeService.resolveFields(
                        config,
                        fieldMapper.findByListConfigId(config.getId()));
        ConditionPartition conditionPartition = partitionCondition(allFields, condition);

        if (!conditionPartition.extensionCondition().isEmpty()) {
            List<EntityDataDTO> allRecords =
                    findListWithResolvedConfig(
                            entityCode,
                            listKey,
                            config,
                            condition);
            long safePageNum = Math.max(1, pageNum);
            long safePageSize = Math.max(1, Math.min(200, pageSize));
            int fromIndex = (int) Math.min(
                    allRecords.size(),
                    (safePageNum - 1) * safePageSize);
            int toIndex = (int) Math.min(
                    allRecords.size(),
                    fromIndex + safePageSize);
            return new PageResult<>(
                    new ArrayList<>(allRecords.subList(fromIndex, toIndex)),
                    allRecords.size(),
                    safePageNum,
                    safePageSize);
        }

        PageResult<EntityDataDTO> page = dynamicService.findPage(
                entityCode,
                resolvedListKey,
                conditionPartition.baseCondition(),
                pageNum,
                pageSize);
        List<EntityDataDTO> enriched = enrichRecords(
                entityCode,
                listKey,
                config,
                allFields,
                page.getRecords(),
                Map.of());
        return new PageResult<>(
                enriched,
                page.getTotal(),
                page.getPageNum(),
                page.getPageSize());
    }

    /**
     * 补充记录集合；结果供调用方的后续步骤使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param config 配置内容，决定后续记录集合的处理规则
     * @param allFields 全部字段，作为 {@code enrichUnifiedDataSources} 的输入影响后续处理
     * @param records 记录集合，作为 {@code enrichUnifiedDataSources} 的输入影响后续处理
     * @param extensionCondition 扩展条件，作为 {@code conditionEvaluator.filter} 的输入影响后续处理
     * @return 实体数据集合，供调用方遍历或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private List<EntityDataDTO> enrichRecords(
            String entityCode,
            String listKey,
            EntityListConfig config,
            List<EntityListField> allFields,
            List<EntityDataDTO> records,
            Map<String, Object> extensionCondition) {
        if (records == null || records.isEmpty()) {
            return records == null ? List.of() : records;
        }
        if (config != null) {
            enrichUnifiedDataSources(
                    entityCode,
                    listKey,
                    config,
                    allFields,
                    records);
            // 3. 筛选出非 ENTITY_FIELD 的字段。查询字段即使不展示，也必须补充值后再过滤。
            List<EntityListField> customFields = allFields.stream()
                    .filter(f -> Boolean.TRUE.equals(f.getShowInList()) || Boolean.TRUE.equals(f.getIsQuery()))
                    .filter(f -> !StringUtils.hasText(f.getInterfaceExtensionId()))
                    .filter(f -> !"ENTITY_FIELD".equals(f.getDataSourceType()) && f.getDataSourceType() != null)
                    .collect(Collectors.toList());

            // 4. 按数据源类型分组，调用对应的数据提供者
            Map<String, List<EntityListField>> fieldsByType = customFields.stream()
                    .collect(Collectors.groupingBy(EntityListField::getDataSourceType));

            Map<String, Object> context = new HashMap<>();
            context.put("entityCode", entityCode);
            context.put("listKey", listKey);
            context.put("listConfigId", config.getId());
            context.put("userId", UserContext.getUserId());
            context.put("userName", UserContext.getUsername());

            for (Map.Entry<String, List<EntityListField>> entry : fieldsByType.entrySet()) {
                String dataSourceType = entry.getKey();
                List<EntityListField> fields = entry.getValue();

                ListFieldDataProvider provider = providerRegistry.getProvider(dataSourceType);
                if (provider == null) {
                    boolean usedForFiltering = fields.stream().anyMatch(field ->
                            hasExtensionCondition(
                                    extensionCondition,
                                    field.getFieldCode()));
                    if (usedForFiltering) {
                        throw new IllegalStateException("查询字段的数据源未注册: " + dataSourceType);
                    }
                    log.warn("跳过历史未注册列表字段数据源: type={}, fields={}", dataSourceType,
                            fields.stream().map(EntityListField::getFieldCode).collect(Collectors.toList()));
                    continue;
                }

                try {
                    provider.enrich(records, fields, context);
                } catch (Exception e) {
                    log.error("列表字段数据补充失败: type={}, entityCode={}, failureType={}",
                            LogValue.safe(dataSourceType), LogValue.safe(entityCode), LogValue.failureType(e));
                    throw new IllegalStateException("列表扩展字段计算失败: " + dataSourceType, e);
                }
            }

            records = conditionEvaluator.filter(
                    records,
                    customFields,
                    extensionCondition);
        }

        actionCapabilityService.enrichRows(entityCode, config, records);
        return records;
    }

    /**
     * 补充统一数据{@code sources}；结果供调用方的后续步骤使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param config 配置内容，决定后续统一数据{@code sources}的处理规则
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @param records 记录集合，作为 {@code request.setInput} 的输入影响后续处理
     */
    private void enrichUnifiedDataSources(
            String entityCode,
            String listKey,
            EntityListConfig config,
            List<EntityListField> fields,
            List<EntityDataDTO> records) {
        for (EntityListField field : fields) {
            if (!StringUtils.hasText(field.getInterfaceExtensionId())
                    || (!Boolean.TRUE.equals(field.getShowInList())
                    && !Boolean.TRUE.equals(field.getIsQuery()))) {
                continue;
            }
            UiExtensionExecuteRequest request = new UiExtensionExecuteRequest();
            request.setUsage(UiDataSourceUsages.LIST_COLUMN);
            request.setConfigType("LIST");
            request.setConfigId(field.getListConfigId());
            request.setReleaseId(config.getActiveReleaseId());
            request.setReleaseVersion(config.getPublishedVersion());
            request.setServerPinnedRelease(
                    Boolean.TRUE.equals(config.getPinnedRelease()));
            request.setTargetType("COLUMN");
            request.setTargetKey(field.getFieldCode());
            request.setEntityCode(entityCode);
            request.setListKey(listKey);
            request.setInput(Map.of(
                    "field", field,
                    "records", records));
            log.info(
                    "开始执行列表列统一数据源: entityCode={}, listKey={}, listConfigId={}, fieldCode={}, dataSourceId={}, recordCount={}",
                    LogValue.safe(entityCode),
                    LogValue.safe(listKey),
                    LogValue.safe(field.getListConfigId()),
                    LogValue.safe(field.getFieldCode()),
                    LogValue.safe(field.getInterfaceExtensionId()),
                    records.size());
            try {
                Object result = uiDataSourceService.execute(
                        field.getInterfaceExtensionId(),
                        request);
                applyUnifiedColumnResult(field, records, result);
                log.info(
                        "列表列统一数据源执行完成: entityCode={}, listKey={}, fieldCode={}, dataSourceId={}, resultType={}, recordCount={}",
                        LogValue.safe(entityCode),
                        LogValue.safe(listKey),
                        LogValue.safe(field.getFieldCode()),
                        LogValue.safe(field.getInterfaceExtensionId()),
                        result == null
                                ? null
                                : result.getClass().getSimpleName(),
                        records.size());
            } catch (RuntimeException exception) {
                log.error(
                        "列表列统一数据源执行失败: entityCode={}, listKey={}, fieldCode={}, dataSourceId={}, failureType={}",
                        LogValue.safe(entityCode),
                        LogValue.safe(listKey),
                        LogValue.safe(field.getFieldCode()),
                        LogValue.safe(field.getInterfaceExtensionId()),
                        LogValue.failureType(exception));
                throw exception;
            }
        }
    }

    /**
     * 应用统一列结果，并将结果传给后续步骤。
     *
     * @param field 字段，作为 {@code putExtensionValue} 的输入影响后续处理
     * @param records 记录集合，供本方法应用统一列结果时使用
     * @param result 结果，供本方法应用统一列结果时使用
     */
    private void applyUnifiedColumnResult(
            EntityListField field,
            List<EntityDataDTO> records,
            Object result) {
        if (result instanceof Map<?, ?> values) {
            for (EntityDataDTO record : records) {
                Object value = values.get(record.getId());
                if (value == null) {
                    value = values.get(String.valueOf(record.getId()));
                }
                putExtensionValue(record, field.getFieldCode(), value);
            }
            return;
        }
        if (result instanceof List<?> values) {
            Map<String, Object> byRecord = new LinkedHashMap<>();
            for (Object item : values) {
                if (item instanceof Map<?, ?> map) {
                    Object recordId = map.get("recordId");
                    if (recordId == null) recordId = map.get("id");
                    if (recordId != null) {
                        byRecord.put(String.valueOf(recordId), map.get("value"));
                    }
                }
            }
            for (EntityDataDTO record : records) {
                putExtensionValue(
                        record,
                        field.getFieldCode(),
                        byRecord.get(record.getId()));
            }
            return;
        }
        for (EntityDataDTO record : records) {
            putExtensionValue(record, field.getFieldCode(), result);
        }
    }

    /**
     * 写入扩展值；后续读取或执行将使用更新后的状态。
     *
     * @param record 记录，供本方法写入扩展值时使用
     * @param fieldCode 字段编码，后续用于写入扩展值时定位或关联目标
     * @param value 待写入扩展值的原始输入，结果供调用方继续使用
     */
    private void putExtensionValue(
            EntityDataDTO record,
            String fieldCode,
            Object value) {
        if (record.getExtData() == null) {
            record.setExtData(new LinkedHashMap<>());
        }
        record.getExtData().put(fieldCode, value);
    }

    /**
     * 处理{@code partition}条件，并将结果传给后续步骤。
     *
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @return 处理后的{@code partition}条件结果，供调用方继续处理
     */
    private ConditionPartition partitionCondition(
            List<EntityListField> fields,
            Map<String, Object> condition) {
        if (condition == null || condition.isEmpty() || fields == null || fields.isEmpty()) {
            return new ConditionPartition(
                    condition == null ? new LinkedHashMap<>() : new LinkedHashMap<>(condition),
                    new LinkedHashMap<>());
        }

        Set<String> extensionCodes = fields.stream()
                .filter(field -> Boolean.TRUE.equals(field.getIsQuery()))
                .filter(field -> field.getDataSourceType() != null)
                .filter(field -> !"ENTITY_FIELD".equalsIgnoreCase(field.getDataSourceType()))
                .map(EntityListField::getFieldCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, Object> baseCondition = new LinkedHashMap<>();
        Map<String, Object> extensionCondition = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : condition.entrySet()) {
            String baseKey = stripConditionSuffix(entry.getKey());
            if (extensionCodes.contains(baseKey)) {
                extensionCondition.put(entry.getKey(), entry.getValue());
            } else {
                baseCondition.put(entry.getKey(), entry.getValue());
            }
        }
        return new ConditionPartition(baseCondition, extensionCondition);
    }

    /**
     * 生成{@code strip}条件后缀文本，供后续匹配或展示。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 处理后的{@code strip}条件后缀文本，供调用方比较或展示
     */
    private String stripConditionSuffix(String key) {
        if (key.endsWith("_start")) {
            return key.substring(0, key.length() - 6);
        }
        if (key.endsWith("_end")) {
            return key.substring(0, key.length() - 4);
        }
        if (key.endsWith("_op")) {
            return key.substring(0, key.length() - 3);
        }
        return key;
    }

    /**
     * 判断是否具有扩展条件；判断结果决定调用方的后续分支。
     *
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param fieldCode 字段编码，后续用于判断是否具有扩展条件时定位或关联目标
     * @return 扩展条件条件成立时为 true，否则为 false
     */
    private boolean hasExtensionCondition(Map<String, Object> condition, String fieldCode) {
        return condition.keySet().stream()
                .map(this::stripConditionSuffix)
                .anyMatch(fieldCode::equals);
    }

    /**
     * 校验并获取请求列表；不满足约束时阻止后续处理。
     *
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param config 配置内容，决定后续请求列表的处理规则
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void requireRequestedList(
            String listKey,
            EntityListConfig config) {
        if (StringUtils.hasText(listKey) && config == null) {
            throw new IllegalArgumentException(
                    "列表不存在或尚未发布: " + listKey);
        }
    }

    /**
     * 封装条件{@code partition}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param baseCondition 基础条件，保存在对象中供后续校验、查询或展示
     * @param extensionCondition 扩展条件，保存在对象中供后续校验、查询或展示
     */
    private record ConditionPartition(
            Map<String, Object> baseCondition,
            Map<String, Object> extensionCondition) {
    }

    /**
     * 查找列表配置
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @return 符合条件的实体列表配置结果，供调用方继续处理
     */
    public EntityListConfig findListConfig(String entityCode, String listKey) {
        return findListConfig(
                entityCode,
                listKey,
                null,
                null,
                null);
    }

    /**
     * 查询列表配置；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param releaseId 发布版本ID，后续用于查询列表配置时定位或关联目标
     * @param releaseVersion 发布版本，作为 {@code publishedRuntimeService.resolveConfig} 的输入影响后续处理
     * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @return 符合条件的实体列表配置结果，供调用方继续处理
     */
    public EntityListConfig findListConfig(
            String entityCode,
            String listKey,
            String releaseId,
            Integer releaseVersion,
            String releaseResolutionToken) {
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
        return publishedRuntimeService.resolveConfig(
                config,
                releaseId,
                releaseVersion,
                releaseResolutionToken);
    }
}
