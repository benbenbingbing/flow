package com.workflow.entity.list.application.validation;

import com.workflow.entity.ui.application.validation.StructuredConfigValidator;
import com.workflow.entity.ui.application.validation.UiConfigInterfaceReferenceValidator;
import com.workflow.entity.list.application.EntityListQueryPolicy;

import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.list.extension.ListFieldDataProviderRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 实体列表配置校验器
 * 
 * 对列表配置 DTO 进行保存前的合法性校验：列表标识、自定义组件/查询提供者标识、
 * 数据范围模式（INHERIT/NARROW/OVERRIDE）、访问权限码、各类结构化配置 JSON、
 * 字段数量上限、字段编码唯一性、数据源类型与虚拟字段/查询能力、查询方式、列对齐方式等。
 */
@Component
@RequiredArgsConstructor
public class EntityListConfigurationValidator {

    /** 列表标识正则：字母开头，字母数字下划线短横线，长度 1~100 */
    private static final Pattern LIST_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,99}");
    /** 字段编码正则：字母开头，字母数字下划线，长度 1~100 */
    private static final Pattern FIELD_CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,99}");
    /** 扩展组件标识正则 */
    private static final Pattern EXTENSION_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,99}");
    /** 支持的查询方式 */
    private static final Set<String> QUERY_TYPES = Set.of(
            "EQ", "NE", "LIKE", "NOT_LIKE", "GT", "GE", "LT", "LE",
            "BETWEEN", "IN", "NOT_IN", "EMPTY", "NOT_EMPTY");
    /** 支持的列对齐方式 */
    private static final Set<String> ALIGNMENTS = Set.of("left", "center", "right");
    /** 支持的数据范围模式 */
    private static final Set<String> DATA_SCOPE_MODES =
            Set.of("INHERIT", "NARROW", "OVERRIDE");
    /** 访问权限码格式正则 */
    private static final Pattern PERMISSION_CODE =
            Pattern.compile("[A-Za-z0-9:_-]{1,200}");

    private final StructuredConfigValidator structuredConfigValidator;
    private final JsonDocumentCodec jsonDocumentCodec;
    private final ListFieldDataProviderRegistry providerRegistry;
    private final EntityFieldMapper entityFieldMapper;
    /** 校验列表草稿引用的接口状态、上下文、作用域和 LIST_* 契约。 */
    private final UiConfigInterfaceReferenceValidator interfaceReferenceValidator;

    /**
     * 校验列表配置整体。
     *
     * @param dto 列表配置 DTO
     * @throws IllegalArgumentException 配置为空、实体信息缺失、标识不合法、数据范围模式错误等时抛出
     */
    public void validate(EntityListConfigDTO dto) {
        validate(dto, null);
    }

    /**
     * 校验一次单列草稿变更，避免其他历史列的旧取值方式阻止逐列编辑和修复。
     *
     * @param dto 已合并本次变更的完整列表；仍检查字段数量、编码唯一性及筛选/排序依赖
     * @param changedField 本次新增或修改的列，必须是 dto.fields 中的对象；只规范化、校验这一列的配置
     * @throws IllegalArgumentException 当前列或列表整体约束不合法时抛出；整包保存和发布仍须调用 validate
     */
    public void validateFieldChange(EntityListConfigDTO dto, EntityListField changedField) {
        if (changedField == null || dto == null || dto.getFields() == null
                || dto.getFields().stream().noneMatch(field -> field == changedField)) {
            throw new IllegalArgumentException("待校验的列必须包含在列表配置中");
        }
        validate(dto, changedField);
    }

    /** changedField 为空表示整包校验；单列保存保留跨列约束，但不重新校验其他列的来源和接口。 */
    private void validate(EntityListConfigDTO dto, EntityListField changedField) {
        if (dto == null) {
            throw new IllegalArgumentException("列表配置不能为空");
        }
        if (!StringUtils.hasText(dto.getEntityId()) || !StringUtils.hasText(dto.getEntityCode())) {
            throw new IllegalArgumentException("实体信息不能为空");
        }
        if (!StringUtils.hasText(dto.getListKey()) || !LIST_KEY.matcher(dto.getListKey()).matches()) {
            throw new IllegalArgumentException("列表标识只能包含字母、数字、下划线和短横线，且必须以字母开头");
        }
        validateExtensionName(dto.getCustomComponent(), "自定义列表组件");
        validateExtensionName(dto.getQueryProviderCode(), "自定义查询提供者");
        validateQuerySource(dto);
        String dataScopeMode = StringUtils.hasText(dto.getDataScopeMode())
                ? dto.getDataScopeMode().trim().toUpperCase(Locale.ROOT)
                : "INHERIT";
        if (!DATA_SCOPE_MODES.contains(dataScopeMode)) {
            throw new IllegalArgumentException("数据范围模式只能是 INHERIT、NARROW 或 OVERRIDE");
        }
        dto.setDataScopeMode(dataScopeMode);
        if (StringUtils.hasText(dto.getAccessPermissionCode())
                && !PERMISSION_CODE.matcher(dto.getAccessPermissionCode()).matches()) {
            throw new IllegalArgumentException("列表访问权限码格式不正确");
        }
        validateStructured(dto.getViewConfig(), "列表视图配置");
        validateStructured(dto.getToolbarConfig(), "工具栏配置");
        validateStructured(dto.getRowActionConfig(), "操作列配置");
        ListCellActionMappingPolicy.validateButtons("TOOLBAR", dto.getToolbarConfig());
        ListCellActionMappingPolicy.validateButtons("ROW", dto.getRowActionConfig());
        ListButtonSelectionPolicy.validateButtons("TOOLBAR", dto.getToolbarConfig());
        ListButtonSelectionPolicy.validateButtons("ROW", dto.getRowActionConfig());
        validateStructured(dto.getSelectionConfig(), "选择模式配置");
        validateStructured(dto.getFixedFilterConfig(), "固定查询条件");

        List<EntityListField> fields = dto.getFields();
        List<EntityListField> changedFields = changedField == null ? fields : List.of(changedField);
        EntityListQueryPolicy.validateConfiguration(changedFields);
        EntityListQueryPolicy.validateFilters(fields,
                com.workflow.entity.ui.application.PageParameterPolicy.map(dto.getFixedFilterConfig()));
        interfaceReferenceValidator.validateListDraft(
                dto.getId(),
                dto.getEntityId(),
                changedField == null ? dto.getQueryInterfaceExtensionId() : null,
                changedFields);
        if (fields == null) {
            fields = List.of();
        }
        if (fields.size() > 200) {
            throw new IllegalArgumentException("单个列表最多配置 200 个字段");
        }

        List<EntityField> entityFields = entityFieldMapper.findByEntityId(dto.getEntityId());
        // 参数用途按完整实体元数据校验，与顶部查询项、列表展示及是否已保存列配置无关。
        // 扩展值在分页后生成，仍不能成为数据库筛选目标。
        Set<String> parameterFields = entityFields.stream()
                .map(EntityField::getFieldCode).collect(Collectors.toSet());
        fields.stream().filter(field -> field != null && !EntityListQueryPolicy.isEntityField(field))
                .map(EntityListField::getFieldCode).forEach(parameterFields::remove);
        com.workflow.entity.ui.application.PageParameterPolicy.validate(
                com.workflow.entity.ui.application.PageParameterPolicy.map(dto.getViewConfig()), "LIST",
                parameterFields);
        Set<String> entityFieldIds = entityFields.stream()
                .map(EntityField::getId)
                .map(String::valueOf)
                .collect(Collectors.toSet());
        Set<String> fieldCodes = new HashSet<>();
        for (EntityListField field : fields) {
            validateFieldCode(field, fieldCodes);
            if (changedField == null || field == changedField) {
                validateField(field, entityFieldIds);
            }
        }
        var view = com.workflow.entity.ui.application.PageParameterPolicy.map(dto.getViewConfig());
        if (view.get("table") instanceof java.util.Map<?, ?> table
                && table.get("defaultSortField") instanceof String sortField && StringUtils.hasText(sortField)) {
            EntityListQueryPolicy.validateSort(fields, sortField);
            if (entityFields.stream().noneMatch(field -> sortField.equals(field.getFieldCode()))) {
                throw new IllegalArgumentException("默认排序字段不是实体字段: " + sortField);
            }
        }
    }

    /**
     * 校验列表查询接口绑定不能和内部查询 Provider 同时配置。
     *
     * @param dto 列表配置 DTO
     */
    private void validateQuerySource(EntityListConfigDTO dto) {
        boolean hasService =
                StringUtils.hasText(dto.getQueryInterfaceExtensionId());
        if (hasService
                && StringUtils.hasText(
                        dto.getQueryProviderCode())) {
            throw new IllegalArgumentException(
                    "列表查询接口扩展和自定义查询提供者不能同时配置");
        }
    }

    /**
     * 校验编码格式与唯一性；增量保存也必须检查完整列表，防止新列与既有列冲突。
     *
     * @param field           列表字段配置
     * @param fieldCodes      已收集的字段编码集合（用于去重，会被本方法修改）
     */
    private void validateFieldCode(EntityListField field, Set<String> fieldCodes) {
        if (field == null || !StringUtils.hasText(field.getFieldCode())
                || !FIELD_CODE.matcher(field.getFieldCode()).matches()) {
            throw new IllegalArgumentException("列表字段编码不合法");
        }
        String normalizedCode = field.getFieldCode().toLowerCase(Locale.ROOT);
        if (!fieldCodes.add(normalizedCode)) {
            throw new IllegalArgumentException("列表字段编码重复: " + field.getFieldCode());
        }
    }

    /**
     * 校验并规范化当前列的互斥取值方式、查询方式、列对齐和配置 JSON。
     *
     * @param field 编码已校验的列表字段配置
     * @param entityFieldIds 实体已有字段 ID，用于校验实体字段来源
     */
    private void validateField(EntityListField field, Set<String> entityFieldIds) {
        String dataSourceType = StringUtils.hasText(field.getDataSourceType())
                ? field.getDataSourceType().trim().toUpperCase(Locale.ROOT)
                : "ENTITY_FIELD";
        field.setDataSourceType(dataSourceType);
        // 取值方式互斥，不能依赖接口覆盖另一种来源的隐式优先级。
        boolean hasInterface = StringUtils.hasText(field.getInterfaceExtensionId());
        if (!"INTERFACE".equals(dataSourceType) && hasInterface) {
            throw new IllegalArgumentException("只有扩展接口取值方式可以绑定接口: " + field.getFieldCode());
        }
        switch (dataSourceType) {
            case "ENTITY_FIELD" -> {
                if (!StringUtils.hasText(field.getFieldId())
                        || !entityFieldIds.contains(String.valueOf(field.getFieldId()))) {
                    throw new IllegalArgumentException("实体字段不存在: " + field.getFieldCode());
                }
            }
            case "FIELD_TEMPLATE" -> providerRegistry.validate(field);
            case "INTERFACE" -> {
                // 接口列既可覆盖实体字段的展示，也可作为虚拟列；引用契约由 validateListDraft 统一校验。
                if (!hasInterface) {
                    throw new IllegalArgumentException("扩展接口取值方式必须选择接口: " + field.getFieldCode());
                }
            }
            // 整包保存和发布可能由其他历史列触发错误，必须指出配置位置和修复入口。
            default -> throw new IllegalArgumentException("列“"
                    + (StringUtils.hasText(field.getFieldName()) ? field.getFieldName() : field.getFieldCode())
                    + "”（字段编码：" + field.getFieldCode() + "）的取值配置与当前版本不兼容（"
                    + dataSourceType + "）。请打开该列的“高级配置”，重新选择可用的取值方式并补全配置后保存；"
                    + "组合文本请选择“字段组合”，业务计算请选择“扩展接口”并绑定接口。");
        }

        String queryType = StringUtils.hasText(field.getQueryType())
                ? field.getQueryType().trim().toUpperCase(Locale.ROOT)
                : "EQ";
        if (!QUERY_TYPES.contains(queryType)) {
            throw new IllegalArgumentException("不支持的查询方式: " + queryType);
        }
        field.setQueryType(queryType);

        String align = StringUtils.hasText(field.getAlign()) ? field.getAlign() : "left";
        if (!ALIGNMENTS.contains(align)) {
            throw new IllegalArgumentException("不支持的列对齐方式: " + align);
        }
        validateExtensionName(field.getRenderComponent(), "单元格组件");
        structuredConfigValidator.parseObject(field.getDataSourceConfig(), "数据源配置");
        EntityListQueryPolicy.validateColumnSort(field,
                structuredConfigValidator.parseObject(field.getColumnConfig(), "列展示配置"));
        structuredConfigValidator.parseObject(field.getQueryConfig(), "查询配置");
        structuredConfigValidator.parseObject(field.getRenderConfig(), "渲染配置");
        field.setColumnConfig(blankToNull(field.getColumnConfig()));
        field.setQueryConfig(blankToNull(field.getQueryConfig()));
        field.setRenderConfig(blankToNull(field.getRenderConfig()));
    }

    /**
     * 校验扩展组件/提供者标识格式
     *
     * @param name 名称，后续用于校验扩展名称时匹配或展示
     * @param label 标签，后续用于校验扩展名称时匹配或展示
     */
    private void validateExtensionName(String name, String label) {
        if (StringUtils.hasText(name) && !EXTENSION_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(label + "标识不合法");
        }
    }

    /**
     * 空白字符串转 null
     *
     * @param value 待处理空白截止空值的原始输入，结果供调用方继续使用
     * @return 处理后的空白截止空值文本，供调用方比较或展示
     */
    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    /**
     * 校验结构化配置：非空时通过 JsonDocumentCodec 序列化校验其合法性
     *
     * @param value 待校验{@code structured}的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于校验{@code structured}时匹配或展示
     */
    private void validateStructured(Object value, String label) {
        if (value != null) {
            jsonDocumentCodec.write(value, label);
        }
    }
}
