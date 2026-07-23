package com.workflow.process.action;

import com.workflow.dto.FlowActionTimingOptionDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 流程动作触发时机目录。
 *
 * <p>合并平台内置的 {@link FlowActionTriggerTiming} 与各 {@link FlowActionTriggerProvider} 扩展的自定义时机，
 * 为前端提供按作用域与 BPMN 元素类型过滤后的可选时机列表。</p>
 */
@Component
public class FlowActionTimingCatalog {

    /** 扩展触发时机提供器集合 */
    private final List<FlowActionTriggerProvider> providers;

    public FlowActionTimingCatalog(List<FlowActionTriggerProvider> providers) {
        this.providers = providers == null ? List.of() : providers;
    }

    /**
     * 列出可用触发时机选项，支持按作用域与 BPMN 元素类型过滤。
     *
     * @param scopeType 作用域类型；为空表示不限
     * @param bpmnType   BPMN 元素类型；用于判断是否为用户任务
     * @return 去重后的触发时机选项列表
     */
    public List<FlowActionTimingOptionDTO> list(String scopeType, String bpmnType) {
        FlowActionScopeType scope = parseScope(scopeType);
        boolean bpmnTypeSpecified = StringUtils.hasText(bpmnType);
        boolean userTask = isUserTask(bpmnType);
        List<FlowActionTimingOptionDTO> options = new ArrayList<>();
        // 先收集内置标准时机，按作用域与用户任务限制过滤
        for (FlowActionTriggerTiming timing : FlowActionTriggerTiming.values()) {
            if (scope != null && !timing.getScopeTypes().contains(scope)) {
                continue;
            }
            if (timing.isUserTaskOnly() && bpmnTypeSpecified && !userTask) {
                continue;
            }
            FlowActionScopeType optionScope = timing.getScopeTypes().iterator().next();
            options.add(new FlowActionTimingOptionDTO(
                    timing.name(),
                    timing.getLabel(),
                    timing.getDescription(),
                    optionScope.name(),
                    timing.isUserTaskOnly(),
                    timing.getDefaultExecutionMode().name(),
                    timing.getDefaultFailurePolicy().name(),
                    timing.getAvailableContext(),
                    false));
        }
        // 再收集扩展提供器注入的自定义时机
        for (FlowActionTriggerProvider provider : providers) {
            CollectionSupport.addAll(options, provider.getTriggerOptions());
        }
        // 按作用域与用户任务限制再次过滤，并按 value 去重保留首项
        return options.stream()
                .filter(option -> scope == null || scope.name().equalsIgnoreCase(option.getScopeType()))
                .filter(option -> !Boolean.TRUE.equals(option.getUserTaskOnly())
                        || !bpmnTypeSpecified
                        || userTask)
                .collect(java.util.stream.Collectors.toMap(
                        option -> option.getValue().toUpperCase(Locale.ROOT),
                        option -> option,
                        (existing, ignored) -> existing,
                        java.util.LinkedHashMap::new))
                .values()
                .stream()
                .toList();
    }

    /**
     * 按编码查找触发时机选项。
     *
     * @param code 时机编码；为空返回 Optional.empty()
     * @return 触发时机选项；未注册返回 Optional.empty()
     */
    public Optional<FlowActionTimingOptionDTO> find(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        // 优先匹配内置标准时机
        for (FlowActionTriggerTiming timing : FlowActionTriggerTiming.values()) {
            if (timing.name().equals(normalized)) {
                FlowActionScopeType optionScope = timing.getScopeTypes().iterator().next();
                return Optional.of(new FlowActionTimingOptionDTO(
                        timing.name(),
                        timing.getLabel(),
                        timing.getDescription(),
                        optionScope.name(),
                        timing.isUserTaskOnly(),
                        timing.getDefaultExecutionMode().name(),
                        timing.getDefaultFailurePolicy().name(),
                        timing.getAvailableContext(),
                        false));
            }
        }
        // 再到扩展提供器中查找
        return providers.stream()
                .flatMap(provider -> provider.getTriggerOptions().stream())
                .filter(option -> normalized.equalsIgnoreCase(option.getValue()))
                .findFirst();
    }

    private FlowActionScopeType parseScope(String scopeType) {
        if (!StringUtils.hasText(scopeType)) {
            return null;
        }
        return FlowActionScopeType.valueOf(scopeType.trim().toUpperCase(Locale.ROOT));
    }

    private boolean isUserTask(String bpmnType) {
        return StringUtils.hasText(bpmnType)
                && bpmnType.toLowerCase(Locale.ROOT).contains("usertask");
    }

    /**
     * 集合工具：将可迭代元素安全地追加到目标集合（忽略 null）。
     */
    private static class CollectionSupport {
        private static <T> void addAll(List<T> target, Iterable<T> values) {
            if (values == null) {
                return;
            }
            values.forEach(target::add);
        }
    }
}
