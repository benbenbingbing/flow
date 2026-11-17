package com.workflow.process.action.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 单前置动作规则：只扩展同组提交后动作，不改变业务生命周期与事务内调用顺序。 */
public final class FlowActionDependencies {
    public static final String RESULT_REFERENCE = "$predecessor";
    private FlowActionDependencies() {}

    /** 保存和发布均检查整张图，拒绝跨组、失效引用和循环；排序变化不改变已有依赖。 */
    public static void validate(List<FlowAction> actions) {
        Map<String, FlowAction> byId = new HashMap<>();
        for (var action : actions) if (action.getId() != null) byId.put(action.getId(), action);
        for (var action : actions) {
            if (!StringUtils.hasText(action.getPredecessorActionId())) continue;
            var predecessor = byId.get(action.getPredecessorActionId());
            if (predecessor == null || !Boolean.TRUE.equals(predecessor.getEnabled())
                    || !Objects.equals(action.getProcessConfigId(), predecessor.getProcessConfigId())
                    || !Objects.equals(action.getScopeType(), predecessor.getScopeType())
                    || !Objects.equals(action.getElementId(), predecessor.getElementId())
                    || !Objects.equals(action.getTriggerTiming(), predecessor.getTriggerTiming())
                    || !"AFTER_COMMIT".equals(action.getExecutionMode())
                    || !Objects.equals(action.getExecutionMode(), predecessor.getExecutionMode())) {
                throw new IllegalArgumentException("前置动作必须为同一绑定位置、同一时机下启用的提交后动作");
            }
            var visited = new HashSet<String>();
            var cursor = action;
            while (cursor != null) {
                if (cursor.getId() != null && !visited.add(cursor.getId())) {
                    throw new IllegalArgumentException("前置动作不能引用自身或形成循环依赖");
                }
                cursor = byId.get(cursor.getPredecessorActionId());
            }
        }
    }

    /** 业务结果只支持明确的字段路径，不引入脚本或表达式执行能力。 */
    public static void validateParams(FlowAction action, ObjectMapper mapper) {
        if (!StringUtils.hasText(action.getParamsJson())) return;
        try {
            var params = mapper.readTree(action.getParamsJson());
            if (!params.isObject()) throw new IllegalArgumentException("动作参数必须是 JSON 对象");
            for (var value : params) {
                if (!value.isObject() || !value.has(RESULT_REFERENCE)) continue;
                if (value.size() != 1 || !value.path(RESULT_REFERENCE).isTextual()
                        || !validPath(value.path(RESULT_REFERENCE).asText())
                        || !StringUtils.hasText(action.getPredecessorActionId())
                        || !"AFTER_COMMIT".equals(action.getExecutionMode())) {
                    throw new IllegalArgumentException("前置结果参数需先选择提交后前置动作，并填写有效字段路径（如 contractNo）");
                }
            }
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException("流程动作参数 JSON 不合法", error);
        }
    }

    /** 允许对象字段及数组下标；禁止空路径、方法调用和表达式。 */
    public static boolean validPath(String path) {
        return path != null && path.length() <= 256
                && path.matches("[A-Za-z_][A-Za-z0-9_]*(\\.(?:[A-Za-z_][A-Za-z0-9_]*|[0-9]+))*");
    }

    /** 配置迁移按依赖顺序创建新草稿 ID；保存后的展示排序仍使用原 sortOrder。 */
    public static List<FlowAction> dependencyOrder(List<FlowAction> actions) {
        validate(actions);
        var remaining = new java.util.ArrayList<>(actions);
        var ordered = new java.util.ArrayList<FlowAction>();
        var completed = new HashSet<String>();
        while (!remaining.isEmpty()) {
            var ready = remaining.stream().filter(action -> !StringUtils.hasText(action.getPredecessorActionId())
                    || completed.contains(action.getPredecessorActionId())).toList();
            if (ready.isEmpty()) throw new IllegalArgumentException("前置动作依赖无法解析");
            for (var action : ready) { ordered.add(action); completed.add(action.getId()); }
            remaining.removeAll(ready);
        }
        return ordered;
    }
}
