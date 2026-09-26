package com.workflow.entity.list.application;

import com.workflow.core.logging.LogValue;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.model.EntityExportBatch;
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
    private final EntityActionCapabilityService actionCapabilityService;
    private final EntityListPublishedRuntimeService publishedRuntimeService;
    private final UiInterfaceExtensionService uiDataSourceService;
    private final JsonDocumentCodec jsonDocumentCodec;

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
        EntityListQueryPolicy.validateConfiguration(allFields);
        EntityListQueryPolicy.validateFilters(allFields, condition);
        EntityListQueryPolicy.validateSort(allFields, defaultSort(config).field());
        Map<String, Object> baseCondition = condition == null ? Map.of() : condition;

        // 2. 基础查询（传入 listConfigId 以应用列表级权限规则）
        List<EntityDataDTO> records;
        if (!baseCondition.isEmpty()) {
            records = dynamicService.findByCondition(
                    entityCode,
                    resolvedListKey,
                    baseCondition);
        } else {
            records = dynamicService.findByEntityCode(entityCode, resolvedListKey);
        }

        if (records.isEmpty()) {
            return records;
        }

        List<EntityDataDTO> enriched = enrichRecords(
                entityCode,
                listKey,
                config,
                allFields,
                records);
        // 兼容未分页内部调用的排序；导出入口改用分批数据库查询。
        List<EntityDataDTO> sorted = new ArrayList<>(enriched);
        sortConfiguredRecords(sorted, defaultSort(config));
        return sorted;
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
        EntityListQueryPolicy.validateConfiguration(allFields);
        EntityListQueryPolicy.validateFilters(allFields, condition);
        EntityListQueryPolicy.validateSort(allFields, defaultSort(config).field());
        Map<String, Object> baseCondition = condition == null ? Map.of() : condition;
        DefaultSort sort = defaultSort(config);

        PageResult<EntityDataDTO> page = dynamicService.findPage(
                entityCode,
                resolvedListKey,
                baseCondition,
                pageNum,
                pageSize,
                sort.field(),
                sort.direction());
        List<EntityDataDTO> enriched = enrichRecords(
                entityCode,
                listKey,
                config,
                allFields,
                page.getRecords());
        return new PageResult<>(
                enriched,
                page.getTotal(),
                page.getPageNum(),
                page.getPageSize());
    }

    /**
     * 只从调用方已解析的发布配置读取排序，避免设计器草稿影响运行页。
     * 字段合法性由动态实体服务对照发布字段校验，SQL 提供者再校验标识符。
     */
    private DefaultSort defaultSort(EntityListConfig config) {
        if (config == null || !StringUtils.hasText(config.getViewConfig())) {
            return new DefaultSort(null, null);
        }
        Map<String, Object> view = jsonDocumentCodec.readObject(
                config.getViewConfig(), "列表视图配置");
        if (!(view.get("table") instanceof Map<?, ?> table)) {
            return new DefaultSort(null, null);
        }
        Object field = table.get("defaultSortField");
        Object direction = table.get("defaultSortDirection");
        return new DefaultSort(field instanceof String value ? value : null,
                direction instanceof String value ? value : null);
    }

    private record DefaultSort(String field, String direction) {}

    /**
     * 导出只读取一个有界批次；ID 限制独立传入 SQL，不能覆盖列表原有过滤条件。
     * 使用调用开始时解析的发布配置，避免批次之间漂移到新的列表发布版本。
     */
    public EntityExportBatch findExportBatchWithResolvedConfig(String entityCode, String listKey,
            EntityListConfig config, Map<String, Object> condition, List<String> selectedIds,
            EntityExportBatch.Cursor cursor) {
        List<EntityListField> fields = config == null ? List.of() : publishedRuntimeService.resolveFields(
                config, fieldMapper.findByListConfigId(config.getId()));
        EntityListQueryPolicy.validateConfiguration(fields);
        EntityListQueryPolicy.validateFilters(fields, condition);
        DefaultSort sort = defaultSort(config);
        EntityListQueryPolicy.validateSort(fields, sort.field());
        EntityExportBatch batch = dynamicService.findExportBatch(entityCode, config == null ? null : config.getListKey(),
                condition, selectedIds, cursor, sort.field(), sort.direction());
        return new EntityExportBatch(enrichRecords(entityCode, listKey, config, fields, batch.records()), batch.nextCursor());
    }

    /**
     * 兼容未分页内部调用的列表排序；运行页和导出入口均在数据库排序。
     * 数字按数值比较，避免金额 9 与 10 被当作字符串排反。
     */
    private void sortConfiguredRecords(List<EntityDataDTO> records, DefaultSort sort) {
        if (records.size() < 2 || !StringUtils.hasText(sort.field())) {
            return;
        }
        String direction = StringUtils.hasText(sort.direction())
                ? sort.direction().trim().toUpperCase(Locale.ROOT) : "ASC";
        if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
            throw new IllegalArgumentException("默认排序方向仅支持 ASC 或 DESC");
        }
        Comparator<EntityDataDTO> comparator = (left, right) -> {
            int compared = compareSortValues(
                    entitySortValue(left, sort.field()),
                    entitySortValue(right, sort.field()));
            if ("DESC".equals(direction)) {
                compared = -compared;
            }
            return compared != 0 ? compared : compareSortValues(right.getId(), left.getId());
        };
        records.sort(comparator);
    }

    private Object entitySortValue(EntityDataDTO record, String field) {
        if (record.getData() != null && record.getData().containsKey(field)) {
            return record.getData().get(field);
        }
        return switch (field) {
            case "id" -> record.getId();
            case "name" -> record.getName();
            case "code" -> record.getCode();
            case "status" -> record.getStatus();
            case "processStatus" -> record.getProcessStatus();
            case "processInstanceId" -> record.getProcessInstanceId();
            case "processStartTime" -> record.getProcessStartTime();
            case "processEndTime" -> record.getProcessEndTime();
            case "currentTaskId" -> record.getCurrentTaskId();
            case "currentTaskName" -> record.getCurrentTaskName();
            case "currentTaskAssignee" -> record.getCurrentTaskAssignee();
            case "submitterId" -> record.getSubmitterId();
            case "submitterName" -> record.getSubmitterName();
            case "deptId" -> record.getDeptId();
            case "deptName" -> record.getDeptName();
            case "submitTime" -> record.getSubmitTime();
            case "create_time" -> record.getCreateTime();
            case "update_time" -> record.getUpdateTime();
            case "create_by" -> record.getCreateBy();
            case "update_by" -> record.getUpdateBy();
            case "deleted" -> record.getDeleted();
            default -> null;
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compareSortValues(Object left, Object right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        if (left instanceof Number && right instanceof Number) {
            return new java.math.BigDecimal(left.toString())
                    .compareTo(new java.math.BigDecimal(right.toString()));
        }
        if (left instanceof Comparable comparable && left.getClass().isInstance(right)) {
            return comparable.compareTo(right);
        }
        return left.toString().compareTo(right.toString());
    }

    /**
     * 补充记录集合；结果供调用方的后续步骤使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param config 配置内容，决定后续记录集合的处理规则
     * @param allFields 全部字段，作为 {@code enrichUnifiedDataSources} 的输入影响后续处理
     * @param records 记录集合，作为 {@code enrichUnifiedDataSources} 的输入影响后续处理
     * @return 实体数据集合，供调用方遍历或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private List<EntityDataDTO> enrichRecords(
            String entityCode,
            String listKey,
            EntityListConfig config,
            List<EntityListField> allFields,
            List<EntityDataDTO> records) {
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
            // 扩展列只补充当前查询结果中需要展示的值，不能再影响页边界或总数。
            List<EntityListField> customFields = allFields.stream()
                    .filter(f -> Boolean.TRUE.equals(f.getShowInList()))
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
                    || !Boolean.TRUE.equals(field.getShowInList())) {
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
