package com.workflow.entity.form.application;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** 验证前端规则保存协议、错误配置拒绝和显式清空往返，不执行 JS 业务逻辑。 */
class FormCustomValidatorRulePolicyTest {
    private Map<String, Object> rule() {
        return new LinkedHashMap<>(Map.of("name", "amount", "version", 1,
                "params", Map.of("maxAmount", 1000), "triggers", List.of("BLUR", "CHANGE")));
    }
    private Map<String, Object> config(Object rule) { return Map.of("version", 1, "rules", List.of(rule)); }

    @Test
    void acceptsBindingsAndSubmissionOnlyRules() {
        assertDoesNotThrow(() -> FormCustomValidatorRulePolicy.validate(config(rule())));
        var rule = rule();
        rule.put("triggers", List.of());
        rule.put("params", Map.of());
        assertDoesNotThrow(() -> FormCustomValidatorRulePolicy.validate(config(rule)));
    }

    @Test
    void rejectsMalformedConfiguration() {
        for (Object invalid : List.of(Map.of(), List.of(), Map.of("version", 2, "rules", List.of()),
                Map.of("version", 1, "rules", List.of(rule(), rule())))) {
            assertThrows(IllegalArgumentException.class, () -> FormCustomValidatorRulePolicy.validate(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> FormCustomValidatorRulePolicy.validate(null));
        for (var entry : Map.of("name", "bad code()", "version", 1.5, "params", List.of(), "triggers", List.of("SUBMIT")).entrySet()) {
            var rule = rule();
            rule.put(entry.getKey(), entry.getValue());
            assertThrows(IllegalArgumentException.class, () -> FormCustomValidatorRulePolicy.validate(config(rule)));
        }
        var unknown = rule(); unknown.put("script", "alert(1)");
        assertThrows(IllegalArgumentException.class, () -> FormCustomValidatorRulePolicy.validate(config(unknown)));
    }

    @Test
    void nodeNormalizationPreservesEmptyRulesParamsAndTriggers() {
        var rule = rule(); rule.put("params", Map.of()); rule.put("triggers", List.of());
        for (var config : List.of(config(rule), Map.of("version", 1, "rules", List.of()))) {
            var source = Map.<String, Object>of("validation", Map.of("customValidators", config));
            var normalized = EntityFormNodePropertyPolicy.normalizeRules("FIELD", source, Map.of("fieldType", "DECIMAL"), false);
            assertEquals(source, normalized.active());
            assertEquals(normalized.active(), EntityFormNodePropertyPolicy.normalizeRules("FIELD", normalized.active(), Map.of("fieldType", "DECIMAL"), false).active());
        }
        var raw = new LinkedHashMap<String, Object>(); raw.put("customValidators", null);
        assertThrows(IllegalArgumentException.class, () -> EntityFormNodePropertyPolicy.normalizeRules("FIELD", Map.of("validation", raw), Map.of("fieldType", "DECIMAL"), false));
    }
}
