package com.workflow.service.permission;

import com.workflow.dto.permission.EntityActionRuleDTO;
import com.workflow.entity.SysUser;

/**
 * 数据权限过滤节点扩展点。
 */
public interface EntityDataPermissionFilterProvider {

    /**
     * 自定义节点类型，建议使用命名空间大写形式。
     */
    String getType();

    default void validate(String entityCode, EntityActionRuleDTO.RuleNode node) {
    }

    /**
     * 将节点编译为安全 SQL 片段。
     *
     * @return 不含外层括号的 SQL 条件片段
     */
    String toSql(String entityCode, EntityActionRuleDTO.RuleNode node, SysUser user);
}
