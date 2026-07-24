package com.workflow.service.permission;

import com.workflow.dto.EntityDataDTO;
import com.workflow.dto.permission.EntityActionRuleDTO;
import com.workflow.entity.SysUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实体动作规则求值器测试。
 *
 * <p>被测对象：{@link EntityActionRuleEvaluator}，覆盖草稿归属删除权限、运行态不可用草稿删除规则、
 * 已撤回归属删除、自定义字段条件求值等场景。
 */
class EntityActionRuleEvaluatorTest {

    /** 被测规则求值器（无扩展关系） */
    private final EntityActionRuleEvaluator evaluator = new EntityActionRuleEvaluator(List.of());

    /** 测试草稿归属人可删除：验证本人草稿在新建状态下满足删除规则 */
    @Test
    void ownerDraftCanDelete() {
        EntityDataDTO row = row("user-1", "user-1", null, "DRAFT");
        SysUser user = user("user-1", "zhangsan", "dept-1");

        assertTrue(evaluator.evaluate(deleteRule(), row, user, "NEW"));
    }

    /** 测试他人草稿不可删除：验证非归属人草稿不满足删除规则 */
    @Test
    void otherUsersDraftCannotDelete() {
        EntityDataDTO row = row("user-2", "user-2", null, "DRAFT");
        SysUser user = user("user-1", "zhangsan", "dept-1");

        assertFalse(evaluator.evaluate(deleteRule(), row, user, "NEW"));
    }

    /** 测试运行态数据不可用草稿删除规则：验证流程进行中状态不满足草稿删除规则 */
    @Test
    void runningDataCannotUseDraftDeleteRule() {
        EntityDataDTO row = row("user-1", "user-1", "proc-1", "PENDING");
        SysUser user = user("user-1", "zhangsan", "dept-1");

        assertFalse(evaluator.evaluate(deleteRule(), row, user, "PROCESSING"));
    }

    /** 测试已撤回归属人可删除：验证本人已撤回且流程已结束时满足删除规则 */
    @Test
    void withdrawnOwnerCanDelete() {
        EntityDataDTO row = row("user-1", "user-1", "proc-1", "WITHDRAWN");
        row.setProcessEndTime(java.time.LocalDateTime.now());
        SysUser user = user("user-1", "zhangsan", "dept-1");

        assertTrue(evaluator.evaluate(deleteRule(), row, user, "WITHDRAWN"));
    }

    /** 测试支持自定义字段条件：验证 amount > 100 的字段条件求值为真 */
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

    /** 构造删除规则：归属人 + (未开始/新建 或 已撤回) */
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

    /** 构造逻辑分组节点（AND/OR），含子节点 */
    private EntityActionRuleDTO.RuleNode group(
            String logic,
            EntityActionRuleDTO.RuleNode... children) {
        EntityActionRuleDTO.RuleNode node = new EntityActionRuleDTO.RuleNode();
        node.setType("GROUP");
        node.setLogic(logic);
        node.setChildren(List.of(children));
        return node;
    }

    /** 构造关系节点（如 CURRENT_USER_IS_CREATOR） */
    private EntityActionRuleDTO.RuleNode relation(String relation) {
        EntityActionRuleDTO.RuleNode node = new EntityActionRuleDTO.RuleNode();
        node.setType("RELATION");
        node.setRelation(relation);
        return node;
    }

    /** 构造字段/状态比较节点 */
    private EntityActionRuleDTO.RuleNode node(String type, String operator, Object value) {
        EntityActionRuleDTO.RuleNode node = new EntityActionRuleDTO.RuleNode();
        node.setType(type);
        node.setOperator(operator);
        node.setValue(value);
        return node;
    }

    /** 构造带归属人、提交人、流程实例与状态的实体数据行 */
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

    /** 构造带 id、用户名与部门的测试用户 */
    private SysUser user(String id, String username, String deptId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setDeptId(deptId);
        return user;
    }
}
