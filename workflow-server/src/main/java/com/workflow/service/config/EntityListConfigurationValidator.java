package com.workflow.service.config;

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

@Component
@RequiredArgsConstructor
public class EntityListConfigurationValidator {

    private static final Pattern LIST_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,99}");
    private static final Pattern FIELD_CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,99}");
    private static final Pattern EXTENSION_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,99}");
    private static final Set<String> QUERY_TYPES = Set.of(
            "EQ", "NE", "LIKE", "NOT_LIKE", "GT", "GE", "LT", "LE",
            "BETWEEN", "IN", "NOT_IN", "EMPTY", "NOT_EMPTY");
    private static final Set<String> ALIGNMENTS = Set.of("left", "center", "right");

    private final StructuredConfigValidator structuredConfigValidator;
    private final ListFieldDataProviderRegistry providerRegistry;
    private final EntityFieldMapper entityFieldMapper;

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
        structuredConfigValidator.parseObject(dto.getViewConfig(), "列表视图配置");
        dto.setViewConfig(blankToNull(dto.getViewConfig()));
        structuredConfigValidator.parseJson(dto.getToolbarConfig(), "工具栏配置");
        structuredConfigValidator.parseJson(dto.getRowActionConfig(), "操作列配置");

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

    private void validateExtensionName(String name, String label) {
        if (StringUtils.hasText(name) && !EXTENSION_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(label + "标识不合法");
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
