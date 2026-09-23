package com.workflow.biz.project.custom;

import com.workflow.contracts.entity.list.spi.EntityListActionProvider;
import com.workflow.contracts.entity.list.model.EntityListRuntimeContext;
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
        return "项目自定义列表动作";
    }

    /**
     * 执行项目自定义实体列表动作提供者，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续项目自定义实体列表动作提供者步骤传递身份、配置或状态
     * @param actionKey 动作键，后续用于授权校验、关联或幂等去重
     * @param payload 载荷，后续用于执行项目自定义实体列表动作提供者并传递处理结果
     * @return 执行后的项目自定义实体列表动作提供者结果，供调用方继续处理
     */
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
