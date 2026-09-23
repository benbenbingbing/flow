package com.workflow.entity.permission.application;

import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import java.util.Map;

/**
 * 数据权限过滤节点扩展点。
 */
public interface EntityDataPermissionFilterProvider {

    /**
     * 自定义节点类型，建议使用命名空间大写形式。
     */
    String getType();

    /**
     * 校验节点配置的合法性，默认空实现。
     *
     * @param entityCode 实体编码
     * @param node       待校验的规则节点
     */
    default void validate(String entityCode, EntityActionRuleDTO.RuleNode node) {
    }

    /**
     * 将节点编译为安全 SQL 片段。
     *
     * @return 不含外层括号的 SQL 条件片段
     */
    String toSql(String entityCode, EntityActionRuleDTO.RuleNode node, SysUser user);

    /**
     * 编译需要运行时值的扩展规则；参数容器与其他允许、拒绝、委托规则共享。
     * 新扩展可使用 PermissionSqlParameters 分配绑定值；默认兼容仅返回固定 SQL 的旧扩展。
     */
    default String toSql(String entityCode, EntityActionRuleDTO.RuleNode node, SysUser user,
                         Map<String, Object> parameters) {
        return toSql(entityCode, node, user);
    }
}
