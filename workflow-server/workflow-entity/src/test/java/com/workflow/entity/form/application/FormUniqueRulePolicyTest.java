package com.workflow.entity.form.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.form.application.model.FormUniqueCandidate;
import com.workflow.entity.form.application.model.FormUniqueRule;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormUniqueRulePolicyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FormUniqueRulePolicy policy = new FormUniqueRulePolicy(
            objectMapper,
            new PublishedFormConditionEvaluator(objectMapper));

    @Test
    void resolvesEnabledRuleAndSkipsAbsentOrDisabledRules() {
        EntityFormField enabled = field(
                "name",
                "项目名称",
                """
                {
                  "uniqueness": {
                    "version": 1,
                    "enabled": true,
                    "ruleId": "uq_project_name",
                    "mode": "GLOBAL",
                    "ignoreBlank": true,
                    "precheck": {
                      "enabled": true,
                      "trigger": "CHANGE",
                      "debounceMs": 350,
                      "watchConditionFields": true
                    }
                  }
                }
                """);
        EntityFormField disabled = field(
                "code",
                "项目编码",
                """
                {"uniqueness":{"version":1,"enabled":false,"mode":"GLOBAL"}}
                """);
        EntityFormField absent = field("description", "说明", "{}");
        EntityForm form = new EntityForm();
        form.setFields(List.of(enabled, disabled, absent));

        List<FormUniqueRule> rules = policy.resolveRules(form);

        assertEquals(1, rules.size());
        FormUniqueRule rule = rules.get(0);
        assertEquals("uq_project_name", rule.ruleId());
        assertEquals("name", rule.fieldCode());
        assertEquals(FormUniqueRule.Mode.GLOBAL, rule.mode());
        assertTrue(rule.precheck().enabled());
        assertEquals(FormUniqueRule.Trigger.CHANGE, rule.precheck().trigger());
        assertEquals(350, rule.precheck().debounceMs());
    }

    @Test
    void mergesEditPatchAndAppliesConditionalRuleToFinalRecord() {
        FormUniqueRule rule = policy.resolveRules(List.of(field(
                "name",
                "项目名称",
                conditionalRule()))).get(0);

        FormUniqueCandidate candidate = policy.prepare(
                rule,
                Map.of("name", "旧名称", "status", "DRAFT"),
                Map.of("data", Map.of("name", "  Project A  "),
                        "status", "IN_PROGRESS"));

        assertTrue(candidate.applicable());
        assertFalse(candidate.ignored());
        assertEquals("project a", candidate.normalizedValue());
        assertEquals("IN_PROGRESS", candidate.record().get("status"));
        assertEquals("  Project A  ", candidate.record().get("name"));
    }

    @Test
    void conditionalConflictRequiresSameConditionAndNormalizedValue() {
        FormUniqueRule rule = policy.resolveRules(List.of(field(
                "name",
                "项目名称",
                conditionalRule()))).get(0);

        assertTrue(policy.conflicts(
                rule,
                "project a",
                Map.of(
                        "name", " PROJECT A ",
                        "status", "IN_PROGRESS")));
        assertFalse(policy.conflicts(
                rule,
                "project a",
                Map.of(
                        "name", "PROJECT A",
                        "status", "DRAFT")));
        assertEquals("1", policy.normalize(new BigDecimal("1.000")));
    }

    @Test
    void nonIgnoredBlankParticipatesWithStableEmptyValue() {
        FormUniqueRule rule = policy.resolveRules(List.of(field(
                "name",
                "项目名称",
                """
                {"uniqueness":{"version":1,"mode":"GLOBAL","ignoreBlank":false}}
                """))).get(0);

        FormUniqueCandidate candidate = policy.prepare(
                rule,
                Map.of(),
                Map.of("name", "   "));

        assertFalse(candidate.ignored());
        assertEquals("", candidate.normalizedValue());
        assertTrue(policy.conflicts(
                rule,
                candidate.normalizedValue(),
                Map.of("name", "")));
    }

    @Test
    void defaultsPrecheckToBlurWhenConfigurationIsOmitted() {
        FormUniqueRule rule = policy.resolveRules(List.of(field(
                "name",
                "项目名称",
                """
                {"uniqueness":{"version":1,"mode":"GLOBAL"}}
                """))).get(0);

        assertTrue(rule.precheck().enabled());
        assertEquals(FormUniqueRule.Trigger.BLUR, rule.precheck().trigger());
        assertEquals(500, rule.precheck().debounceMs());

        FormUniqueRule partial = policy.resolveRules(List.of(field(
                "code",
                "项目编码",
                """
                {"uniqueness":{"version":1,"mode":"GLOBAL","precheck":{"trigger":"SUBMIT_ONLY"}}}
                """))).get(0);
        assertTrue(partial.precheck().enabled());
        assertEquals(
                FormUniqueRule.Trigger.SUBMIT_ONLY,
                partial.precheck().trigger());
    }

    @Test
    void booleanNormalizationMatchesTinyintStorageValues() {
        assertEquals("1", policy.normalize(true));
        assertEquals("0", policy.normalize(false));
        assertEquals(
                policy.normalize(true),
                policy.normalize(1));
        assertEquals(
                policy.normalize(false),
                policy.normalize(0));
    }

    @Test
    void temporalNormalizationMatchesFormAndJdbcRepresentations() {
        assertEquals(
                "2026-08-27 09:30:15",
                policy.normalize(LocalDateTime.of(
                        2026, 8, 27, 9, 30, 15)));
        assertEquals(
                "2026-08-27 09:30:15",
                policy.normalize(Timestamp.valueOf(
                        "2026-08-27 09:30:15")));
        assertEquals(
                "2026-08-27",
                policy.normalize(LocalDate.of(2026, 8, 27)));
        assertEquals(
                "2026-08-27",
                policy.normalize(java.sql.Date.valueOf(
                        "2026-08-27")));

        EntityFormField dateTimeField = field(
                "meetingTime",
                "会议时间",
                "{\"uniqueness\":{\"version\":1,\"mode\":\"GLOBAL\"}}");
        dateTimeField.setFieldType("DATETIME");
        FormUniqueRule rule = policy.resolveRules(
                List.of(dateTimeField)).get(0);
        FormUniqueCandidate candidate = policy.prepare(
                rule,
                Map.of(),
                Map.of("meetingTime", "2026-08-27 09:30:15"));

        assertTrue(policy.conflicts(
                rule,
                candidate.normalizedValue(),
                Map.of("meetingTime", LocalDateTime.of(
                        2026, 8, 27, 9, 30, 15))));
        assertTrue(policy.conflicts(
                rule,
                candidate.normalizedValue(),
                Map.of("meetingTime", OffsetDateTime.parse(
                        "2026-08-27T09:30:15+08:00"))));
        FormUniqueCandidate isoMillisCandidate = policy.prepare(
                rule,
                Map.of(),
                Map.of(
                        "meetingTime",
                        "2026-08-27T09:30:15.987"));
        assertEquals(
                "2026-08-27 09:30:15",
                isoMillisCandidate.normalizedValue());
        assertTrue(policy.conflicts(
                rule,
                isoMillisCandidate.normalizedValue(),
                Map.of(
                        "meetingTime",
                        Timestamp.valueOf(
                                "2026-08-27 09:30:15.123456"))));
    }

    @Test
    void validatesConditionReferencesAndRejectsComplexFields() {
        EntityFormField conditional = field(
                "name",
                "项目名称",
                conditionalRule());
        assertDoesNotThrow(() -> policy.validate(
                List.of(conditional),
                Set.of("name", "status")));
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(
                        List.of(conditional),
                        Set.of("name")));

        EntityFormField attachment = field(
                "attachments",
                "附件",
                """
                {"uniqueness":{"version":1,"mode":"GLOBAL"}}
                """);
        attachment.setFieldType("FILE");
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(
                        List.of(attachment),
                        Set.of("attachments")));
    }

    @Test
    void publishedRuleRequiresPersistentTargetAndConditionFields() {
        EntityFormField conditional = field(
                "name",
                "项目名称",
                conditionalRule());

        assertDoesNotThrow(() -> policy.validatePublished(
                List.of(conditional),
                Set.of("name", "status")));
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validatePublished(
                        List.of(conditional),
                        Set.of("status")));
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validatePublished(
                        List.of(conditional),
                        Set.of("name")));
    }

    private String conditionalRule() {
        return """
                {
                  "uniqueness": {
                    "version": 1,
                    "ruleId": "uq_project_name_active",
                    "mode": "CONDITIONAL",
                    "ignoreBlank": true,
                    "normalization": "TRIM_CASE_INSENSITIVE",
                    "condition": {
                      "version": 1,
                      "root": {
                        "type": "GROUP",
                        "logic": "OR",
                        "children": [
                          {
                            "type": "CONDITION",
                            "property": "status",
                            "operator": "==",
                            "value": "IN_PROGRESS"
                          },
                          {
                            "type": "CONDITION",
                            "property": "status",
                            "operator": "==",
                            "value": "APPROVING"
                          }
                        ]
                      }
                    },
                    "message": "项目名称在当前状态下已存在",
                    "precheck": {"enabled":true,"trigger":"CHANGE"}
                  }
                }
                """;
    }

    private EntityFormField field(
            String code,
            String label,
            String validationRules) {
        EntityFormField field = new EntityFormField();
        field.setFieldCode(code);
        field.setFieldName(label);
        field.setFieldLabel(label);
        field.setFieldType("STRING");
        field.setValidationRules(validationRules);
        return field;
    }
}
