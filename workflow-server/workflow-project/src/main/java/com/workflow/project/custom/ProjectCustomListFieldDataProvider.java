package com.workflow.project.custom;

import com.workflow.core.logging.LogValue;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.list.extension.ListFieldDataProvider;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 列表自定义字段数据提供者示例。
 *
 * <p>数据源类型为 {@value #DATA_SOURCE_TYPE}。每个配置字段会在
 * {@code extData} 中写入可见的演示文本，便于从前端确认 Provider 已执行。</p>
 */
@Slf4j
@Component
public class ProjectCustomListFieldDataProvider
        implements ListFieldDataProvider {

    public static final String DATA_SOURCE_TYPE =
            "PROJECT_CUSTOM_FIELD";

    @Override
    public String getDataSourceType() {
        return DATA_SOURCE_TYPE;
    }

    @Override
    public String getDisplayName() {
        return "项目自定义日志字段";
    }

    @Override
    public String getDescription() {
        return "写入项目自定义演示文本并记录执行日志。";
    }

    @Override
    public boolean supportsQuery() {
        return false;
    }

    @Override
    public List<Map<String, Object>> getConfigSchema() {
        return List.of(Map.of(
                "key", "labelPrefix",
                "label", "展示前缀",
                "type", "text",
                "required", false,
                "defaultValue", "项目扩展"));
    }

    @Override
    public void enrich(
            List<EntityDataDTO> records,
            List<EntityListField> fields,
            Map<String, Object> context) {
        List<EntityDataDTO> safeRecords =
                records == null ? List.of() : records;
        List<EntityListField> safeFields =
                fields == null ? List.of() : fields;
        for (EntityListField field : safeFields) {
            if (field == null
                    || field.getFieldCode() == null
                    || field.getFieldCode().isBlank()) {
                continue;
            }
            for (EntityDataDTO record : safeRecords) {
                if (record == null) {
                    continue;
                }
                if (record.getExtData() == null) {
                    record.setExtData(new HashMap<>());
                }
                String identity =
                        record.getDataNo() == null
                                || record.getDataNo().isBlank()
                                ? record.getId()
                                : record.getDataNo();
                record.getExtData().put(
                        field.getFieldCode(),
                        "项目扩展:"
                                + String.valueOf(identity));
            }
        }
        log.info(
                "项目列表字段数据补充完成: dataSourceType={}, entityCode={}, listKey={}, recordCount={}, fieldCount={}",
                DATA_SOURCE_TYPE,
                LogValue.safe(context == null
                        ? null : context.get("entityCode")),
                LogValue.safe(context == null
                        ? null : context.get("listKey")),
                safeRecords.size(),
                safeFields.size());
    }
}
