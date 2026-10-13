package com.workflow.entity.excel.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiEventBindingService;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * 导入事务的配置资格检查，同时用于整文件原子模式及分批模式。
 * 只有无外部副作用的前置处理和 READ 数据事件可以随批次执行，保证失败批次回滚及重试不会留下远程写入。
 */
@Service
@RequiredArgsConstructor
public class EntityExcelAtomicImportPolicy {
    private final UiConfigReleaseService releases;
    private final UiEventBindingService bindings;
    private final UiInterfaceExtensionService interfaces;
    private final ObjectMapper mapper;

    public void requireSupported(EntityExcelRuntimeResolver.Plan plan, String formConfigHash) {
        if (plan.writeForm() == null) throw new IllegalArgumentException("导入方案缺少写入表单");
        UiEventExecuteRequest event = new UiEventExecuteRequest();
        event.setConfigType("FORM"); event.setConfigId(plan.writeForm().form().getId());
        event.setEntityCode(plan.scheme().entityCode()); event.setEventCode("DATA_CREATE");
        event.setTargetType("OWNER"); event.setReleaseId(plan.writeForm().releaseId());
        event.setReleaseVersion(plan.writeForm().releaseVersion()); event.setConfigHash(formConfigHash);
        var chain = bindings.resolvePublished(event);
        // 当前表单以内容摘要发布；必须检查执行事件实际解析的快照，不能回读旧 ACTIVE 发布记录。
        if (chain.snapshot() == null) throw new IllegalArgumentException("导入写入表单缺少已发布快照");
        inspect(chain.snapshot(), 0);
        for (var step : chain.steps()) {
            if ("REPLACE".equals(normalize(step.get("strategy"))) || Boolean.TRUE.equals(step.get("replaceResult")))
                throw new IllegalArgumentException("原子Excel导入不能使用替代平台新增的数据事件，请保留标准新增链路");
            if (!"STOP".equals(normalize(step.getOrDefault("failurePolicy", "STOP"))))
                throw new IllegalArgumentException("原子Excel导入的数据事件必须失败即终止");
            String id = interfaceId(step);
            if (id != null) requireRead(id, step.get("operationCode"));
        }
    }

    private void inspect(Object value, int depth) {
        if (depth > 80) throw new IllegalArgumentException("写入表单配置嵌套过深");
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if ("BEFORE_SUBMIT".equals(entry.getKey())) {
                    List<?> configured = entry.getValue() instanceof List<?> list ? list : List.of(entry.getValue());
                    for (Object item : configured) {
                        if (!(item instanceof Map<?, ?> binding) || !Boolean.TRUE.equals(binding.get("sideEffectFree")))
                            throw new IllegalArgumentException("写入表单含未声明无副作用的提交前处理，暂不支持原子Excel导入");
                        String id = interfaceId(binding);
                        if (id == null) throw new IllegalArgumentException("提交前处理缺少接口身份");
                        requireRead(id, binding.get("operationCode"));
                    }
                } else inspect(entry.getValue(), depth + 1);
            }
        } else if (value instanceof List<?> list) {
            list.forEach(item -> inspect(item, depth + 1));
        } else if (value instanceof String text && (text.stripLeading().startsWith("{") || text.stripLeading().startsWith("["))) {
            Object decoded;
            try { decoded = mapper.readValue(text, Object.class); }
            catch (Exception ignored) { return; }
            inspect(decoded, depth + 1);
        }
    }

    private void requireRead(String id, Object operationCode) {
        var frozen = interfaces.freezeOperation(id, operationCode == null ? null : String.valueOf(operationCode));
        Map<String, Object> definition;
        try { definition = mapper.readValue(frozen.document(), new TypeReference<>() {}); }
        catch (Exception ex) { throw new IllegalArgumentException("导入事件接口定义无效", ex); }
        if (!"READ".equals(definition.get("interfaceKind")))
            throw new IllegalArgumentException("原子Excel导入不支持包含外部写入的事件；请改为提交后Outbox处理");
        try {
            Object raw = definition.get("executionPolicyDocument");
            Map<String, Object> policy = raw instanceof String text && !text.isBlank()
                    ? mapper.readValue(text, new TypeReference<>() {})
                    : raw instanceof Map<?, ?> ? mapper.convertValue(raw, new TypeReference<>() {}) : Map.of();
            if (!"FAIL".equals(normalize(policy.getOrDefault("failurePolicy", "FAIL"))))
                throw new IllegalArgumentException("原子Excel导入的接口必须失败即终止");
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("导入事件接口执行策略无效", ex);
        }
    }

    /** 历史 serviceId 仍会被运行时执行，资格检查必须采用同一回退规则。 */
    private String interfaceId(Map<?, ?> value) {
        Object current = value.get("extensionId");
        if (current == null || String.valueOf(current).isBlank()) current = value.get("serviceId");
        return current == null || String.valueOf(current).isBlank() ? null : String.valueOf(current);
    }

    private String normalize(Object value) { return value == null ? "" : String.valueOf(value).trim().toUpperCase(Locale.ROOT); }
}
