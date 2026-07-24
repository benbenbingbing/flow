package com.workflow.service.permission;

import com.workflow.dto.permission.EntityActionRuleDTO;
import com.workflow.dto.permission.FilterConfigDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityStatus;
import com.workflow.entity.SysUser;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityStatusMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 权限 SQL 构建器测试。
 *
 * <p>被测对象：{@link PermissionSqlBuilder}，覆盖非法字段名拒绝、遗留个人规则转义与状态限制、
 * 嵌套结构化条件编译、流程状态与部门关系编译、自定义过滤提供者扩展、遗留自定义 SQL 拒绝、
 * 缺失/未知结构化值拒绝、未知状态码与标量 IN 操作拒绝等场景。
 */
class PermissionSqlBuilderTest {

    private final EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
    private final EntityFieldMapper fieldMapper = mock(EntityFieldMapper.class);
    private final EntityStatusMapper statusMapper = mock(EntityStatusMapper.class);
    /** 被测 SQL 构建器 */
    private PermissionSqlBuilder builder;

    /** 装配构建器并预置实体定义与字段的 Mock 返回值 */
    @BeforeEach
    void setUp() {
        builder = new PermissionSqlBuilder(
                definitionMapper,
                fieldMapper,
                statusMapper,
                List.of());
        EntityDefinition definition = new EntityDefinition();
        definition.setId("entity-1");
        definition.setEntityCode("expense");
        EntityField amount = new EntityField();
        amount.setFieldCode("amount");
        amount.setDbColumnName("amount");
        when(definitionMapper.findByEntityCode("expense")).thenReturn(Optional.of(definition));
        when(fieldMapper.findByEntityId("entity-1")).thenReturn(List.of(amount));
    }

    /** 测试构建过滤 SQL 拒绝非法字段名：验证含 SQL 注入的字段映射被拒绝并返回 1=0 */
    @Test
    void buildFilterSqlRejectsInvalidFieldName() {
        SysUser user = user("u'1", "alice", "dept-1");
        FilterConfigDTO filter = new FilterConfigDTO();
        filter.setType("PERSONAL");
        FilterConfigDTO.FieldMappingDTO mapping = new FilterConfigDTO.FieldMappingDTO();
        mapping.setUserField("create_by OR 1=1");
        filter.setFieldMapping(mapping);

        String sql = builder.buildFilterSql("expense", filter, user);

        assertEquals("1=0", sql);
    }

    /** 测试遗留个人规则转义值并追加状态限制：验证 SQL 中值被转义且含状态 IN 条件 */
    @Test
    void buildLegacyPersonalRuleEscapesValuesAndAddsStatusLimit() {
        SysUser user = user("u'1", null, "dept-1");
        FilterConfigDTO filter = new FilterConfigDTO();
        filter.setType("PERSONAL");
        FilterConfigDTO.StatusLimitDTO statusLimit = new FilterConfigDTO.StatusLimitDTO();
        statusLimit.setEnabled(true);
        statusLimit.setValues(List.of("PENDING", "A'B"));
        filter.setStatusLimit(statusLimit);

        String sql = builder.buildFilterSql("expense", filter, user);

        assertEquals(
                "(create_by IN ('u''1')) AND (status IN ('PENDING','A''B'))",
                sql);
    }

    /** 测试编译跨用户、状态与自定义字段的嵌套结构化条件：验证 SQL 含 create_by、amount、status 条件并用 AND 连接 */
    @Test
    void compilesNestedStructuredConditionsAcrossUserStatusAndCustomFields() {
        EntityStatus processing = status("PENDING_REVIEW", "PROCESSING");
        when(statusMapper.findByCategory("expense", "PROCESSING"))
                .thenReturn(List.of(processing));
        FilterConfigDTO filter = new FilterConfigDTO();
        filter.setType("RULE");
        filter.setRoot(group(
                "AND",
                relation("CURRENT_USER_IS_CREATOR"),
                condition("FIELD", "amount", "GTE", 100),
                condition("STATUS_CATEGORY", null, "EQ", "PROCESSING")));

        String sql = builder.buildFilterSql(
                "expense",
                filter,
                user("u1", "alice", "dept-1"));

        assertTrue(sql.contains("create_by IN ('u1','alice')"));
        assertTrue(sql.contains("amount >= 100"));
        assertTrue(sql.contains("status IN ('PENDING_REVIEW')"));
        assertTrue(sql.contains(" AND "));
    }

    /** 测试编译流程状态与部门关系：验证 SQL 含 dept_id、process_end_time 与状态 IN 条件 */
    @Test
    void compilesProcessStateAndDepartmentRelations() {
        EntityStatus withdrawn = status("WITHDRAWN", "WITHDRAWN");
        EntityStatus terminated = status("TERMINATED", "TERMINATED");
        when(statusMapper.findByCategory("expense", "WITHDRAWN"))
                .thenReturn(List.of(withdrawn));
        when(statusMapper.findByCategory("expense", "TERMINATED"))
                .thenReturn(List.of(terminated));
        FilterConfigDTO filter = new FilterConfigDTO();
        filter.setType("RULE");
        filter.setRoot(group(
                "OR",
                relation("CURRENT_USER_SAME_DEPT"),
                condition("PROCESS_STATE", null, "EQ", "COMPLETED")));

        String sql = builder.buildFilterSql(
                "expense",
                filter,
                user("u1", "alice", "dept-1"));

        assertTrue(sql.contains("dept_id = 'dept-1'"));
        assertTrue(sql.contains("process_end_time IS NOT NULL"));
        assertTrue(sql.contains("status IN ('WITHDRAWN','TERMINATED')"));
    }

    /** 测试自定义过滤提供者扩展校验与编译：验证自定义类型经提供者转为 SQL 且通过校验 */
    @Test
    void validatesCustomFilterProviderExtension() {
        EntityDataPermissionFilterProvider provider = new EntityDataPermissionFilterProvider() {
            @Override
            public String getType() {
                return "CRM:CUSTOMER_LEVEL";
            }

            @Override
            public String toSql(
                    String entityCode,
                    EntityActionRuleDTO.RuleNode node,
                    SysUser user) {
                return "customer_level = 'VIP'";
            }
        };
        PermissionSqlBuilder extensibleBuilder = new PermissionSqlBuilder(
                definitionMapper,
                fieldMapper,
                statusMapper,
                List.of(provider));
        FilterConfigDTO filter = new FilterConfigDTO();
        filter.setType("RULE");
        EntityActionRuleDTO.RuleNode node = new EntityActionRuleDTO.RuleNode();
        node.setType("CRM:CUSTOMER_LEVEL");
        filter.setRoot(node);

        extensibleBuilder.validateFilter("expense", filter);
        assertEquals(
                "customer_level = 'VIP'",
                extensibleBuilder.buildFilterSql(
                        "expense",
                        filter,
                        user("u1", "alice", "dept-1")));
    }

    /** 测试拒绝遗留自定义 SQL 与表达式：验证 CUSTOM_SQL 类型抛出 IllegalArgumentException */
    @Test
    void rejectsLegacyCustomSqlAndExpression() {
        FilterConfigDTO filter = new FilterConfigDTO();
        filter.setType("CUSTOM_SQL");
        filter.setCustomSql("1=1");

        assertThrows(
                IllegalArgumentException.class,
                () -> builder.validateFilter("expense", filter));
    }

    /** 测试拒绝缺失与未知结构化值：验证缺少比较值与未知流程状态值均抛出 IllegalArgumentException */
    @Test
    void rejectsMissingAndUnknownStructuredValues() {
        FilterConfigDTO missingValue = new FilterConfigDTO();
        missingValue.setType("RULE");
        missingValue.setRoot(condition("PROCESS_STATE", null, "EQ", null));

        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> builder.validateFilter("expense", missingValue));
        assertTrue(missing.getMessage().contains("缺少比较值"));

        FilterConfigDTO unknownState = new FilterConfigDTO();
        unknownState.setType("RULE");
        unknownState.setRoot(condition(
                "PROCESS_STATE",
                null,
                "EQ",
                "UNKNOWN_STATE"));

        IllegalArgumentException unknown = assertThrows(
                IllegalArgumentException.class,
                () -> builder.validateFilter("expense", unknownState));
        assertTrue(unknown.getMessage().contains("不支持的值"));
    }

    /** 测试拒绝未知状态码与标量 IN 操作：验证未知状态码抛"状态编码不存在"，标量 IN 抛"必须提供多个值" */
    @Test
    void rejectsUnknownStatusCodeAndScalarInOperator() {
        when(statusMapper.findByEntityAndCode("expense", "MISSING"))
                .thenReturn(null);
        FilterConfigDTO unknownStatus = new FilterConfigDTO();
        unknownStatus.setType("RULE");
        unknownStatus.setRoot(condition(
                "STATUS_CODE",
                null,
                "EQ",
                "MISSING"));

        IllegalArgumentException unknown = assertThrows(
                IllegalArgumentException.class,
                () -> builder.validateFilter("expense", unknownStatus));
        assertTrue(unknown.getMessage().contains("状态编码不存在"));

        FilterConfigDTO scalarIn = new FilterConfigDTO();
        scalarIn.setType("RULE");
        scalarIn.setRoot(condition(
                "FIELD",
                "amount",
                "IN",
                100));

        IllegalArgumentException invalidIn = assertThrows(
                IllegalArgumentException.class,
                () -> builder.validateFilter("expense", scalarIn));
        assertTrue(invalidIn.getMessage().contains("必须提供多个值"));
    }

    /** 构造测试用户 */
    private SysUser user(String id, String username, String deptId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setDeptId(deptId);
        return user;
    }

    /** 构造带状态码与分类的实体状态对象 */
    private EntityStatus status(String code, String category) {
        EntityStatus status = new EntityStatus();
        status.setStatusCode(code);
        status.setStatusCategory(category);
        return status;
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

    /** 构造带字段、操作符与值的比较条件节点 */
    private EntityActionRuleDTO.RuleNode condition(
            String type,
            String field,
            String operator,
            Object value) {
        EntityActionRuleDTO.RuleNode node = new EntityActionRuleDTO.RuleNode();
        node.setType(type);
        node.setField(field);
        node.setOperator(operator);
        node.setValue(value);
        return node;
    }
}
