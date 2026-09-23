package com.workflow.entity.data.application;

import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Validation and normalization that must use one immutable published snapshot.
 */
final class PublishedEntityDataValidator {

    /**
     * 初始化已发布实体数据校验器，保存构造参数供后续方法使用。
     */
    private PublishedEntityDataValidator() {
    }

    /**
     * 清洗与校验；结果供调用方的后续步骤使用。
     *
     * @param snapshot 快照，供本方法清洗与校验时使用
     * @param storageData 存储数据，供本方法清洗与校验时使用
     * @param recordMapper 记录映射器，供本方法清洗与校验时使用
     * @param richTextSanitizer {@code rich}文本{@code sanitizer}，供本方法清洗与校验时使用
     */
    static void sanitizeAndValidate(
            EntityPublishedSnapshot snapshot,
            Map<String, Object> storageData,
            EntityRuntimeRecordMapper recordMapper,
            RichTextSanitizer richTextSanitizer) {
        if (snapshot.getFields() == null || snapshot.getFields().isEmpty()) {
            return;
        }
        for (EntityField field : snapshot.getFields()) {
            if (isRelationField(field) || !StringUtils.hasText(field.getFieldCode())) {
                continue;
            }
            String columnName = recordMapper.toColumnName(field.getFieldCode());
            Object value = storageData.get(columnName);
            if (field.getFieldType() == EntityField.FieldType.RICH_TEXT
                    && value instanceof String html) {
                value = richTextSanitizer.sanitize(html);
                storageData.put(columnName, value);
            }
            if (Boolean.TRUE.equals(field.getIsRequired()) && isBlank(value)) {
                throw new RuntimeException("字段必填: " + field.getFieldName());
            }
        }
    }

    /**
     * 判断是否关系字段；判断结果决定调用方的后续分支。
     *
     * @param field 字段，供本方法判断是否关系字段时使用
     * @return 关系字段条件成立时为 true，否则为 false
     */
    private static boolean isRelationField(EntityField field) {
        return field.getFieldType() == EntityField.FieldType.SUB_FORM
                || field.getFieldType() == EntityField.FieldType.SUB_LIST;
    }

    /**
     * 判断是否空白；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否空白的原始输入，结果供调用方继续使用
     * @return 空白条件成立时为 true，否则为 false
     */
    private static boolean isBlank(Object value) {
        return value == null || (value instanceof String string && string.isBlank());
    }
}
