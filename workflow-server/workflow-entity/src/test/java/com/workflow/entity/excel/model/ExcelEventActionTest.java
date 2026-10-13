package com.workflow.entity.excel.model;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ExcelEventActionTest {
    private Map<String, Object> step(Map<String, Object> config) {
        return new LinkedHashMap<>(Map.of("actionType", ExcelEventAction.TYPE, "strategy", "REPLACE", "actionConfig", config));
    }
    private Map<String, Object> config(String operation, String scope) {
        return Map.of("operation", operation, "dataScope", scope, "schemeRef", Map.of("mode", "EXPLICIT", "schemeCode", "finance"));
    }
    private void validate(Map<String, Object> step) {
        ExcelEventAction.validateStep(step, "LIST", "BUTTON", "TOOLBAR_BUTTON_CLICK");
    }
    @Test void acceptsStaticDefaultAndExplicitReferencesWithoutInterface() {
        assertThatCode(() -> validate(step(config("EXPORT", "SELECTED")))).doesNotThrowAnyException();
        assertThatCode(() -> validate(step(Map.of("operation", "IMPORT", "schemeRef", Map.of("mode", "DEFAULT")))))
                .doesNotThrowAnyException();
    }
    @Test void rejectsDynamicOrInterfaceSteps() {
        for (String key : List.of("extensionId", "serviceId", "operationCode", "executableSnapshot", "inputMapping", "outputMapping")) {
            var value = step(config("EXPORT", "SELECTED")); value.put(key, Map.of("value", "client.input"));
            assertThatThrownBy(() -> validate(value)).hasMessageContaining("接口或参数映射");
        }
        var conditional = step(config("EXPORT", "SELECTED")); conditional.put("condition", Map.of("field", "show"));
        assertThatThrownBy(() -> validate(conditional)).hasMessageContaining("无条件");
        conditional.remove("condition"); conditional.put("failurePolicy", "CONTINUE");
        assertThatThrownBy(() -> validate(conditional)).hasMessageContaining("停止执行");
    }
    @Test void rejectsEntityAndNonButtonOrigins() {
        var value = step(config("EXPORT", "SELECTED"));
        assertThatThrownBy(() -> ExcelEventAction.validateStep(value, "ENTITY", "BUTTON", "TOOLBAR_BUTTON_CLICK"))
                .hasMessageContaining("按钮事件");
        assertThatThrownBy(() -> ExcelEventAction.validateStep(value, "LIST", "OWNER", "LIST_LOAD"))
                .hasMessageContaining("按钮事件");
    }
    @Test void rowButtonsCannotImportOrExpandExportScope() {
        assertThatThrownBy(() -> ExcelEventAction.validateStep(step(config("IMPORT", "SELECTED")), "LIST", "BUTTON", "ROW_BUTTON_CLICK"))
                .hasMessageContaining("行按钮不支持");
        assertThatThrownBy(() -> ExcelEventAction.validateStep(step(config("EXPORT", "FILTERED")), "LIST", "BUTTON", "ROW_BUTTON_CLICK"))
                .hasMessageContaining("当前记录");
    }
    @Test void formBatchExportRequiresPublishedListSelectionConfiguration() {
        var config = new LinkedHashMap<>(config("EXPORT", "SELECTED"));
        assertThatThrownBy(() -> ExcelEventAction.validateStep(step(config), "FORM", "BUTTON", "FORM_BUTTON_CLICK"))
                .hasMessageContaining("LIST_DIALOG");
        config.put("listKey", "employees"); config.put("selectionSource", Map.of("type", "LIST_DIALOG"));
        assertThatCode(() -> ExcelEventAction.validateStep(step(config), "FORM", "BUTTON", "FORM_BUTTON_CLICK"))
                .doesNotThrowAnyException();
    }
    @Test void rejectsUnknownOperationScopeAndMalformedSchemeReference() {
        assertThatThrownBy(() -> validate(step(config("DELETE", "SELECTED")))).hasMessageContaining("操作无效");
        assertThatThrownBy(() -> validate(step(config("EXPORT", "ALL")))).hasMessageContaining("范围无效");
        assertThatThrownBy(() -> validate(step(Map.of("operation", "EXPORT", "schemeRef", Map.of("mode", "EXPLICIT")))))
                .hasMessageContaining("方案编码");
        assertThatThrownBy(() -> validate(step(Map.of("operation", "EXPORT", "schemeRef", Map.of("mode", "DEFAULT", "schemeCode", "finance")))))
                .hasMessageContaining("默认");
    }
}
