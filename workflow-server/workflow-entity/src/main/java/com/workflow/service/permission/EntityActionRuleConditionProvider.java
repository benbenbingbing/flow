package com.workflow.service.permission;

import com.workflow.dto.EntityDataDTO;
import com.workflow.dto.permission.EntityActionRuleDTO;
import com.workflow.entity.SysUser;

/**
 * 自定义按钮规则条件扩展点。
 */
public interface EntityActionRuleConditionProvider {

    /**
     * 自定义条件类型，建议使用大写命名空间，如 CRM:CUSTOMER_LEVEL。
     */
    String getType();

    default void validate(EntityActionRuleDTO.RuleNode node) {
    }

    boolean evaluate(
            EntityActionRuleDTO.RuleNode node,
            EntityDataDTO row,
            SysUser user,
            String statusCategory);
}
