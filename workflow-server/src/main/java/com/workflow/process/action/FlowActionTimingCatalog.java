package com.workflow.process.action;

import com.workflow.dto.FlowActionTimingOptionDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class FlowActionTimingCatalog {

    private final List<FlowActionTriggerProvider> providers;

    public FlowActionTimingCatalog(List<FlowActionTriggerProvider> providers) {
        this.providers = providers == null ? List.of() : providers;
    }

    public List<FlowActionTimingOptionDTO> list(String scopeType, String bpmnType) {
        FlowActionScopeType scope = parseScope(scopeType);
        boolean bpmnTypeSpecified = StringUtils.hasText(bpmnType);
        boolean userTask = isUserTask(bpmnType);
        List<FlowActionTimingOptionDTO> options = new ArrayList<>();
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
        for (FlowActionTriggerProvider provider : providers) {
            CollectionSupport.addAll(options, provider.getTriggerOptions());
        }
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

    public Optional<FlowActionTimingOptionDTO> find(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
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

    private static class CollectionSupport {
        private static <T> void addAll(List<T> target, Iterable<T> values) {
            if (values == null) {
                return;
            }
            values.forEach(target::add);
        }
    }
}
