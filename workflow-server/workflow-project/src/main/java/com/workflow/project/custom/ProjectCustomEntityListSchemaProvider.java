package com.workflow.project.custom;

import com.workflow.contracts.entity.list.EntityListRuntimeContext;
import com.workflow.contracts.entity.list.EntityListSchemaProvider;
import com.workflow.core.logging.LogValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自定义实体列表结构扩展示例。
 *
 * <p>编码为 {@value #CODE}。当前只在 Schema 顶层增加
 * {@code projectCustomSchema} 标记，不覆盖平台已有字段。</p>
 */
@Slf4j
@Component
public class ProjectCustomEntityListSchemaProvider
        implements EntityListSchemaProvider {

    public static final String CODE =
            "PROJECT_CUSTOM_LIST_SCHEMA";

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getDisplayName() {
        return "项目自定义列表结构";
    }

    @Override
    public Map<String, Object> enhance(
            EntityListRuntimeContext context,
            Map<String, Object> schema) {
        Map<String, Object> result =
                new LinkedHashMap<>(
                        schema == null ? Map.of() : schema);
        result.put("projectCustomSchema", Map.of(
                "providerCode", CODE,
                "enabled", true,
                "description",
                "项目列表结构扩展示例已执行"));
        log.info(
                "项目列表结构扩展执行: code={}, entityCode={}, listKey={}, scene={}, originalKeys={}",
                CODE,
                LogValue.safe(context == null
                        ? null : context.entityCode()),
                LogValue.safe(context == null
                        ? null : context.listKey()),
                LogValue.safe(context == null
                        ? null : context.scene()),
                schema == null
                        ? java.util.Set.of()
                        : schema.keySet());
        return result;
    }
}
