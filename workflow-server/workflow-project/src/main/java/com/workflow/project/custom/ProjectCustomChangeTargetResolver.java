package com.workflow.project.custom;

import com.workflow.contracts.entity.mutation.EntityChangeTarget;
import com.workflow.contracts.entity.mutation.EntityChangeTargetContext;
import com.workflow.contracts.entity.mutation.EntityChangeTargetResolver;
import com.workflow.core.logging.LogValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 实体变更目标解析器示例。
 *
 * <p>编码为 {@value #CODE}。可通过 {@code recordIdPath} 从来源记录读取目标
 * ID，也可配置 {@code useSourceRecordId=true} 使用来源记录 ID。未解析到目标时
 * 返回空列表，不猜测业务目标。</p>
 */
@Slf4j
@Component
public class ProjectCustomChangeTargetResolver
        implements EntityChangeTargetResolver {

    public static final String CODE =
            "PROJECT_CUSTOM_CHANGE_TARGET";

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getDisplayName() {
        return "项目自定义变更目标";
    }

    @Override
    public Map<String, Object> configurationSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "recordIdPath", Map.of(
                                "type", "string",
                                "title", "目标记录 ID 路径",
                                "description",
                                "例如 data.targetRecordId"),
                        "useSourceRecordId", Map.of(
                                "type", "boolean",
                                "title", "使用来源记录 ID")));
    }

    @Override
    public List<EntityChangeTarget> resolve(
            EntityChangeTargetContext context) {
        Map<String, Object> configuration =
                context == null
                        || context.configuration() == null
                        ? Map.of()
                        : context.configuration();
        boolean useSourceRecordId =
                Boolean.parseBoolean(String.valueOf(
                        configuration.getOrDefault(
                                "useSourceRecordId",
                                false)));
        String recordId = useSourceRecordId
                ? context == null
                        ? null
                        : context.sourceRecordId()
                : text(readPath(
                        context == null
                                ? Map.of()
                                : context.sourceRecord(),
                        text(configuration.get(
                                "recordIdPath"))));
        log.info(
                "项目实体变更目标解析: code={}, sourceEntityCode={}, sourceRecordId={}, processInstanceId={}, useSourceRecordId={}, recordIdPath={}, resolved={}",
                CODE,
                LogValue.safe(context == null
                        ? null : context.sourceEntityCode()),
                LogValue.safe(context == null
                        ? null : context.sourceRecordId()),
                LogValue.safe(context == null
                        ? null : context.processInstanceId()),
                useSourceRecordId,
                LogValue.safe(configuration.get(
                        "recordIdPath")),
                StringUtils.hasText(recordId));
        if (!StringUtils.hasText(recordId)) {
            return List.of();
        }
        return List.of(new EntityChangeTarget(
                null,
                recordId,
                null,
                Map.of()));
    }

    private Object readPath(
            Map<String, Object> source,
            String path) {
        if (source == null || source.isEmpty()
                || !StringUtils.hasText(path)) {
            return null;
        }
        Object current = source;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private String text(Object value) {
        String result =
                value == null ? null : String.valueOf(value);
        return StringUtils.hasText(result)
                ? result.trim() : null;
    }
}
