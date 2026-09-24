package com.workflow.entity.permission.application;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import com.workflow.entity.permission.api.response.FilterConfigDTO;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/** 同一 USER_FIELD 规则在按钮求值与列表权限中必须有相同含义。 */
class PermissionConditionParityTest {
    private final EntityActionRuleEvaluator evaluator = new EntityActionRuleEvaluator(List.of());

    @Test
    void missingUserAttributeFailsClosedIncludingNegatedAndOrderedComparisons() {
        SysUser user = new SysUser();
        for (String op : List.of("EQ", "NE", "IN", "NOT_IN", "CONTAINS", "NOT_CONTAINS", "GT", "GTE", "LT", "LTE")) {
            assertParity(user, "deptId", op, List.of("IN", "NOT_IN").contains(op) ? List.of("d1") : "d1", false);
        }
        assertParity(user, "deptId", "EMPTY", null, true);
        assertParity(user, "deptId", "NOT_EMPTY", null, false);
    }

    @Test
    void rolesUseIntersectionAndNegationOfIntersection() {
        SysUser user = new SysUser();
        user.setRoleIds(List.of("r1", "r2"));
        assertParity(user, "roleIds", "IN", List.of("r2", "r3"), true);
        assertParity(user, "roleIds", "NOT_IN", List.of("r2", "r3"), false);
        assertParity(user, "roleIds", "NOT_IN", List.of("r3"), true);
        assertParity(user, "roleIds", "CONTAINS", "r1", true);
        assertParity(user, "roleIds", "NOT_CONTAINS", "r1", false);
    }

    @Test
    void stringIdentitiesKeepLexicalOrderingWhileExplicitNumbersUseNumericOrdering() {
        SysUser user = new SysUser();
        user.setUsername("10");
        // 用户标识保持字符串语义；仅实际 Number 输入触发数值比较，兼容按钮规则。
        assertParity(user, "username", "LT", "2", true);
        assertParity(user, "username", "LT", 2, false);
        assertParity(user, "username", "EQ", 10, true);
    }

    @Test
    void missingExpectedValueDoesNotGrantNegatedUserRules() {
        SysUser user = new SysUser(); user.setUsername("alice");
        for (String op : List.of("NE", "NOT_IN", "NOT_CONTAINS", "LT")) {
            assertParity(user, "username", op, null, false);
        }
    }

    private void assertParity(SysUser user, String field, String op, Object value, boolean expected) {
        EntityDefinitionMapper definitions = mock(EntityDefinitionMapper.class);
        EntityFieldMapper fields = mock(EntityFieldMapper.class);
        EntityDefinition definition = new EntityDefinition();
        definition.setId("e1");
        when(definitions.findByEntityCode("asset")).thenReturn(Optional.of(definition));
        when(fields.findByEntityId("e1")).thenReturn(List.of());
        PermissionSqlBuilder builder = new PermissionSqlBuilder(definitions, fields,
                mock(EntityStatusMapper.class), List.of(), DatabaseQueryDialects.forVendor(DatabaseVendor.MYSQL));
        EntityActionRuleDTO.RuleNode node = new EntityActionRuleDTO.RuleNode();
        node.setType("USER_FIELD"); node.setField(field); node.setOperator(op); node.setValue(value);
        FilterConfigDTO filter = new FilterConfigDTO(); filter.setType("RULE"); filter.setRoot(node);
        assertEquals(expected, evaluator.evaluate(node, null, user, null), field + " " + op + " button");
        assertEquals(expected ? "1=1" : "1=0", builder.buildFilterSql("asset", filter, user), field + " " + op + " SQL");
    }
}
