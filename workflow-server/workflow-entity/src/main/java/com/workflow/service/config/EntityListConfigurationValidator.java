package com.workflow.service.config;

import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.EntityListConfigDTO;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityListField;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.service.listfield.ListFieldDataProvider;
import com.workflow.service.listfield.ListFieldDataProviderRegistry;
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

    /**
     * 校验列表配置整体。
     *
     * @param dto 列表配置 DTO
     * @throws IllegalArgumentException 配置为空、实体信息缺失、标识不合法、数据范围模式错误等时抛出
     */
    public void validate(EntityListConfigDTO dto) {
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
        validateStructured(dto.getAllowedScenes(), "允许场景配置");
        validateStructured(dto.getSelectionConfig(), "选择模式配置");
        validateStructured(dto.getFixedFilterConfig(), "固定查询条件");
        validateStructured(dto.getContextBindingConfig(), "上下文绑定配置");

        List<EntityListField> fields = dto.getFields();
        if (fields == null) {
            return;
        }
        if (fields.size() > 200) {
            throw new IllegalArgumentException("单个列表最多配置 200 个字段");
        }

        Set<String> entityFieldIds = entityFieldMapper.findByEntityId(dto.getEntityId()).stream()
                .map(EntityField::getId)
                .map(String::valueOf)
                .collect(Collectors.toSet());
        Set<String> fieldCodes = new HashSet<>();
        for (EntityListField field : fields) {
            validateField(field, entityFieldIds, fieldCodes);
        }
    }

    /**
     * 校验单个列表字段：编码格式与唯一性、数据源类型与提供者能力、查询方式、列对齐、各类配置 JSON。
     *
     * @param field           列表字段配置
     * @param entityFieldIds  实体已有的字段 ID 集合（用于校验实体字段来源）
     * @param fieldCodes      已收集的字段编码集合（用于去重，会被本方法修改）
     */
    private void validateField(
            EntityListField field,
            Set<String> entityFieldIds,
            Set<String> fieldCodes) {
        if (field == null || !StringUtils.hasText(field.getFieldCode())
                || !FIELD_CODE.matcher(field.getFieldCode()).matches()) {
            throw new IllegalArgumentException("列表字段编码不合法");
        }
        String normalizedCode = field.getFieldCode().toLowerCase(Locale.ROOT);
        if (!fieldCodes.add(normalizedCode)) {
            throw new IllegalArgumentException("列表字段编码重复: " + field.getFieldCode());
        }

        String dataSourceType = StringUtils.hasText(field.getDataSourceType())
                ? field.getDataSourceType().trim().toUpperCase(Locale.ROOT)
                : "ENTITY_FIELD";
        field.setDataSourceType(dataSourceType);
        if ("ENTITY_FIELD".equals(dataSourceType)) {
            if (!StringUtils.hasText(field.getFieldId())
                    || !entityFieldIds.contains(String.valueOf(field.getFieldId()))) {
                throw new IllegalArgumentException("实体字段不存在: " + field.getFieldCode());
            }
        } else {
            providerRegistry.validate(field);
            ListFieldDataProvider provider = providerRegistry.getProvider(dataSourceType);
            if (!provider.supportsVirtualField()
                    && String.valueOf(field.getFieldId()).startsWith("virtual_")) {
                throw new IllegalArgumentException("数据源不支持虚拟字段: " + dataSourceType);
            }
            if (Boolean.TRUE.equals(field.getIsQuery()) && !provider.supportsQuery()) {
                throw new IllegalArgumentException("数据源不支持查询条件: " + dataSourceType);
            }
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
        structuredConfigValidator.parseObject(field.getColumnConfig(), "列展示配置");
        structuredConfigValidator.parseObject(field.getQueryConfig(), "查询配置");
        structuredConfigValidator.parseObject(field.getRenderConfig(), "渲染配置");
        field.setColumnConfig(blankToNull(field.getColumnConfig()));
        field.setQueryConfig(blankToNull(field.getQueryConfig()));
        field.setRenderConfig(blankToNull(field.getRenderConfig()));
    }

    /** 校验扩展组件/提供者标识格式 */
    private void validateExtensionName(String name, String label) {
        if (StringUtils.hasText(name) && !EXTENSION_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(label + "标识不合法");
        }
    }

    /** 空白字符串转 null */
    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    /** 校验结构化配置：非空时通过 JsonDocumentCodec 序列化校验其合法性 */
    private void validateStructured(Object value, String label) {
        if (value != null) {
            jsonDocumentCodec.write(value, label);
        }
    }
}
