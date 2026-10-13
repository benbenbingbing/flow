package com.workflow.entity.excel.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.excel.model.ExcelEventAction;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.application.UiEventBindingService;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 从已发布事件主步骤读取 Excel 方案可用性，仅提供按钮展示提示。
 * 不执行 Provider，也不调用设计器接口；真实执行仍由运行解析器重新检查权限。
 *
 * <p>展示探测必须暂停调用方事务：严格方案解析会在配置缺失时抛出业务异常，
 * 即使这里捕获异常，参与调用方事务的 REQUIRED 代理仍会将其标记为 rollback-only。
 * 所有展示入口统一隔离，只读取已提交的发布配置；实际导入、执行和发布校验继续使用严格解析器。</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ExcelButtonAvailabilityService {
    private final EntityExcelConfigurationService configurations;
    private final ObjectProvider<UiEventBindingService> bindings;
    private final ObjectProvider<UiInterfaceExtensionService> interfaces;
    private final ObjectMapper json;

    /**
     * 检查系统默认导入/导出按钮引用的已发布方案，供列表展示禁用原因。
     * 返回 null 表示可用；缺少默认方案或方案失效时返回业务提示，实际执行仍须严格解析方案。
     */
    public String defaultReason(String entityCode, String operation) {
        try {
            configurations.resolvePublished(entityCode, operation, "DEFAULT", null);
            return null;
        } catch (IllegalArgumentException | BusinessConflictException failure) {
            return failure.getMessage();
        }
    }

    /** 批量解析某一位置的自定义按钮，每个按钮只读取一次事件链，供整页数据行复用。 */
    public Map<String, String> listReasons(String entityCode, EntityListConfig list, List<Map<String, Object>> buttons, String event) {
        if (list == null || !Boolean.TRUE.equals(list.getPublishedSnapshot())) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> button : buttons) {
            if (!"custom".equals(button.get("type")) || Boolean.FALSE.equals(button.get("enabled"))) continue;
            UiEventExecuteRequest origin = origin(entityCode, "LIST", list.getId(), String.valueOf(button.get("key")), event);
            origin.setListKey(list.getListKey()); origin.setReleaseId(list.getActiveReleaseId());
            origin.setReleaseVersion(list.getPublishedVersion()); origin.setConfigHash(list.getConfigHash()); origin.setRuntimeContext(list.getRuntimeContext());
            origin.setServerPinnedRelease(Boolean.TRUE.equals(list.getPinnedRelease()));
            String reason = reason(origin);
            if (reason != null) result.put(origin.getTargetKey(), reason);
        }
        return result;
    }

    /** 只有事件模式的真实已发布表单按钮才查询 Excel 主步骤，其他按钮模式保持原能力。 */
    public String formReason(String entityCode, EntityForm form, Map<String, Object> button) {
        return formReason(entityCode, form, button, null);
    }

    /** 当前记录导出要求有已保存身份；导入不依赖当前页面未保存的字段。 */
    public String formReason(String entityCode, EntityForm form, Map<String, Object> button, String recordId) {
        if (!"event".equals(Objects.toString(button.get("customMode"), "event"))) return null;
        UiEventExecuteRequest origin = origin(entityCode, "FORM", form.getId(), String.valueOf(button.get("key")), "FORM_BUTTON_CLICK");
        origin.setRecordId(recordId);
        origin.setReleaseId(form.getRuntimeReleaseId()); origin.setReleaseVersion(form.getRuntimeReleaseVersion());
        origin.setConfigHash(form.getConfigHash()); origin.setRuntimeContext(form.getRuntimeContext());
        return reason(origin);
    }

    private UiEventExecuteRequest origin(String entity, String type, String id, String key, String event) {
        UiEventExecuteRequest origin = new UiEventExecuteRequest(); origin.setEntityCode(entity); origin.setConfigType(type);
        origin.setConfigId(id); origin.setTargetType("BUTTON"); origin.setTargetKey(key); origin.setEventCode(event);
        return origin;
    }

    private String reason(UiEventExecuteRequest origin) {
        UiEventBindingService.ResolvedEventChain chain;
        try { chain = bindings.getObject().resolvePublished(origin); }
        catch (RuntimeException failure) { return null; } // 普通事件读取错误仍由其既有机制处理，不能误标为 Excel 错误。
        if (chain == null || !Objects.equals(chain.entityCode(), origin.getEntityCode())) return null;
        for (Map<String, Object> step : chain.steps()) {
            if (!"REPLACE".equals(step.get("strategy")) || step.get("condition") != null
                    && (!(step.get("condition") instanceof Map<?, ?> condition) || !condition.isEmpty())) continue;
            JsonNode configuration;
            try {
                if (ExcelEventAction.isExcelStep(step)) {
                    ExcelEventAction.validateStep(step, origin.getConfigType(), origin.getTargetType(), origin.getEventCode());
                    configuration = json.valueToTree(ExcelEventAction.configuration(step));
                } else {
                    Object document = step.get("executableSnapshot");
                    Object extensionId = step.get("extensionId") == null ? step.get("serviceId") : step.get("extensionId");
                    // 历史 LIST 接口运行时仍读取当前定义；FORM 才使用发布冻结的接口快照。
                    if ((document == null || "LIST".equals(origin.getConfigType())) && extensionId != null) {
                        document = interfaces.getObject().freezeOperation(String.valueOf(extensionId),
                                step.get("operationCode") == null ? null : String.valueOf(step.get("operationCode"))).document();
                    }
                    JsonNode definition = document instanceof String text ? json.readTree(text) : json.valueToTree(document);
                    if (definition == null || !"entity_excel_action".equals(definition.path("providerCode").asText())) continue;
                    configuration = definition.path("implementationConfigDocument");
                    if (configuration.isTextual()) configuration = json.readTree(configuration.asText());
                }
            } catch (Exception failure) {
                if (ExcelEventAction.isExcelStep(step)) return "Excel 操作配置不可用，请联系管理员";
                continue;
            }
            try {
                JsonNode reference = configuration.path("schemeRef");
                configurations.resolvePublished(origin.getEntityCode(), configuration.path("operation").asText(),
                        reference.path("mode").asText(), reference.path("schemeCode").asText(null));
                if ("FORM".equals(origin.getConfigType()) && "EXPORT".equals(configuration.path("operation").asText())
                        && "CURRENT_RECORD".equals(configuration.path("dataScope").asText("CURRENT_RECORD"))
                        && (origin.getRecordId() == null || origin.getRecordId().isBlank())) {
                    return "请先保存记录再导出";
                }
                return null;
            } catch (IllegalArgumentException | BusinessConflictException failure) {
                return failure.getMessage();
            } catch (Exception failure) {
                return "Excel 操作配置不可用，请联系管理员";
            }
        }
        return null;
    }
}
