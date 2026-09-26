package com.workflow.process.action.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.port.EntityCodeCatalogPort;
import com.workflow.contracts.process.action.model.*;
import com.workflow.contracts.process.action.spi.FlowActionFailureStrategyProvider;
import com.workflow.contracts.process.action.spi.FlowActionProvider;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

/** 策略目录负责稳定版本注册、适用范围及参数校验，运行时不回退到“最新版本”。 */
@Component
public class FlowActionFailureStrategyCatalog {
    private final Map<String, FlowActionFailureStrategyProvider> providers = new LinkedHashMap<>();
    private final Map<String, FailureStrategyDescriptor> descriptors = new LinkedHashMap<>();
    private final ObjectMapper json;
    private final EntityCodeCatalogPort entities;
    private final ApplicationContext beans;

    public FlowActionFailureStrategyCatalog(List<FlowActionFailureStrategyProvider> extensions,
            ObjectMapper json, EntityCodeCatalogPort entities, ApplicationContext beans) {
        this.json = json;
        this.entities = entities;
        this.beans = beans;
        for (var provider : extensions) {
            var d = provider.descriptor();
            if (d == null || d.code() == null || !d.code().matches("[A-Z][A-Z0-9_]{0,63}")
                    || d.version() == null || !d.version().matches("[A-Za-z0-9._-]{1,32}")
                    || d.displayName() == null || d.displayName().isBlank()
                    || d.supportedExecutionModes().isEmpty() || d.possibleDispositions().isEmpty()) {
                throw new IllegalArgumentException("失败策略身份或能力声明不完整");
            }
            Set<String> keys = new HashSet<>();
            for (var parameter : d.configSchema()) {
                if (parameter.key() == null || !parameter.key().matches("[A-Za-z][A-Za-z0-9_]{0,63}")
                        || !keys.add(parameter.key()) || !Set.of("string", "number", "boolean", "select").contains(parameter.type())
                        || (parameter.type().equals("select") && parameter.options().isEmpty())
                        || (parameter.min() != null && parameter.max() != null && parameter.min() > parameter.max())) {
                    throw new IllegalArgumentException("失败策略参数定义不合法: " + d.code());
                }
                if (parameter.defaultValue() != null) validateValue(parameter, parameter.defaultValue());
            }
            String key = key(d.code(), d.version());
            if (providers.putIfAbsent(key, provider) != null) {
                throw new IllegalArgumentException("失败策略编码和版本重复: " + key);
            }
            descriptors.put(key, d);
        }
    }

    /** 仅返回当前流程实体可使用的策略；模式不兼容项由页面保留并说明原因。 */
    public List<FailureStrategyDescriptor> list(String processConfigId) {
        String entity = entities.findEntityCodeByProcessDefinitionId(processConfigId);
        return descriptors.values().stream().filter(d -> visible(d, entity)).toList();
    }

    /** 按精确版本查找实现，缺失时拒绝，不能自动替换为最新版本。 */
    public FlowActionFailureStrategyProvider require(String code, String version) {
        var provider = providers.get(key(code, version));
        if (provider == null) throw new IllegalArgumentException("自定义失败策略不可用: " + code + "@" + version);
        return provider;
    }

    /** 读取注册时冻结的能力声明，避免实现反复返回可变元数据。 */
    public FailureStrategyDescriptor descriptor(FlowAction action) {
        require(action.getFailureStrategyCode(), action.getFailureStrategyVersion());
        return descriptors.get(key(action.getFailureStrategyCode(), action.getFailureStrategyVersion()));
    }

    /** 保存与发布共同使用的校验；前端过滤不能代替后端的可见范围和处理器能力检查。 */
    public void validate(FlowAction action) {
        var d = descriptor(action);
        var mode = FlowActionExecutionMode.valueOf(action.getExecutionMode().toUpperCase(Locale.ROOT));
        if (!visible(d, entities.findEntityCodeByProcessDefinitionId(action.getProcessConfigId()))) {
            throw new IllegalArgumentException("自定义失败策略不适用于当前流程实体");
        }
        if (!d.supportedExecutionModes().contains(mode)
                || d.possibleDispositions().stream().noneMatch(result -> allowed(mode, result))) {
            throw new IllegalArgumentException("自定义失败策略不支持当前执行方式");
        }
        if (mode == FlowActionExecutionMode.AFTER_COMMIT
                && d.possibleDispositions().contains(FailureDisposition.RETRY) && !retryable(action)) {
            throw new IllegalArgumentException("该策略可能重试，但动作处理器未声明可安全重试");
        }
        configuration(action);
    }

    /** 运行时只核对发布快照与当前实现的可执行能力，不读取当前流程草稿或最新配置。 */
    public void validateSnapshot(FlowAction action, String entityCode, boolean retryable) {
        var d = descriptor(action);
        var mode = FlowActionExecutionMode.valueOf(action.getExecutionMode());
        if (!visible(d, entityCode) || !d.supportedExecutionModes().contains(mode)) {
            throw new IllegalArgumentException("发布快照与当前策略实现不兼容");
        }
        if (mode == FlowActionExecutionMode.AFTER_COMMIT && d.possibleDispositions().contains(FailureDisposition.RETRY) && !retryable) {
            throw new IllegalArgumentException("当前处理器已不支持策略要求的安全重放");
        }
        configuration(action);
    }

    /** 配置只接受 Schema 中声明的标量类型；默认值由服务端补齐，禁止静默丢弃未知字段。 */
    public Map<String, Object> configuration(FlowAction action) {
        Map<String, Object> values;
        try {
            String raw = action.getFailureStrategyConfig();
            values = raw == null || raw.isBlank() ? new LinkedHashMap<>()
                    : json.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
            if (values == null) throw new IllegalArgumentException("策略参数必须为 JSON 对象");
        } catch (Exception error) {
            throw new IllegalArgumentException("策略参数必须为 JSON 对象", error);
        }
        var schema = descriptor(action).configSchema();
        Set<String> known = new HashSet<>();
        for (var p : schema) {
            known.add(p.key());
            Object value = values.get(p.key());
            if (value == null) value = p.defaultValue();
            if (value == null) {
                if (p.required()) throw new IllegalArgumentException("请填写策略参数: " + p.label());
                values.remove(p.key());
            } else {
                validateValue(p, value);
                values.put(p.key(), value);
            }
        }
        if (!known.containsAll(values.keySet())) throw new IllegalArgumentException("策略参数含未声明字段");
        return Collections.unmodifiableMap(values);
    }

    /** 读取当前处理器的幂等能力，供发布、运行和人工重放复核。 */
    public boolean retryable(FlowAction action) {
        return beans.getBean(action.getInterfaceName(), FlowActionProvider.class).retryable();
    }

    public static boolean custom(FlowAction action) {
        return action != null && "CUSTOM".equalsIgnoreCase(action.getFailurePolicy());
    }

    public static boolean allowed(FlowActionExecutionMode mode, FailureDisposition result) {
        return mode == FlowActionExecutionMode.IN_TRANSACTION
                ? result == FailureDisposition.ROLLBACK || result == FailureDisposition.CONTINUE
                : result == FailureDisposition.RETRY || result == FailureDisposition.IGNORE || result == FailureDisposition.MANUAL;
    }

    private static boolean visible(FailureStrategyDescriptor d, String entity) {
        return d.entityCodes().isEmpty() || (entity != null && d.entityCodes().contains(entity));
    }

    private static String key(String code, String version) { return code + "@" + version; }

    private static void validateValue(FailureStrategyParameter p, Object value) {
        boolean valid = switch (p.type()) {
            case "string" -> value instanceof String s && s.length() <= 1000 && (!p.required() || !s.isBlank());
            case "boolean" -> value instanceof Boolean;
            case "select" -> value instanceof String && p.options().contains(value);
            case "number" -> value instanceof Number n && validNumber(p, n);
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("策略参数类型或范围不合法: " + p.label());
    }

    private static boolean validNumber(FailureStrategyParameter p, Number n) {
        try {
            long value = new BigDecimal(n.toString()).longValueExact();
            return (p.min() == null || value >= p.min()) && (p.max() == null || value <= p.max());
        } catch (ArithmeticException | NumberFormatException error) { return false; }
    }
}
