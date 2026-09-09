package com.workflow.entity.permission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.list.application.EntityListRelationalConfigService;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 列表内置按钮默认条件回归：编辑与删除限制一致，已有显式条件不受默认值调整影响。
 */
class EntityListActionConfigServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityListActionConfigService service = new EntityListActionConfigService(
            objectMapper,
            mock(EntityDefinitionMapper.class),
            mock(EntityListConfigMapper.class),
            mock(EntityListRelationalConfigService.class),
            List.of(),
            List.of());
    private final EntityActionRuleEvaluator evaluator = new EntityActionRuleEvaluator(List.of());

    @ParameterizedTest
    @CsvSource({
            "u1, other, NEW, false, true",
            "other, u1, NEW, false, true",
            "other, other, NEW, false, false",
            "u1, other, NEW, true, false",
            "u1, other, APPROVING, true, false",
            "u1, other, WITHDRAWN, true, true",
            "other, u1, WITHDRAWN, true, true",
            "other, other, WITHDRAWN, true, false"
    })
    void defaultEditAndDeleteEnforceTheSameOwnershipAndState(
            String creator, String submitter, String category,
            boolean processStarted, boolean expected) {
        List<Map<String, Object>> buttons = service.resolveRowButtons(null, "expense");
        EntityActionRuleDTO edit = rule(buttons, "edit");
        EntityActionRuleDTO delete = rule(buttons, "delete");
        EntityDataDTO row = new EntityDataDTO();
        row.setCreatedBy(creator);
        row.setSubmitterId(submitter);
        row.setProcessInstanceId(processStarted ? "process-1" : null);
        SysUser user = new SysUser();
        user.setId("u1");

        assertEquals(objectMapper.valueToTree(delete.getRoot()), objectMapper.valueToTree(edit.getRoot()));
        assertEquals("HIDE", edit.getUnavailableBehavior());
        assertEquals("仅本人未流转草稿或已撤回数据可以编辑", edit.getMessage());
        assertEquals(expected, evaluator.evaluate(edit, row, user, category));
        assertEquals(expected, evaluator.evaluate(delete, row, user, category));
    }

    @Test
    void savingLegacyButtonsAddsDefaultEditRule() throws Exception {
        EntityListConfig config = new EntityListConfig();
        config.setEntityCode("expense");
        config.setPublishedSnapshot(true);
        config.setRowActionConfig("[{\"key\":\"edit\",\"type\":\"built-in\"}]");

        service.normalizeForSave(config);

        Map<?, ?> stored = (Map<?, ?>) objectMapper.readValue(config.getRowActionConfig(), List.class).get(0);
        assertTrue(stored.containsKey("availabilityRule"));
        EntityActionRuleDTO edit = rule(service.resolveRowButtons(config, "expense"), "edit");
        assertEquals("仅本人未流转草稿或已撤回数据可以编辑", edit.getMessage());
    }

    @Test
    void explicitCustomOrUnrestrictedEditRulesArePreserved() throws Exception {
        Map<String, Object> customRule = Map.of(
                "unavailableBehavior", "DISABLE",
                "message", "指定状态可以编辑",
                "root", Map.of("type", "STATUS_CODE", "operator", "EQ", "value", "REVIEW"));
        Map<String, Object> customButton = Map.of(
                "key", "edit", "type", "built-in", "availabilityRule", customRule);
        EntityListConfig config = new EntityListConfig();
        config.setEntityCode("expense");
        config.setPublishedSnapshot(true);
        config.setRowActionConfig(objectMapper.writeValueAsString(List.of(customButton)));

        service.normalizeForSave(config);

        EntityActionRuleDTO edit = rule(service.resolveRowButtons(config, "expense"), "edit");
        assertEquals("指定状态可以编辑", edit.getMessage());
        assertEquals("DISABLE", edit.getUnavailableBehavior());
        assertEquals("STATUS_CODE", edit.getRoot().getType());
        assertEquals("REVIEW", edit.getRoot().getValue());

        // 用户显式选择“始终可操作”时保留规则对象但清空 root，不应回填内置限制。
        Map<String, Object> unrestrictedButton = new LinkedHashMap<>(customButton);
        Map<String, Object> unrestrictedRule = new LinkedHashMap<>();
        unrestrictedRule.put("version", 1);
        unrestrictedRule.put("unavailableBehavior", "HIDE");
        unrestrictedRule.put("message", "");
        unrestrictedRule.put("root", null);
        unrestrictedButton.put("availabilityRule", unrestrictedRule);
        config.setRowActionConfig(objectMapper.writeValueAsString(List.of(unrestrictedButton)));
        service.normalizeForSave(config);
        assertNull(rule(service.resolveRowButtons(config, "expense"), "edit").getRoot());
    }

    private EntityActionRuleDTO rule(List<Map<String, Object>> buttons, String key) {
        return service.readRule(buttons.stream()
                .filter(button -> key.equals(button.get("key")))
                .findFirst()
                .orElseThrow());
    }
}
