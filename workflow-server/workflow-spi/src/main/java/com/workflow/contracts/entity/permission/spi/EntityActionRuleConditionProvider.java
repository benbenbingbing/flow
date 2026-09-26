package com.workflow.contracts.entity.permission.spi;

import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.contracts.entity.permission.model.EntityActionRule;
import com.workflow.contracts.identity.model.IdentityUser;

/**
 * 自定义按钮规则条件扩展点。
 */
public interface EntityActionRuleConditionProvider {

    /**
     * 自定义条件类型，建议使用大写命名空间，如 CRM:CUSTOMER_LEVEL。
     *
     * @return 读取后的类型文本，供调用方比较或展示
     */
    String getType();

    /**
     * 校验条件节点配置合法性，默认空实现。
     *
     * @param node 待校验的规则节点
     */
    default void validate(EntityActionRule.RuleNode node) {
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
            EntityActionRule.RuleNode node,
            EntityRecordData row,
            IdentityUser user,
            String statusCategory);
}
