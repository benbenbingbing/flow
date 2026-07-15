package com.workflow.service.permission;

import com.workflow.dto.EntityDataDTO;
import com.workflow.dto.permission.EntityActionRuleDTO;
import com.workflow.entity.SysUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityActionRuleEvaluatorTest {

    private final EntityActionRuleEvaluator evaluator = new EntityActionRuleEvaluator(List.of());

    @Test
    void ownerDraftCanDelete() {
        EntityDataDTO row = row("user-1", "user-1", null, "DRAFT");
        SysUser user = user("user-1", "zhangsan", "dept-1");

        assertTrue(evaluator.evaluate(deleteRule(), row, user, "NEW"));
    }

    @Test
    void otherUsersDraftCannotDelete() {
        EntityDataDTO row = row("user-2", "user-2", null, "DRAFT");
        SysUser user = user("user-1", "zhangsan", "dept-1");

        assertFalse(evaluator.evaluate(deleteRule(), row, user, "NEW"));
    }

    @Test
    void runningDataCannotUseDraftDeleteRule() {
        EntityDataDTO row = row("user-1", "user-1", "proc-1", "PENDING");
        SysUser user = user("user-1", "zhangsan", "dept-1");

        assertFalse(evaluator.evaluate(deleteRule(), row, user, "PROCESSING"));
    }

    @Test
    void withdrawnOwnerCanDelete() {
        EntityDataDTO row = row("user-1", "user-1", "proc-1", "WITHDRAWN");
        row.setProcessEndTime(java.time.LocalDateTime.now());
        SysUser user = user("user-1", "zhangsan", "dept-1");

        assertTrue(evaluator.evaluate(deleteRule(), row, user, "WITHDRAWN"));
    }

    @Test
    void customFieldConditionsAreSupported() {
        EntityDataDTO row = row("user-1", "user-1", null, "DRAFT");
        row.setData(Map.of("amount", 120));
        EntityActionRuleDTO rule = new EntityActionRuleDTO();
        EntityActionRuleDTO.RuleNode node = node("FIELD", "GT", 100);
        node.setField("amount");
        rule.setRoot(node);

        assertTrue(evaluator.evaluate(rule, row, user("user-1", "zhangsan", "dept-1"), "NEW"));
    }

    private EntityActionRuleDTO deleteRule() {
        EntityActionRuleDTO rule = new EntityActionRuleDTO();
        rule.setRoot(group("AND",
                group("OR",
                        relation("CURRENT_USER_IS_CREATOR"),
                        relation("CURRENT_USER_IS_SUBMITTER")),
                group("OR",
                        group("AND",
                                node("PROCESS_STATE", "EQ", "NOT_STARTED"),
                                node("STATUS_CATEGORY", "EQ", "NEW")),
                        node("STATUS_CATEGORY", "EQ", "WITHDRAWN"))));
        return rule;
    }

    private EntityActionRuleDTO.RuleNode group(
            String logic,
            EntityActionRuleDTO.RuleNode... children) {
        EntityActionRuleDTO.RuleNode node = new EntityActionRuleDTO.RuleNode();
        node.setType("GROUP");
        node.setLogic(logic);
        node.setChildren(List.of(children));
        return node;
    }

    private EntityActionRuleDTO.RuleNode relation(String relation) {
        EntityActionRuleDTO.RuleNode node = new EntityActionRuleDTO.RuleNode();
        node.setType("RELATION");
        node.setRelation(relation);
        return node;
    }

    private EntityActionRuleDTO.RuleNode node(String type, String operator, Object value) {
        EntityActionRuleDTO.RuleNode node = new EntityActionRuleDTO.RuleNode();
        node.setType(type);
        node.setOperator(operator);
        node.setValue(value);
        return node;
    }

    private EntityDataDTO row(
            String createdBy,
            String submitterId,
            String processInstanceId,
            String status) {
        EntityDataDTO row = new EntityDataDTO();
        row.setId("data-1");
        row.setCreatedBy(createdBy);
        row.setSubmitterId(submitterId);
        row.setProcessInstanceId(processInstanceId);
        row.setStatus(status);
        return row;
    }

    private SysUser user(String id, String username, String deptId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setDeptId(deptId);
        return user;
    }
}
