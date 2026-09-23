package com.workflow.biz.project.custom;

import com.workflow.contracts.entity.list.model.EntityListRuntimeContext;
import com.workflow.contracts.entity.list.spi.EntityListSchemaProvider;
import com.workflow.core.logging.LogValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自定义实体列表结构扩展示例。
 *
 * <p>编码为 {@value #CODE}。当前在 Schema 的 {@code viewConfig} 中增加
 * {@code projectCustomSchema} 标记，不覆盖平台已有字段。标记放在
 * {@code viewConfig} 内，是为了经过平台强类型 DTO 转换后仍可供前端和验收
 * 脚本读取。</p>
 */
@Slf4j
@Component
public class ProjectCustomEntityListSchemaProvider
        implements EntityListSchemaProvider {

    public static final String CODE =
            "PROJECT_CUSTOM_LIST_SCHEMA";

    /**
     * 读取编码；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的编码文本，供调用方比较或展示
     */
    @Override
    public String getCode() {
        return CODE;
    }

    /**
     * 读取用户可见名称，供页面和操作日志展示。
     *
     * @return 读取后的展示名称文本，供调用方比较或展示
     */
    @Override
    public String getDisplayName() {
        return "项目自定义列表结构";
    }

    /**
     * 整理{@code enhance}数据，供调用方遍历或继续处理。
     *
     * @param context 执行上下文，向后续{@code enhance}步骤传递身份、配置或状态
     * @param schema 结构，供本方法处理{@code enhance}时使用
     * @return {@code enhance}键值结果，供调用方继续处理
     */
    @Override
    public Map<String, Object> enhance(
            EntityListRuntimeContext context,
            Map<String, Object> schema) {
        Map<String, Object> result =
                new LinkedHashMap<>(
                        schema == null ? Map.of() : schema);
        Map<String, Object> viewConfig =
                new LinkedHashMap<>();
        Object configuredView =
                result.get("viewConfig");
        if (configuredView instanceof Map<?, ?> configuredMap) {
            configuredMap.forEach((key, value) ->
                    viewConfig.put(String.valueOf(key), value));
        }
        viewConfig.put("projectCustomSchema", Map.of(
                "providerCode", CODE,
                "enabled", true,
                "description",
                "项目列表结构扩展示例已执行"));
        result.put("viewConfig", viewConfig);
        log.info(
                "项目列表结构扩展执行: code={}, entityCode={}, listKey={}, scene={}, originalKeys={}, viewConfigKeys={}",
                CODE,
                LogValue.safe(context == null
                        ? null : context.entityCode()),
                LogValue.safe(context == null
                        ? null : context.listKey()),
                LogValue.safe(context == null
                        ? null : context.scene()),
                schema == null
                        ? java.util.Set.of()
                        : schema.keySet(),
                viewConfig.keySet());
        return result;
    }
}
