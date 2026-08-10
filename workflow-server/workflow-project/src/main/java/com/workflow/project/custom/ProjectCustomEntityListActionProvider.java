package com.workflow.project.custom;

import com.workflow.contracts.entity.list.EntityListActionProvider;
import com.workflow.contracts.entity.list.EntityListRuntimeContext;
import com.workflow.core.logging.LogValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 自定义实体列表动作示例。
 *
 * <p>编码为 {@value #CODE}。当前平台尚未提供统一配置目录，
 * 可在接入列表动作路由后按此编码调用。示例只打印日志并返回参数键。</p>
 */
@Slf4j
@Component
public class ProjectCustomEntityListActionProvider
        implements EntityListActionProvider {

    public static final String CODE =
            "PROJECT_CUSTOM_LIST_ACTION";

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getDisplayName() {
        return "项目自定义列表动作";
    }

    @Override
    public Object execute(
            EntityListRuntimeContext context,
            String actionKey,
            Map<String, Object> payload) {
        Map<String, Object> safePayload =
                payload == null ? Map.of() : payload;
        log.info(
                "项目列表动作执行: code={}, actionKey={}, entityCode={}, listKey={}, scene={}, payloadKeys={}",
                CODE,
                LogValue.safe(actionKey),
                LogValue.safe(context == null
                        ? null : context.entityCode()),
                LogValue.safe(context == null
                        ? null : context.listKey()),
                LogValue.safe(context == null
                        ? null : context.scene()),
                safePayload.keySet());
        return Map.of(
                "handledBy", CODE,
                "status", "LOGGED",
                "payloadKeys",
                List.copyOf(safePayload.keySet()));
    }
}
