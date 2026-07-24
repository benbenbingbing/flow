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

    /**
     * 校验条件节点配置合法性，默认空实现。
     *
     * @param node 待校验的规则节点
     */
    default void validate(EntityActionRuleDTO.RuleNode node) {
    }

    /**
     * 评估自定义条件是否满足。
     *
     * @param node           条件节点
     * @param row            当前数据行，可为 null（工具栏场景）
     * @param user           当前用户
     * @param statusCategory 数据所属状态分类，可为 null
     * @return 条件满足返回 true
     */
    boolean evaluate(
            EntityActionRuleDTO.RuleNode node,
            EntityDataDTO row,
            SysUser user,
            String statusCategory);
}
